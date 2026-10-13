<?php
/** Atomic, payload-bound receipts. Call reserve/store inside the money transaction. */
function customer_wallet_hash(string $action, array $payload): string {
    ksort($payload);
    return hash('sha256', $action . ':' . json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR));
}
function customer_wallet_require_transactional($conn, array $tables): void {
    foreach ($tables as $table) {
        $stmt = wallet_prepare($conn, 'SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?');
        mysqli_stmt_bind_param($stmt, 's', $table);
        customer_wallet_execute($stmt);
        mysqli_stmt_bind_result($stmt, $engine);
        $found = customer_wallet_fetch($stmt);
        mysqli_stmt_close($stmt);
        if (!$found || strcasecmp((string)$engine, 'InnoDB') !== 0) {
            throw new RuntimeException('Penyimpanan wallet belum siap. Hubungi admin.');
        }
    }
    // A unique slot is necessary to serialize competing requests before any debit.
    $stmt = wallet_prepare($conn, "SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_idempotency' AND NON_UNIQUE=0 GROUP BY INDEX_NAME");
    customer_wallet_execute($stmt);
    mysqli_stmt_bind_result($stmt, $index, $columns);
    $valid = false;
    while (customer_wallet_fetch($stmt)) {
        $parts = explode(',', (string)$columns); sort($parts);
        if ($parts === ['action_name', 'idem_key', 'user_id']) $valid = true;
    }
    mysqli_stmt_close($stmt);
    if (!$valid) throw new RuntimeException('Pengaman transaksi wallet belum siap. Hubungi admin.');
}
function customer_wallet_execute($stmt): void {
    if (!mysqli_stmt_execute($stmt)) throw new RuntimeException('Penyimpanan transaksi gagal. Silakan periksa hasil operasi yang sama.');
}
function customer_wallet_fetch($stmt) {
    $found=mysqli_stmt_fetch($stmt);
    if ($found===false) throw new RuntimeException('Hasil operasi belum dapat dibaca.');
    return $found;
}
function customer_wallet_receipt(array $stored, string $hash): ?array {
    if (!isset($stored['_request_hash']) || !hash_equals((string)$stored['_request_hash'], $hash)) {
        throw new RuntimeException('ID operasi sudah digunakan untuk data berbeda. Periksa riwayat transaksi.');
    }
    if (!empty($stored['success']) || (!empty($stored['terminal']) && ($stored['operation_state']??'') === 'rejected')) {
        unset($stored['_request_hash']);
        $stored['replayed'] = true;
        $stored['terminal'] = true;
        $stored['committed'] = !empty($stored['success']);
        $stored['operation_state'] = !empty($stored['success']) ? 'succeeded' : 'rejected';
        return $stored;
    }
    return null;
}
function customer_wallet_reserve($conn, int $userId, string $action, string $key, string $hash, bool &$newOperation): ?array {
    if ($key === '' || strlen($key) > 120) throw new RuntimeException('ID operasi wajib diisi. Perbarui aplikasi.');
    $pending = json_encode(['_request_hash'=>$hash], JSON_THROW_ON_ERROR);
    $stmt = wallet_prepare($conn, 'INSERT INTO api_idempotency(user_id,action_name,idem_key,response_json) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE idem_key=idem_key');
    mysqli_stmt_bind_param($stmt, 'isss', $userId, $action, $key, $pending);
    customer_wallet_execute($stmt); $newOperation = mysqli_stmt_affected_rows($stmt) === 1; mysqli_stmt_close($stmt);
    $stmt = wallet_prepare($conn, 'SELECT response_json FROM api_idempotency WHERE user_id=? AND action_name=? AND idem_key=? FOR UPDATE');
    mysqli_stmt_bind_param($stmt, 'iss', $userId, $action, $key);
    customer_wallet_execute($stmt); mysqli_stmt_bind_result($stmt, $json);
    if (!customer_wallet_fetch($stmt)) { mysqli_stmt_close($stmt); throw new RuntimeException('Receipt operasi tidak tersedia.'); }
    mysqli_stmt_close($stmt);
    $newOperation = false; // Conflicting/legacy receipts must never authorize a fresh client intent.
    $stored = json_decode((string)$json, true, 32, JSON_THROW_ON_ERROR);
    $receipt = customer_wallet_receipt($stored, $hash);
    $newOperation = $receipt === null;
    if ($receipt !== null) $receipt['operation_id']=$key;
    return $receipt;
}
/** Optional fast replay before a rate limiter that owns its own transaction. */
function customer_wallet_read_receipt($conn, int $userId, string $action, string $key, string $hash): ?array {
    if ($key === '') return null;
    $stmt=wallet_prepare($conn, 'SELECT response_json FROM api_idempotency WHERE user_id=? AND action_name=? AND idem_key=? LIMIT 1');
    mysqli_stmt_bind_param($stmt,'iss',$userId,$action,$key); customer_wallet_execute($stmt);
    mysqli_stmt_bind_result($stmt,$json); $found=customer_wallet_fetch($stmt); mysqli_stmt_close($stmt);
    $receipt=$found ? customer_wallet_receipt(json_decode((string)$json,true,32,JSON_THROW_ON_ERROR),$hash) : null;
    if ($receipt !== null) $receipt['operation_id']=$key;
    return $receipt;
}
function customer_wallet_store($conn, int $userId, string $action, string $key, string $hash, array $receipt): void {
    $receipt['terminal']=true;
    $receipt['operation_state']=!empty($receipt['success'])?'succeeded':'rejected';
    $receipt['_request_hash'] = $hash;
    $json = json_encode($receipt, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    $stmt = wallet_prepare($conn, 'UPDATE api_idempotency SET response_json=? WHERE user_id=? AND action_name=? AND idem_key=?');
    mysqli_stmt_bind_param($stmt, 'siss', $json, $userId, $action, $key);
    customer_wallet_execute($stmt);
    if (mysqli_stmt_affected_rows($stmt) !== 1) { mysqli_stmt_close($stmt); throw new RuntimeException('Receipt operasi gagal disimpan.'); }
    mysqli_stmt_close($stmt);
}
function customer_wallet_after_commit(callable $work): void {
    try { $work(); } catch (Throwable $error) { error_log('CUSTOMER_WALLET_POST_COMMIT_FAILED'); }
}
/** Persist only notification intent inside the money transaction; the worker resolves tokens later. */
function customer_wallet_queue_intent($conn, string $event, int $userId, string $title, string $body, array $data): void {
    $strings=[]; foreach ($data as $key=>$value) $strings[(string)$key]=(string)$value;
    $strings=array_merge($strings, ['app'=>'transiva','title'=>$title,'body'=>$body,'message'=>$body,
        'click_action'=>'OPEN_TRANSIVA','channel_id'=>'transiva_wallet_channel','v'=>(string)time()]);
    $payload=json_encode(['user_id'=>$userId,'title'=>$title,'body'=>$body,'data'=>$strings], JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    $event='wallet-transfer-'.hash('sha256',$event);
    $stmt=wallet_prepare($conn, "INSERT INTO order_notification_outbox(event_key,kind,payload_json) VALUES(?,'wallet_fcm',?) ON DUPLICATE KEY UPDATE event_key=event_key");
    mysqli_stmt_bind_param($stmt,'ss',$event,$payload); customer_wallet_execute($stmt); mysqli_stmt_close($stmt);
}
/** Keep the reserved slot locked while undoing business changes and committing a terminal rejection. */
function customer_wallet_finalize_rejection($conn, int $userId, string $action, string $key, string $hash, string $message): ?array {
    try {
        if (!mysqli_query($conn, 'ROLLBACK TO SAVEPOINT customer_wallet_business')) return null;
        $receipt=['success'=>false,'message'=>$message,'operation_id'=>$key,'operation_state'=>'rejected',
            'terminal'=>true,'committed'=>false];
        customer_wallet_store($conn,$userId,$action,$key,$hash,$receipt);
        if (!mysqli_commit($conn)) return null;
        return $receipt;
    } catch (Throwable $error) { error_log('CUSTOMER_WALLET_REJECTION_UNCONFIRMED'); return null; }
}
function customer_wallet_legacy_transfer($conn, int $userId, string $key, int $amount, string $recipient): ?array {
    $stmt = wallet_prepare($conn, "SELECT r.amount, u.username, r.status, r.response_json FROM wallet_transfer_requests r JOIN users u ON u.id=r.receiver_id WHERE r.request_id=? AND r.sender_id=? LIMIT 1 FOR UPDATE");
    mysqli_stmt_bind_param($stmt, 'si', $key, $userId);
    customer_wallet_execute($stmt); mysqli_stmt_bind_result($stmt, $oldAmount, $oldRecipient, $status, $json);
    $found = customer_wallet_fetch($stmt); mysqli_stmt_close($stmt);
    if (!$found) return null;
    if ((int)$oldAmount !== $amount || strcasecmp((string)$oldRecipient, $recipient) !== 0) throw new RuntimeException('ID transfer sudah digunakan untuk data berbeda.');
    if ($status !== 'success' || !$json) throw new RuntimeException('Hasil transfer belum tersedia. Periksa kembali operasi yang sama.');
    $receipt = json_decode($json, true, 32, JSON_THROW_ON_ERROR);
    if (empty($receipt['success'])) throw new RuntimeException('Receipt transfer tidak valid.');
    $receipt['replayed'] = true; $receipt['committed'] = true;
    $receipt['operation_id']=$key; $receipt['terminal']=true; $receipt['operation_state']='succeeded';
    return $receipt;
}
function customer_wallet_status_result(string $key, ?array $stored): array {
    if($stored===null)return ['operation_id'=>$key,'operation_state'=>'not_found','terminal'=>false,'receipt'=>null];
    unset($stored['_request_hash']);
    $success=!empty($stored['success']);
    $rejected=!empty($stored['terminal'])&&($stored['operation_state']??'')==='rejected';
    if(!$success&&!$rejected)return ['operation_id'=>$key,'operation_state'=>'processing','terminal'=>false,'receipt'=>null];
    $stored['operation_id']=$key; $stored['terminal']=true; $stored['committed']=$success;
    $stored['operation_state']=$success?'succeeded':'rejected';
    return ['operation_id'=>$key,'operation_state'=>$stored['operation_state'],'terminal'=>true,'receipt'=>$stored];
}
function customer_wallet_lookup_status($conn, int $userId, string $action, string $key): array {
    $stmt=wallet_prepare($conn,'SELECT response_json FROM api_idempotency WHERE user_id=? AND action_name=? AND idem_key=? LIMIT 1');
    mysqli_stmt_bind_param($stmt,'iss',$userId,$action,$key);customer_wallet_execute($stmt);
    mysqli_stmt_bind_result($stmt,$json);$found=customer_wallet_fetch($stmt);mysqli_stmt_close($stmt);
    if($found)return customer_wallet_status_result($key,json_decode((string)$json,true,32,JSON_THROW_ON_ERROR));
    if($action==='wallet_transfer') {
        // Compatibility lookup for older successful transfers, without importing or writing receipts.
        $stmt=wallet_prepare($conn,'SELECT status,response_json FROM wallet_transfer_requests WHERE request_id=? AND sender_id=? LIMIT 1');
        mysqli_stmt_bind_param($stmt,'si',$key,$userId);customer_wallet_execute($stmt);
        mysqli_stmt_bind_result($stmt,$status,$legacy);$found=customer_wallet_fetch($stmt);mysqli_stmt_close($stmt);
        if($found) {
            if($status!=='success'||!$legacy)return ['operation_id'=>$key,'operation_state'=>'processing','terminal'=>false,'receipt'=>null];
            return customer_wallet_status_result($key,json_decode((string)$legacy,true,32,JSON_THROW_ON_ERROR));
        }
    }
    // Absence is observational, not a guarantee that an in-flight request cannot commit later.
    return customer_wallet_status_result($key,null);
}
