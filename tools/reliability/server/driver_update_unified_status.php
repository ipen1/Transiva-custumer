<?php
header("Content-Type: application/json; charset=utf-8");
require_once "config.php";
require_once __DIR__."/native_auth.php";
$auth = native_require_driver($conn);
require_once "payment_upgrade_helper.php";
require_once "driver_busy_helper.php";
require_once __DIR__."/driver_dispatch_helper.php";
require_once __DIR__."/transiva_push_helper.php";
require_once __DIR__."/loyalty_season_helper.php";
require_once __DIR__."/customer_coin_helper.php";
if(!function_exists('transiva_coin_award_safe')){
  function transiva_coin_award_safe(mysqli $c,array $order):array{
    try{
      if(function_exists('coin_award_finished_order_safe')) return coin_award_finished_order_safe($c,$order);
      if(function_exists('coin_award_finished_order')) return coin_award_finished_order($c,$order);
      error_log('COIN_REWARD_HELPER_MISSING order='.(string)($order['order_id']??$order['id']??''));
      return ['awarded'=>false,'coins'=>0,'deferred'=>true,'reason'=>'helper_missing'];
    }catch(Throwable $e){
      error_log('COIN_REWARD_ENDPOINT_NON_FATAL order='.(string)($order['order_id']??$order['id']??'').' error='.$e->getMessage());
      return ['awarded'=>false,'coins'=>0,'deferred'=>true];
    }
  }
}

require_once __DIR__."/reward_integrity_helper.php";

mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$unifiedTransaction=false;
require_once __DIR__."/pickup_payment_helper.php";
function out($arr, $http=200){ global $conn,$unifiedTransaction; if($unifiedTransaction){mysqli_rollback($conn);$unifiedTransaction=false;} http_response_code($http); echo json_encode($arr, JSON_UNESCAPED_UNICODE); exit; }
function esc($conn,$v){ return mysqli_real_escape_string($conn, trim((string)$v)); }

require_once __DIR__.'/ride_extension_helper.php';rex_bootstrap($conn);
$raw = json_decode(file_get_contents("php://input"), true);
if(!is_array($raw)) $raw = $_POST;
$driver = esc($conn, (string)$auth["username"]);
$source = esc($conn, $raw["source"] ?? "orders");
$id = intval($raw["id"] ?? 0);
$status = strtolower(esc($conn, $raw["status"] ?? ""));
$otp = esc($conn, $raw["otp"] ?? "");
if($status === 'completed') $status = 'finished';
$allowed = ["arrived_pickup","picked_up","on_delivery","arrived_delivery","finished"];
if($driver === "" || $id <= 0 || !in_array($status, $allowed, true)) out(["success"=>false,"code"=>"INVALID_STATUS","message"=>"Data update status tidak valid"],400);
$table = $source === "pickup_orders" ? "pickup_orders" : "orders";
if($table === "pickup_orders"){
  $exists = mysqli_query($conn, "SHOW TABLES LIKE 'pickup_orders'");
  if(!$exists || mysqli_num_rows($exists) == 0) out(["success"=>false,"code"=>"TABLE_MISSING","message"=>"Tabel pickup_orders belum ada"],500);
}

$previous = [
  'arrived_pickup'   => ['taken','driver_accepted'],
  'picked_up'        => ['arrived_pickup'],
  'on_delivery'      => ['arrived_pickup','picked_up'],
  'arrived_delivery' => ['on_delivery'],
  'finished'         => ['arrived_delivery']
];

