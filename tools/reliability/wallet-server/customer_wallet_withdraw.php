<?php

require "config.php";
require_once "wallet_helper.php";
require_once __DIR__ . "/p0_security_helper.php";
require_once __DIR__ . "/customer_wallet_operation_helper.php";
$newOperation = false; $transactionOpen = false; $businessReady = false; $operationState = "unknown";

try {
    wallet_bootstrap($conn);
    $data = wallet_input();
    $auth = p0_require_customer($conn);
    $userId = (int)$auth["id"];
    if (isset($data['operation_owner_id']) && (string)$data['operation_owner_id'] !== (string)$userId) throw new RuntimeException('Sesi operasi telah berubah.');
    $username = trim((string)$auth["username"]);
    $idem = p0_idempotency_key(trim((string)($data["idempotency_key"] ?? "")));
    $amount = (int)(isset($data["amount"]) ? $data["amount"] : 0);
    $bankName = trim((string)(isset($data["bank_name"]) ? $data["bank_name"] : ""));
    $accountName = trim((string)(isset($data["account_name"]) ? $data["account_name"] : ""));
    $accountNumber = trim((string)(isset($data["account_number"]) ? $data["account_number"] : ""));
    $note = trim((string)(isset($data["note"]) ? $data["note"] : ""));

    $hash = customer_wallet_hash('wallet_withdraw', compact('amount', 'bankName', 'accountName', 'accountNumber', 'note'));
    $receipt=customer_wallet_read_receipt($conn,$userId,'wallet_withdraw',$idem,$hash);
    if ($receipt !== null) wallet_out(!empty($receipt['success']),$receipt['message'],$receipt);
    // This limiter owns a separate transaction: finish it before reserving the money operation.
    p0_rate_limit($conn, 'wallet_withdraw', $userId . '|' . p0_client_ip(), 5, 600);
    customer_wallet_require_transactional($conn, ['users', 'customer_withdrawals', 'wallet_transactions', 'api_idempotency']);
    if (!mysqli_begin_transaction($conn)) throw new RuntimeException('Transaksi tidak dapat dimulai.');
    $transactionOpen = true;
    try {
        $receipt = customer_wallet_reserve($conn, $userId, 'wallet_withdraw', $idem, $hash, $newOperation);
        if ($receipt !== null) {
            mysqli_rollback($conn); $transactionOpen = false;
            wallet_out(!empty($receipt['success']), $receipt['message'], $receipt);
        }
        if (!mysqli_query($conn,'SAVEPOINT customer_wallet_business')) throw new RuntimeException('Pengaman transaksi belum tersedia.');
        $businessReady=true;
        if ($amount < 10000) throw new RuntimeException('Minimal withdraw Rp10.000');
        if ($bankName === '' || $accountName === '' || $accountNumber === '') throw new RuntimeException('Data rekening atau e-wallet wajib diisi');
        $user = wallet_user($conn, $userId, $username, true);

        if (!$user) {
            throw new RuntimeException("Pengguna tidak ditemukan");
        }

        $userId = (int)$user["id"];
        $username = (string)$user["username"];
        // The user row lock serializes this limit with all withdrawals for this account.
        // Never call p0_rate_limit here: it starts/commits its own transaction.
        $limit = wallet_prepare($conn, 'SELECT COUNT(*) FROM customer_withdrawals WHERE user_id=? AND created_at >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)');
        mysqli_stmt_bind_param($limit, 'i', $userId); customer_wallet_execute($limit);
        mysqli_stmt_bind_result($limit, $recent); customer_wallet_fetch($limit); mysqli_stmt_close($limit);
        if ((int)$recent >= 5) throw new RuntimeException('Terlalu banyak pengajuan. Tunggu beberapa menit.');
        $before = (int)$user["balance"];

        if ($before < $amount) {
            throw new RuntimeException("Saldo tidak mencukupi");
        }

        $after = $before - $amount;
        $fee = 0;
        $net = $amount - $fee;

        $update = wallet_prepare(
            $conn,
            "UPDATE users
             SET balance=?
             WHERE id=? AND balance>=?"
        );

        mysqli_stmt_bind_param(
            $update,
            "iii",
            $after,
            $userId,
            $amount
        );

        if (!mysqli_stmt_execute($update)
            || mysqli_stmt_affected_rows($update) !== 1) {
            throw new RuntimeException(
                "Saldo berubah atau tidak mencukupi. Silakan ulangi."
            );
        }

        mysqli_stmt_close($update);

        $insert = wallet_prepare(
            $conn,
            "INSERT INTO customer_withdrawals
            (
                user_id, username, amount, fee, net_amount,
                bank_name, account_name, account_number,
                note, status, created_at
            )
            VALUES(?,?,?,?,?,?,?,?,?,'pending',NOW())"
        );

        mysqli_stmt_bind_param(
            $insert,
            "isiiissss",
            $userId,
            $username,
            $amount,
            $fee,
            $net,
            $bankName,
            $accountName,
            $accountNumber,
            $note
        );

        if (!mysqli_stmt_execute($insert)) {
            throw new RuntimeException(
                "Gagal membuat pengajuan: " . mysqli_stmt_error($insert)
            );
        }

        $withdrawId = (int)mysqli_insert_id($conn);
        mysqli_stmt_close($insert);

        $reference = "CWD-" . $withdrawId;

        wallet_add_log(
            $conn,
            $userId,
            "withdraw_hold",
            "out",
            $amount,
            $before,
            $after,
            "pending",
            $reference,
            "Withdraw ke " . $bankName . " • " . $accountNumber
        );

        $payload = array(
            "success" => true,
            "message" => "Withdraw berhasil diajukan dan menunggu persetujuan admin.",
            "withdraw_id" => $withdrawId,
            "balance" => $after,
            "reference" => $reference,
            "terminal" => true,
            "operation_state" => "succeeded",
            "committed" => true,
            "operation_id" => $idem
        );
        customer_wallet_store($conn, $userId, 'wallet_withdraw', $idem, $hash, $payload);
        if (!mysqli_commit($conn)) { $newOperation = false; throw new RuntimeException('Hasil belum terkonfirmasi. Periksa operasi yang sama.'); }
        $transactionOpen = false;
        wallet_out(true, $payload["message"], $payload);
    } catch (Throwable $error) {
        if ($transactionOpen && $newOperation && $businessReady) {
            $receipt=customer_wallet_finalize_rejection($conn,$userId,'wallet_withdraw',$idem,$hash,$error->getMessage());
            if ($receipt !== null) { $transactionOpen=false; wallet_out(false,$receipt['message'],$receipt); }
        }
        if ($transactionOpen) mysqli_rollback($conn);
        $transactionOpen=false; $operationState='unknown';
        throw $error;
    }

} catch (Throwable $error) {
    wallet_out(false, $error->getMessage(), ["operation_state"=>$operationState], 400);
}
