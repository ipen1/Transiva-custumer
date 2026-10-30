<?php
declare(strict_types=1);
/** Transaction receipts and request fingerprints are durable in the same commit as the order. */
function order_request_hash(array $data): string {
    $normalize=function($value) use (&$normalize) {
        if(!is_array($value)) return $value;
        if(array_keys($value)!==range(0,count($value)-1)) ksort($value,SORT_STRING);
        foreach($value as $key=>$item)$value[$key]=$normalize($item);
        return $value;
    };
    return hash('sha256',json_encode($normalize($data),JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR));
}
function order_require_transactional(mysqli $conn,array $tables): void {
    $st=mysqli_prepare($conn,'SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?');
    foreach(array_unique($tables) as $table){
        mysqli_stmt_bind_param($st,'s',$table);mysqli_stmt_execute($st);$r=mysqli_stmt_get_result($st);$row=$r?mysqli_fetch_assoc($r):null;
        if(!$row || strcasecmp((string)$row['ENGINE'],'InnoDB')!==0) throw new RuntimeException('Migrasi database transaksi wajib diselesaikan sebelum layanan digunakan.');
    }
    // Optional financial/reward/index tables must also participate in rollback when present.
    $optional=['voucher_usage_refunds','split_bill_sessions','split_bill_payments','customer_coin_secure_balance','customer_point_secure_balance','customer_loyalty_point_ledger','customer_loyalty_redemptions','customer_promo_usages','ride_voucher_usages','ride_vouchers','customer_promos','driver_dispatch_offers','driver_dispatch_history','merchant_order_lookup','merchant_daily_stats','merchant_menu_meta'];
    foreach(array_diff($optional,$tables) as $table){
        mysqli_stmt_bind_param($st,'s',$table);mysqli_stmt_execute($st);$r=mysqli_stmt_get_result($st);$row=$r?mysqli_fetch_assoc($r):null;
        if($row && strcasecmp((string)$row['ENGINE'],'InnoDB')!==0)throw new RuntimeException('Tabel transaksi tambahan memerlukan migrasi InnoDB.');
    }
    mysqli_stmt_close($st);
}
function order_attempt_read(mysqli $conn,int $user,string $key,string $hash): ?array {
    $st=mysqli_prepare($conn,'SELECT request_hash,response_json FROM order_attempts WHERE user_id=? AND attempt_key=? LIMIT 1');
    mysqli_stmt_bind_param($st,'is',$user,$key);mysqli_stmt_execute($st);$r=mysqli_stmt_get_result($st);$row=$r?mysqli_fetch_assoc($r):null;mysqli_stmt_close($st);
    if(!$row)return null;
    if(!hash_equals((string)$row['request_hash'],$hash)){
        http_response_code(409);throw new DomainException('Permintaan sebelumnya berbeda. Periksa aktivitas pesanan terlebih dahulu.');
    }
    return json_decode((string)$row['response_json'],true,512,JSON_THROW_ON_ERROR);
}
function order_attempt_store(mysqli $conn,int $user,string $key,string $hash,array $response): void {
    $json=json_encode($response,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    $st=mysqli_prepare($conn,'INSERT INTO order_attempts(user_id,attempt_key,request_hash,response_json) VALUES(?,?,?,?)');
    mysqli_stmt_bind_param($st,'isss',$user,$key,$hash,$json);
    if(!mysqli_stmt_execute($st))throw new RuntimeException('Gagal menyimpan bukti transaksi.');
    mysqli_stmt_close($st);
}
function order_attempt_lock(mysqli $conn,int $user,string $key): void {
    $name='attempt:'.substr(hash('sha256',$user.':'.$key),0,52);
    $st=mysqli_prepare($conn,'SELECT GET_LOCK(?,10) AS acquired');mysqli_stmt_bind_param($st,'s',$name);mysqli_stmt_execute($st);$row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    if((int)($row['acquired']??0)!==1){http_response_code(503);throw new RuntimeException('Permintaan sebelumnya masih diproses. Coba kembali.');}
    register_shutdown_function(function()use($conn,$name){try{$st=mysqli_prepare($conn,'SELECT RELEASE_LOCK(?)');mysqli_stmt_bind_param($st,'s',$name);mysqli_stmt_execute($st);mysqli_stmt_close($st);}catch(Throwable $ignored){}});
}
