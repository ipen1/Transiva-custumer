<?php

require_once __DIR__.'/schema_runtime_guard.php';
require_once __DIR__ . '/customer_benefit_helper.php';
require_once __DIR__ . '/voucher_discount_rules.php';
/**
 * Unified voucher engine V18.
 * Backward compatible with ride_vouchers and also accepts promo codes created
 * from Web Admin in customer_promos.
 */
function rv_clean(mysqli $conn, $v): string {
    return strtoupper(trim((string)($v ?? '')));
}
function rv_service_name(string $orderType): string {
    $t = strtolower(trim($orderType));
    return in_array($t, ['transcar','car','mobil'], true) ? 'TransCar' : 'TransRide';
}
function rv_table_exists(mysqli $conn, string $table): bool {
    $stmt = mysqli_prepare($conn, 'SELECT COUNT(*) total FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?');
    if (!$stmt) return false;
    mysqli_stmt_bind_param($stmt, 's', $table);
    mysqli_stmt_execute($stmt);
    $r = mysqli_stmt_get_result($stmt);
    $row = $r ? mysqli_fetch_assoc($r) : null;
    mysqli_stmt_close($stmt);
    return (int)($row['total'] ?? 0) > 0;
}
function rv_ensure_customer_usage(mysqli $conn): void {
    transiva_schema_query($conn, "CREATE TABLE IF NOT EXISTS customer_promo_usages (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        promo_id BIGINT UNSIGNED NOT NULL,
        user_id BIGINT NOT NULL,
        order_id VARCHAR(120) NOT NULL,
        discount_amount INT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY(id),
        KEY idx_promo_user(promo_id,user_id),
        KEY idx_order(order_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
}
function rv_apply(mysqli $conn, int $userId, string $code, int $originalPrice, string $serviceType, bool $lock = false, ?int $deliveryFee = null): array {
    $code = rv_clean($conn, $code);
    $empty = ['voucher_id'=>0,'promo_id'=>0,'source'=>'','code'=>'','discount'=>0,'final_price'=>$originalPrice,'title'=>''];
    if ($code === '') return $empty;
    cb_assert_voucher_verified($conn, $userId, $code);
    rv_require_refund_schema($conn);
    if ($serviceType === 'TransFood' && $deliveryFee === null) throw new Exception('Dasar ongkir voucher belum tersedia');
    $discountBase = $deliveryFee === null ? $originalPrice : max(0, min($originalPrice, $deliveryFee));

    // 1) Existing native ride voucher table, for backward compatibility.
    if (rv_table_exists($conn, 'ride_vouchers')) {
        $lockSql = $lock ? ' FOR UPDATE' : '';
        $stmt = mysqli_prepare($conn, "SELECT * FROM ride_vouchers WHERE UPPER(code)=? AND is_active=1 AND (starts_at IS NULL OR starts_at<=NOW()) AND (expires_at IS NULL OR expires_at>=NOW()) LIMIT 1$lockSql");
        if ($stmt) {
            mysqli_stmt_bind_param($stmt, 's', $code);
            mysqli_stmt_execute($stmt);
            $res = mysqli_stmt_get_result($stmt);
            $row = $res ? mysqli_fetch_assoc($res) : null;
            mysqli_stmt_close($stmt);
            if ($row) {
                $rowService = (string)($row['service_type'] ?? 'all');
                if (!in_array($rowService, ['all', $serviceType], true)) throw new Exception('Voucher tidak berlaku untuk layanan ini');
                if ($originalPrice < (int)($row['min_order'] ?? 0)) throw new Exception('Minimal tarif voucher belum terpenuhi');
                if (($row['quota_total'] ?? null) !== null && (int)($row['quota_total']) > 0 && (int)($row['quota_used'] ?? 0) >= (int)$row['quota_total']) throw new Exception('Kuota voucher sudah habis');
                $vid = (int)$row['id'];
                $limit = max(1, (int)($row['per_user_limit'] ?? 1));
                $used = rv_usage_count($conn, 'ride_voucher_usages', 'voucher_id', $vid, $userId, $lock);
                if ($used >= $limit) throw new Exception('Batas penggunaan voucher Anda sudah tercapai');
                $discount = vd_delivery_discount($row, $originalPrice, $discountBase);
                if ($discount <= 0) throw new Exception('Voucher tidak menghasilkan potongan untuk transaksi ini');
                return ['voucher_id'=>$vid,'promo_id'=>0,'source'=>'ride_vouchers','code'=>$code,'discount'=>$discount,'final_price'=>$originalPrice-$discount,'title'=>(string)($row['title'] ?? '')];
            }
        }
    }

    // 2) Web Admin promo/voucher table. This is the fix for codes that were
    // shown on the customer dashboard but previously rejected at checkout.
    if (!rv_table_exists($conn, 'customer_promos')) throw new Exception('Voucher tidak ditemukan atau sudah berakhir');
    $lockSql = $lock ? ' FOR UPDATE' : '';
    $stmt = mysqli_prepare($conn, "SELECT * FROM customer_promos WHERE UPPER(promo_code)=? AND promo_code<>'' AND is_active=1 AND (starts_at IS NULL OR starts_at<=NOW()) AND (ends_at IS NULL OR ends_at>=NOW()) LIMIT 1$lockSql");
    if (!$stmt) throw new Exception('Mesin voucher belum siap');
    mysqli_stmt_bind_param($stmt, 's', $code); mysqli_stmt_execute($stmt);
    $res = mysqli_stmt_get_result($stmt); $row = $res ? mysqli_fetch_assoc($res) : null; mysqli_stmt_close($stmt);
    if (!$row) throw new Exception('Voucher tidak ditemukan atau sudah berakhir');

    $rowService = (string)($row['service_type'] ?? 'all');
    if (!in_array($rowService, ['all', $serviceType], true)) throw new Exception('Voucher tidak berlaku untuk layanan ini');
    if ($originalPrice < (int)($row['min_order'] ?? 0)) throw new Exception('Minimal tarif voucher belum terpenuhi');

    $pid = (int)$row['id'];
    $limit = max(1, (int)($row['per_user_limit'] ?? 1));
    rv_ensure_customer_usage($conn);
    if (rv_usage_count($conn, 'customer_promo_usages', 'promo_id', $pid, $userId, $lock) >= $limit)
        throw new Exception('Batas penggunaan voucher Anda sudah tercapai');
    $quota = (int)($row['quota_total'] ?? 0);
    if ($quota > 0 && rv_usage_count($conn, 'customer_promo_usages', 'promo_id', $pid, null, $lock) >= $quota)
        throw new Exception('Kuota voucher sudah habis');
    if ((int)($row['discount_value'] ?? 0) <= 0) throw new Exception('Voucher belum memiliki nilai diskon');
    $discount = vd_delivery_discount($row, $originalPrice, $discountBase);
    if ($discount <= 0) throw new Exception('Voucher tidak menghasilkan potongan untuk transaksi ini');

    return ['voucher_id'=>0,'promo_id'=>$pid,'source'=>'customer_promos','code'=>$code,'discount'=>$discount,'final_price'=>$originalPrice-$discount,'title'=>(string)($row['title'] ?? '')];
}
function rv_record_usage(mysqli $conn, array $voucher, int $userId, string $orderId): void {
    $source = (string)($voucher['source'] ?? '');
    $discount = (int)($voucher['discount'] ?? 0);
    if ($source === 'customer_promos' && (int)($voucher['promo_id'] ?? 0) > 0) {
        rv_ensure_customer_usage($conn);
        $pid = (int)$voucher['promo_id'];
        $stmt = mysqli_prepare($conn, 'INSERT INTO customer_promo_usages(promo_id,user_id,order_id,discount_amount) VALUES(?,?,?,?)');
        if (!$stmt) throw new Exception('Gagal menyiapkan pencatatan voucher');
        mysqli_stmt_bind_param($stmt, 'iisi', $pid, $userId, $orderId, $discount);
        if (!mysqli_stmt_execute($stmt)) throw new Exception('Gagal mencatat penggunaan voucher');
        mysqli_stmt_close($stmt);
        return;
    }
    $vid = (int)($voucher['voucher_id'] ?? 0);
    if ($vid <= 0) return;
    if (!rv_table_exists($conn, 'ride_voucher_usages')) throw new Exception('Pencatatan voucher belum siap');
    $stmt = mysqli_prepare($conn, 'INSERT INTO ride_voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES(?,?,?,?)');
    mysqli_stmt_bind_param($stmt, 'iisi', $vid, $userId, $orderId, $discount);
    if (!mysqli_stmt_execute($stmt)) throw new Exception('Gagal mencatat penggunaan voucher');
    mysqli_stmt_close($stmt);
    if(!transiva_schema_query($conn, 'UPDATE ride_vouchers SET quota_used=quota_used+1 WHERE id=' . $vid . ' LIMIT 1')) throw new Exception('Gagal memperbarui kuota voucher');
}


/** Schema is deployed before upload; never run DDL inside an order transaction. */
function rv_require_refund_schema(mysqli $conn): void {
    static $ready=[];$key=spl_object_id($conn);
    if (!isset($ready[$key])) {
        if (!rv_table_exists($conn,'voucher_usage_refunds')) throw new Exception('Pembaruan sistem voucher belum selesai. Hubungi admin.');
        $ready[$key]=true;
    }
}
function rv_usage_count(mysqli $conn,string $table,string $column,int $id,?int $uid,bool $lock): int {
    if (!in_array($table,['ride_voucher_usages','customer_promo_usages'],true)
        || !in_array($column,['voucher_id','promo_id'],true)) throw new LogicException('Invalid voucher usage source');
    if (!rv_table_exists($conn,$table)) throw new Exception('Pencatatan voucher belum siap');
    $where = $uid === null ? '' : ' AND u.user_id=?';
    // A locking SELECT is a current read even under REPEATABLE READ. COUNT(*) could use a stale snapshot.
    $sql="SELECT u.id FROM `$table` u LEFT JOIN voucher_usage_refunds f
        ON f.source_table='$table' AND f.usage_id=u.id WHERE u.`$column`=?$where AND f.usage_id IS NULL".($lock?' FOR UPDATE':'');
    $st=mysqli_prepare($conn,$sql);
    if ($uid===null) mysqli_stmt_bind_param($st,'i',$id); else mysqli_stmt_bind_param($st,'ii',$id,$uid);
    mysqli_stmt_execute($st);$r=mysqli_stmt_get_result($st);$count=mysqli_num_rows($r);mysqli_stmt_close($st);return $count;
}
/** Caller owns the order lock and transaction; usage rows are retained for financial audit. */
function rv_restore_cancelled_voucher(mysqli $conn,int $uid,string $oid): void {
    if ($uid<=0 || $oid==='') return;
    foreach (['ride_voucher_usages'=>'voucher_id','customer_promo_usages'=>'promo_id'] as $table=>$column) {
        if (!rv_table_exists($conn,$table)) continue;
        $q=mysqli_prepare($conn,"SELECT u.* FROM `$table` u WHERE user_id=? AND order_id=?");
        mysqli_stmt_bind_param($q,'is',$uid,$oid);mysqli_stmt_execute($q);$rows=mysqli_fetch_all(mysqli_stmt_get_result($q),MYSQLI_ASSOC);mysqli_stmt_close($q);
        if (!$rows) continue;
        rv_require_refund_schema($conn);
        foreach ($rows as $row) {
            $parent=$table==='ride_voucher_usages'?'ride_vouchers':'customer_promos';$pid=(int)$row[$column];
            // Same parent-before-usage locking order as checkout.
            $st=mysqli_prepare($conn,"SELECT id FROM `$parent` WHERE id=? FOR UPDATE");mysqli_stmt_bind_param($st,'i',$pid);mysqli_stmt_execute($st);mysqli_stmt_close($st);
            $id=(int)$row['id'];$amount=(int)$row['discount_amount'];
            $st=mysqli_prepare($conn,'INSERT IGNORE INTO voucher_usage_refunds(source_table,usage_id,user_id,order_id,discount_amount) VALUES(?,?,?,?,?)');
            mysqli_stmt_bind_param($st,'siisi',$table,$id,$uid,$oid,$amount);mysqli_stmt_execute($st);$inserted=mysqli_stmt_affected_rows($st);mysqli_stmt_close($st);
            if ($inserted===1 && $table==='ride_voucher_usages') {
                $st=mysqli_prepare($conn,'UPDATE ride_vouchers SET quota_used=GREATEST(0,quota_used-1) WHERE id=?');mysqli_stmt_bind_param($st,'i',$pid);mysqli_stmt_execute($st);mysqli_stmt_close($st);
            }
        }
    }
}
