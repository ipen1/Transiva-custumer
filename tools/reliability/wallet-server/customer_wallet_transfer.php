<?php

header("Content-Type: application/json; charset=utf-8");
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");

require "config.php";
require_once __DIR__ . "/p0_security_helper.php";
require_once "wallet_helper.php";
require_once "wallet_transfer_security.php";
require_once __DIR__ . "/customer_wallet_operation_helper.php";
$newOperation = false; $transactionOpen = false; $businessReady=false; $operationState = "unknown";

try {
    wallet_bootstrap($conn);
    wallet_security_bootstrap($conn);

    $data = wallet_input();
    // P0: identitas pengirim hanya berasal dari token customer.
    $auth = p0_require_customer($conn);
    $userId = (int)$auth["id"];
    if (isset($data['operation_owner_id']) && (string)$data['operation_owner_id'] !== (string)$userId) throw new RuntimeException('Sesi operasi telah berubah.');
    $username = (string)$auth["username"];
    // user_id / username dari request sengaja diabaikan.
    $recipient = trim((string)($data["recipient"] ?? ""));
    $amount = (int)($data["amount"] ?? 0);
    $note = trim((string)($data["note"] ?? ""));
    $quoteToken = trim((string)($data["quote_token"] ?? ""));
    $requestId = trim((string)($data["request_id"] ?? ""));

    $senderId = $userId;
    $hash = customer_wallet_hash('wallet_transfer', compact('recipient', 'amount', 'note'));
    customer_wallet_require_transactional($conn, ['users', 'api_idempotency', 'wallet_transfer_requests', 'wallet_transactions', 'wallet_platform_ledger', 'wallet_transfer_rules', 'wallet_security_audit', 'order_notification_outbox']);
    if (!mysqli_begin_transaction($conn)) throw new RuntimeException('Transaksi tidak dapat dimulai.');
    $transactionOpen = true;
    try {
        $receipt = customer_wallet_reserve($conn, $senderId, 'wallet_transfer', $requestId, $hash, $newOperation);
        if ($receipt !== null) {
            mysqli_rollback($conn); $transactionOpen = false;
            wallet_out(!empty($receipt['success']), $receipt['message'], $receipt);
        }
        // Receipts from the previous release remain replayable even after quote expiry.
        $receipt = customer_wallet_legacy_transfer($conn, $senderId, $requestId, $amount, $recipient);
        if ($receipt !== null) {
            customer_wallet_store($conn, $senderId, 'wallet_transfer', $requestId, $hash, $receipt);
            if (!mysqli_commit($conn)) { $newOperation=false; throw new RuntimeException('Hasil belum terkonfirmasi.'); }
            $transactionOpen=false; wallet_out(!empty($receipt['success']), $receipt['message'], $receipt);
        }
        if (!mysqli_query($conn,'SAVEPOINT customer_wallet_business')) throw new RuntimeException('Pengaman transaksi belum tersedia.');
        $businessReady=true;
        if ($quoteToken === '' || $requestId === '') throw new RuntimeException('Detail transfer tidak lengkap');
        $quote = wallet_verify_quote($quoteToken);
        wallet_rate_limit($conn, $senderId);
        if ((int)$quote['sender_id'] !== $senderId || (int)$quote['amount'] !== $amount
            || (string)$quote['request_id'] !== $requestId
            || strcasecmp((string)$quote['receiver_username'], $recipient) !== 0) {
            throw new RuntimeException('Detail transfer telah berubah');
        }
        $rules =
            wallet_transfer_rules(
                $conn,
                true
            );

        if (!$rules["enabled"]) {
            throw new RuntimeException(
                "Fitur kirim dana sedang dinonaktifkan"
            );
        }

        if ($amount < $rules["minimum_amount"]) {
            throw new RuntimeException(
                "Minimal transfer "
                . wallet_money(
                    $rules["minimum_amount"]
                )
            );
        }

        $senderLocked = wallet_user(
            $conn,
            $senderId,
            "",
            true
        );

        if (!$senderLocked) {
            throw new RuntimeException(
                "Pengirim tidak ditemukan"
            );
        }

        $receiverId =
            (int)$quote["receiver_id"];

        $receiverStmt = wallet_prepare(
            $conn,
            "SELECT id, username, balance
             FROM users
             WHERE id=?
             LIMIT 1
             FOR UPDATE"
        );

        mysqli_stmt_bind_param(
            $receiverStmt,
            "i",
            $receiverId
        );

        customer_wallet_execute($receiverStmt);
        mysqli_stmt_bind_result(
            $receiverStmt,
            $dbReceiverId,
            $receiverUsername,
            $receiverBalance
        );

        if (!customer_wallet_fetch($receiverStmt)) {
            mysqli_stmt_close($receiverStmt);
            throw new RuntimeException(
                "Penerima tidak ditemukan"
            );
        }

        mysqli_stmt_close($receiverStmt);

        /*
         * Hitung ulang kuota dan biaya di server saat eksekusi.
         * Nilai dari Android dan quote tidak dipercaya.
         */
        $used =
            wallet_monthly_transfer_count(
                $conn,
                $senderId
            );

        $free =
            $used < $rules["free_limit"];

        $fee =
            $free
                ? 0
                : $rules["fee_amount"];

        if ((int)$quote["fee"] !== $fee) {
            throw new RuntimeException(
                "Kuota atau biaya telah berubah. Silakan lihat detail kembali."
            );
        }

        $totalDebit =
            $amount + $fee;

        $senderBefore =
            (int)$senderLocked["balance"];

        $receiverBefore =
            (int)$receiverBalance;

        if ($senderBefore < $totalDebit) {
            throw new RuntimeException(
                "Saldo tidak mencukupi untuk nominal dan biaya"
            );
        }

        /*
         * Idempotency: satu request_id hanya boleh diproses sekali.
         */
        $requestStmt = wallet_prepare(
            $conn,
            "INSERT INTO wallet_transfer_requests
            (
                request_id,
                sender_id,
                receiver_id,
                amount,
                fee,
                status,
                ip_address,
                user_agent,
                created_at
            )
            VALUES(?,?,?,?,?,'processing',?,?,NOW())"
        );

        $ip = wallet_client_ip();
        $agent = wallet_user_agent();

        mysqli_stmt_bind_param(
            $requestStmt,
            "siiiiss",
            $requestId,
            $senderId,
            $receiverId,
            $amount,
            $fee,
            $ip,
            $agent
        );

        if (!mysqli_stmt_execute($requestStmt)) {
            if (mysqli_errno($conn) === 1062) {
                throw new RuntimeException(
                    "Transfer ini sudah pernah diproses"
                );
            }

            throw new RuntimeException(
                "Request transfer gagal dicatat"
            );
        }

        mysqli_stmt_close($requestStmt);

        $senderAfter =
            $senderBefore - $totalDebit;

        $receiverAfter =
            $receiverBefore + $amount;

        $debit = wallet_prepare(
            $conn,
            "UPDATE users
             SET balance=?
             WHERE id=? AND balance>=?"
        );

        mysqli_stmt_bind_param(
            $debit,
            "iii",
            $senderAfter,
            $senderId,
            $totalDebit
        );

        if (
            !mysqli_stmt_execute($debit)
            || mysqli_stmt_affected_rows($debit) !== 1
        ) {
            throw new RuntimeException(
                "Saldo berubah atau tidak mencukupi"
            );
        }

        mysqli_stmt_close($debit);

        $credit = wallet_prepare(
            $conn,
            "UPDATE users
             SET balance=?
             WHERE id=?"
        );

        mysqli_stmt_bind_param(
            $credit,
            "ii",
            $receiverAfter,
            $receiverId
        );

        if (
            !mysqli_stmt_execute($credit)
            || mysqli_stmt_affected_rows($credit) !== 1
        ) {
            throw new RuntimeException(
                "Gagal menambah saldo penerima"
            );
        }

        mysqli_stmt_close($credit);

        $reference =
            wallet_reference("TRF");

        $safeNote = $note !== ""
            ? (
                function_exists("mb_substr")
                    ? mb_substr($note, 0, 120)
                    : substr($note, 0, 120)
            )
            : "";

        $descriptionOut =
            "Transfer ke "
            . $receiverUsername;

        $descriptionIn =
            "Transfer dari "
            . $senderLocked["username"];

        if ($safeNote !== "") {
            $descriptionOut .= " • " . $safeNote;
            $descriptionIn .= " • " . $safeNote;
        }

        if ($fee > 0) {
            $descriptionOut .=
                " • biaya layanan "
                . wallet_money($fee);
        }

        wallet_add_log(
            $conn,
            $senderId,
            "transfer_out",
            "out",
            $amount,
            $senderBefore,
            $senderAfter,
            "success",
            $reference,
            $descriptionOut,
            $receiverId
        );

        wallet_add_log(
            $conn,
            $receiverId,
            "transfer_in",
            "in",
            $amount,
            $receiverBefore,
            $receiverAfter,
            "success",
            $reference,
            $descriptionIn,
            $senderId
        );

        if ($fee > 0) {
            $feeReference =
                "FEE-" . $reference;

            $feeStmt = wallet_prepare(
                $conn,
                "INSERT INTO wallet_platform_ledger
                (
                    source_type,
                    source_reference,
                    amount,
                    description,
                    created_at
                )
                VALUES('transfer_fee',?,?,?,NOW())"
            );

            $feeDescription =
                "Biaya transfer "
                . $senderLocked["username"]
                . " ke "
                . $receiverUsername;

            mysqli_stmt_bind_param(
                $feeStmt,
                "sis",
                $feeReference,
                $fee,
                $feeDescription
            );

            customer_wallet_execute($feeStmt);
            mysqli_stmt_close($feeStmt);
        }

        $remaining =
            max(
                0,
                $rules["free_limit"]
                - ($used + 1)
            );

        $responseData = array(
            "success" => true,
            "message" => "Berhasil mengirim "
                . wallet_money($amount)
                . " ke "
                . $receiverUsername
                . ".",
            "reference" => $reference,
            "amount" => $amount,
            "fee" => $fee,
            "total_debit" => $totalDebit,
            "balance" => $senderAfter,
            "receiver_balance" => $receiverAfter,
            "terminal" => true,
            "operation_state" => "succeeded",
            "committed" => true,
            "operation_id" => $requestId,
            "free_remaining" => $remaining
        );

        $updateRequest = wallet_prepare(
            $conn,
            "UPDATE wallet_transfer_requests
             SET status='success',
                 reference=?,
                 response_json=?,
                 completed_at=NOW()
             WHERE request_id=?"
        );

        $responseJson =
            json_encode(
                $responseData,
                JSON_UNESCAPED_UNICODE
            );

        mysqli_stmt_bind_param(
            $updateRequest,
            "sss",
            $reference,
            $responseJson,
            $requestId
        );

        customer_wallet_execute($updateRequest);
        if (mysqli_stmt_affected_rows($updateRequest) !== 1) throw new RuntimeException("Receipt transfer gagal disimpan");
        mysqli_stmt_close($updateRequest);

        wallet_audit(
            $conn,
            $senderId,
            "execute_transfer",
            "success",
            $responseJson
        );

        customer_wallet_queue_intent($conn,$reference.':in:'.$receiverId,$receiverId,'Saldo Masuk',
            'Anda menerima '.wallet_money($amount).' dari '.$senderLocked['username'].'.',
            ['type'=>'wallet_transfer_in','screen'=>'customer_transactions','reference'=>$reference,'amount'=>$amount,'balance'=>$receiverAfter]);
        customer_wallet_queue_intent($conn,$reference.':out:'.$senderId,$senderId,'Transfer Berhasil',
            'Berhasil mengirim '.wallet_money($amount).' ke '.$receiverUsername.'.',
            ['type'=>'wallet_transfer_out','screen'=>'customer_transactions','reference'=>$reference,'amount'=>$amount,'fee'=>$fee,'balance'=>$senderAfter]);
        customer_wallet_store($conn, $senderId, 'wallet_transfer', $requestId, $hash, $responseData);
        if (!mysqli_commit($conn)) { $newOperation=false; throw new RuntimeException('Hasil belum terkonfirmasi. Periksa status operasi yang sama.'); }
        $transactionOpen=false;

        wallet_out(
            true,
            $responseData["message"],
            $responseData
        );

    } catch (Throwable $error) {
        if ($transactionOpen && $newOperation && $businessReady) {
            $receipt=customer_wallet_finalize_rejection($conn,$senderId,'wallet_transfer',$requestId,$hash,$error->getMessage());
            if ($receipt !== null) { $transactionOpen=false; wallet_out(false,$receipt['message'],$receipt); }
        }
        if ($transactionOpen) mysqli_rollback($conn);
        $transactionOpen=false; $operationState='unknown';
        throw $error;
    }

} catch (Throwable $error) {
    wallet_out(
        false,
        $error->getMessage(),
        array("operation_state"=>$operationState),
        400
    );
}
