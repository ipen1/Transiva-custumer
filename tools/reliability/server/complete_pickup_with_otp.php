<?php
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/native_auth.php';
require_once __DIR__ . '/p0_security_helper.php';
require_once __DIR__ . '/loyalty_season_helper.php';
require_once __DIR__ . '/customer_coin_helper.php';
require_once __DIR__ . '/reward_integrity_helper.php';
$auth = native_require_driver($conn);
require_once __DIR__.'/pickup_payment_helper.php';
require_once __DIR__.'/smart_waiting_helper.php';
sw_bootstrap($conn);
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$pickupTransaction=false;


function out(array $data): void {
    global $conn,$pickupTransaction;if($pickupTransaction){mysqli_rollback($conn);$pickupTransaction=false;}
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}
function tableColumnExists(mysqli $conn, string $table, string $column): bool {
    $table = mysqli_real_escape_string($conn, $table);
    $column = mysqli_real_escape_string($conn, $column);
    $q = mysqli_query($conn, "SHOW COLUMNS FROM `{$table}` LIKE '{$column}'");
    return $q && mysqli_num_rows($q) > 0;
}
function pickupStatusType(mysqli $conn): string {
    $q = mysqli_query($conn, "SHOW COLUMNS FROM `pickup_orders` LIKE 'status'");
    if (!$q) return '';
    $row = mysqli_fetch_assoc($q);
    return strtolower((string)($row['Type'] ?? ''));
}
function enumValues(string $type): array {
    if (strpos($type, 'enum(') !== 0) return [];
    preg_match_all("/'((?:[^'\\\\]|\\\\.)*)'/", $type, $matches);
    return array_map(static fn($v) => strtolower(stripslashes($v)), $matches[1] ?? []);
}
function ensureFinishedStatusSupported(mysqli $conn): bool {
    // pickup_orders.status sudah VARCHAR/final melalui migrasi deployment.
    return true;
}


$input = json_decode(file_get_contents('php://input'), true);
if (!is_array($input)) $input = $_POST;
$orderId = trim((string)($input['order_id'] ?? $input['id'] ?? ''));
$driver = trim((string)$auth['username']);
p0_rate_limit($conn, 'pickup_otp', 'u:'.(int)$auth['id'].'|o:'.$orderId, 5, 600);
$otp = preg_replace('/[^0-9]/', '', trim((string)($input['otp'] ?? '')));

if ($orderId === '' || $driver === '' || $otp === '') {
    out(['success'=>false, 'message'=>'Order, driver, atau OTP tidak lengkap']);
}

try {
order_require_transactional($conn,['users','pickup_orders','wallet_transactions','smart_waiting_bills']);
mysqli_begin_transaction($conn);$pickupTransaction=true;
$stmt = mysqli_prepare($conn,
    "SELECT *
       FROM pickup_orders
      WHERE (order_id=? OR CAST(id AS CHAR)=?)
        AND driver_username=?
      LIMIT 1 FOR UPDATE"
);
if (!$stmt) out(['success'=>false, 'message'=>'Gagal memeriksa order']);
mysqli_stmt_bind_param($stmt, 'sss', $orderId, $orderId, $driver);
mysqli_stmt_execute($stmt);
$row = mysqli_fetch_assoc(mysqli_stmt_get_result($stmt));
mysqli_stmt_close($stmt);

if (!$row) out(['success'=>false, 'message'=>'Order pickup tidak ditemukan untuk driver ini']);
$settlement=pickup_finish_payment_locked($conn,$row,$auth,$otp);
if(!empty($settlement['idempotent'])){mysqli_commit($conn);$pickupTransaction=false;out(['success'=>true,'status'=>'finished','idempotent'=>true]);}

if (!ensureFinishedStatusSupported($conn)) {
    out(['success'=>false, 'message'=>'Kolom status pickup belum mendukung status selesai']);
}

$id = (int)$row['id'];
$set = "status='finished'";
if (tableColumnExists($conn, 'pickup_orders', 'completed_at')) $set .= ", completed_at=NOW()";
if (tableColumnExists($conn, 'pickup_orders', 'updated_at')) $set .= ", updated_at=NOW()";
if (tableColumnExists($conn, 'pickup_orders', 'offer_expired_at')) $set .= ", offer_expired_at=NULL";
if (tableColumnExists($conn, 'pickup_orders', 'offered_driver')) $set .= ", offered_driver=NULL";
if (tableColumnExists($conn, 'pickup_orders', 'offer_action_token')) $set .= ", offer_action_token=NULL";


if (!mysqli_query($conn, "UPDATE pickup_orders SET {$set} WHERE id={$id} LIMIT 1")) {
    mysqli_rollback($conn); out(['success'=>false, 'message'=>'Gagal menyimpan status selesai: '.mysqli_error($conn)]);
}

// Verifikasi supaya respons sukses tidak diberikan bila database masih menyimpan nilai kosong.
$q = mysqli_query($conn, "SELECT status FROM pickup_orders WHERE id={$id} LIMIT 1");
$check = $q ? mysqli_fetch_assoc($q) : null;
$saved = strtolower(trim((string)($check['status'] ?? '')));
if ($saved !== 'finished') {
    mysqli_rollback($conn); out(['success'=>false, 'message'=>'Status selesai gagal tersimpan di database']);
}

mysqli_commit($conn);$pickupTransaction=false;
$row['order_type']='TransSend';$coinReward=['awarded'=>false,'coins'=>0,'deferred'=>true];
try{$coinReward=coin_award_finished_order($conn,$row);ri_point_sync_from_sources($conn,(int)($row['user_id']??0));}catch(Throwable $e){error_log('PICKUP_REWARD_DEFERRED type='.get_class($e));}
out([
    'success'=>true,
    'message'=>'Paket berhasil diterima dan order selesai',
    'status'=>'finished',
    'order_id'=>$orderId,
    'coin_reward'=>$coinReward
]);

}catch(DomainException $e){http_response_code(409);out(['success'=>false,'message'=>$e->getMessage()]);}
catch(Throwable $e){error_log('COMPLETE_PICKUP_FAILED type='.get_class($e));http_response_code(503);out(["success"=>false,"message"=>"Penyelesaian pesanan belum tersimpan. Coba kembali."]);}