require_once __DIR__.'/smart_waiting_helper.php';sw_bootstrap($conn);
$cols=pu_ensure_schema($conn,$table);
try{
if($table==='pickup_orders')order_require_transactional($conn,['users','pickup_orders','wallet_transactions','smart_waiting_bills']);
mysqli_begin_transaction($conn);$unifiedTransaction=true;
$q = mysqli_query($conn, "SELECT * FROM `$table` WHERE id='$id' LIMIT 1 FOR UPDATE");
$order = $q ? mysqli_fetch_assoc($q) : null;
if(!$order){ mysqli_rollback($conn); out(["success"=>false,"code"=>"ORDER_NOT_FOUND","message"=>"Order tidak ditemukan"],404); }
$orderDriver = trim((string)($order["driver"] ?? ""));
$orderDriver2 = trim((string)($order["driver_username"] ?? ""));
$orderDriver3 = trim((string)($order["assigned_driver"] ?? ""));
if(strcasecmp($orderDriver,$driver)!==0 && strcasecmp($orderDriver2,$driver)!==0 && strcasecmp($orderDriver3,$driver)!==0){
  mysqli_rollback($conn); out(["success"=>false,"code"=>"ORDER_OWNERSHIP","message"=>"Order bukan milik driver ini"],403);
}
$current = strtolower(trim((string)($order['status'] ?? '')));
if($current === $status){ if($table==='pickup_orders' && $status==='finished')pickup_finish_payment_locked($conn,$order,$auth,$otp); if(!mysqli_commit($conn))throw new RuntimeException('Komit transaksi belum terkonfirmasi.');$unifiedTransaction=false; out(["success"=>true,"message"=>"Status sudah tersimpan","status"=>$status,"idempotent"=>true]); }
if(in_array($current,['finished','completed','canceled','cancelled'],true)){
  mysqli_rollback($conn); out(["success"=>false,"code"=>"ORDER_FINAL","message"=>"Order sudah berada pada status final"],409);
}
if(!in_array($current, $previous[$status], true)){
  mysqli_rollback($conn); out(["success"=>false,"code"=>"INVALID_TRANSITION","message"=>"Perubahan status tidak valid: $current → $status","current_status"=>$current,"requested_status"=>$status],409);
}
if($table==='orders' && $status==='finished'){
  try{rex_guard_finish($conn,$order);}catch(Throwable $e){mysqli_rollback($conn);out(['success'=>false,'message'=>$e->getMessage()],409);}
}
if($status === "finished"){
  if((int)($order["customer_received"] ?? 0) !== 1){ mysqli_rollback($conn); out(["success"=>false,"code"=>"WAITING_CUSTOMER","message"=>"Tunggu customer menekan Terima Pesanan terlebih dahulu","waiting_customer_confirmation"=>true],409); }
  if(strtolower(trim((string)($order["price_change_status"] ?? ""))) === "pending"){
    mysqli_rollback($conn); out(["success"=>false,"code"=>"PRICE_CHANGE_PENDING","message"=>"Perubahan harga masih menunggu konfirmasi customer"],409);
  }
}
if(in_array($status,['arrived_pickup','arrived_delivery'],true)&&!sw_near($order,$raw,$status==='arrived_pickup'?'pickup':'delivery')){mysqli_rollback($conn);out(['success'=>false,'message'=>'Driver harus berada dalam 200 meter dari titik.'],409);}
if($status==='finished' && $table==='pickup_orders')pickup_finish_payment_locked($conn,$order,$auth,$otp);
$set = "status='".esc($conn,$status)."'";
if(isset($cols["updated_at"])) $set .= ", updated_at=NOW()";
if($status === "arrived_pickup" && isset($cols["arrived_pickup_at"])) $set .= ", arrived_pickup_at=NOW()";
if(($status === "picked_up" || $status === "on_delivery") && isset($cols["picked_up_at"])) $set .= ", picked_up_at=NOW()";
if($status === "arrived_delivery" && isset($cols["arrived_delivery_at"])) $set .= ", arrived_delivery_at=NOW()";
if($status === "finished" && isset($cols["completed_at"])) $set .= ", completed_at=NOW()";
$currentEsc = esc($conn,$current);
$ok = mysqli_query($conn, "UPDATE `$table` SET $set WHERE id='$id' AND status='$currentEsc' LIMIT 1");
if(!$ok || mysqli_affected_rows($conn)!==1){ mysqli_rollback($conn); out(["success"=>false,"code"=>"STATE_CONFLICT","message"=>"Status berubah bersamaan. Muat ulang order lalu coba lagi."],409); }
if($status==='arrived_pickup')sw_start($conn,$table,$order,'pickup','Penjemputan');
if($status==='arrived_delivery')sw_start($conn,$table,$order,'delivery','Tujuan akhir');
if(in_array($status,['picked_up','on_delivery'],true))sw_close($conn,$table,$id);
$coinReward=['awarded'=>false,'coins'=>0];
if(!mysqli_commit($conn))throw new RuntimeException('Komit transaksi belum terkonfirmasi.');$unifiedTransaction=false;
}catch(DomainException $e){out(['success'=>false,'message'=>$e->getMessage()],409);}
catch(Throwable $e){error_log('UNIFIED_STATUS_TRANSACTION_FAILED type='.get_class($e));out(['success'=>false,'message'=>'Status belum tersimpan. Muat ulang pesanan dan coba kembali.'],503);}
try{
if($status==='finished'){
  if($table==='pickup_orders')$order['order_type']='TransSend';
  require_once __DIR__.'/referral_cash_helper.php';rf_process_safe($conn,(int)($order['user_id']??$order['customer_id']??0));
  $coinReward=transiva_coin_award_safe($conn,$order);
  try{ri_point_sync_from_sources($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('POINT_SYNC_NON_FATAL unified order='.(string)($order['order_id']??$id).' error='.$e->getMessage());}
}

if(in_array($status,["arrived_pickup","arrived_delivery"],true)){
  $uid=(int)($order["user_id"] ?? 0);
  if($uid>0){
    $uq=mysqli_query($conn,"SELECT fcm_token FROM users WHERE id='$uid' LIMIT 1"); $ur=$uq?mysqli_fetch_assoc($uq):null; $fcm=trim((string)($ur["fcm_token"]??""));
    if($fcm!==""){
      $isPickup=$status==="arrived_pickup";
      @send_fcm_notification($fcm,$isPickup?"Driver tiba di penjemputan":"Driver tiba di pengantaran",$isPickup?"Driver tiba. Smart Waiting gratis 5 menit, lalu Rp500/menit tambahan.":"Driver tiba. Smart Waiting gratis 5 menit, lalu Rp500/menit. Konfirmasi diterima untuk menghentikan waktu tunggu.",[
        "app"=>"transiva","type"=>"customer_order_status","event"=>$status,"smart_waiting"=>"1","waiting_free_seconds"=>"300","waiting_rate"=>"500","status"=>$status,"screen"=>"customer_order","source"=>$table,
        "order_id"=>(string)($order["order_id"]??$id),"order_db_id"=>(string)$id,"driver"=>$driver,"wake_screen"=>"1","full_screen"=>"arrival","priority"=>"high","channel_id"=>"transiva_order_channel","click_action"=>"OPEN_TRANSIVA"
      ]);
    }
  }
}
if($status === "finished"){
  $stillBusy=dd_driver_has_other_active($conn,$driver,$table,$id);
  if(function_exists("driver_set_busy")) @driver_set_busy($conn,$driver,$stillBusy);
  else @mysqli_query($conn,"UPDATE driver_profiles dp JOIN users u ON u.id=dp.user_id SET dp.is_busy=".($stillBusy?1:0)." WHERE u.username='".mysqli_real_escape_string($conn,$driver)."'");
}
$saved=$conn->query("SELECT * FROM `$table` WHERE id=$id")->fetch_assoc();$waiting=sw_snapshot($conn,$table,$saved);
// The driver must receive the OTP from the recipient, never from a status response.
unset($saved['delivery_otp'],$saved['receiver_otp'],$saved['otp']);
}catch(Throwable $e){error_log('UNIFIED_POST_COMMIT_DEFERRED type='.get_class($e));out(['success'=>true,'status'=>$status,'message'=>'Status tersimpan; informasi tambahan akan dimuat ulang.','coin_reward'=>$coinReward,'refresh_required'=>true]);}

out(["success"=>true,"message"=>"Status berhasil diperbarui","status"=>$status,"coin_reward"=>$coinReward,"order"=>$saved,"receipt"=>['smart_waiting'=>$waiting,'waiting_fee'=>$waiting['applied_fee'],'customer_contribution'=>(int)$saved['price'],'order'=>$saved]]);
