<?php
require_once __DIR__.'/voucher_discount_rules.php';

header("Content-Type: application/json");

include "config.php";
require_once __DIR__ . "/driver_busy_helper.php";
include "transiva_pay_helper.php";
require_once __DIR__ . "/loyalty_season_helper.php";
require_once __DIR__ . "/customer_coin_helper.php";
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

require_once __DIR__ . "/reward_integrity_helper.php";

$data = json_decode(file_get_contents("php://input"), true);

function out($success, $message, $extra = []){
  echo json_encode(array_merge([
    "success" => $success,
    "message" => $message
  ], $extra));
  exit;
}

function fee_from_db($conn, $serviceType, $ongkir){
  $serviceType = tp_clean($conn, $serviceType);
  $ongkir = intval($ongkir);

  $q = mysqli_query($conn, "
    SELECT *
    FROM service_fee
    WHERE service_type='$serviceType'
    AND is_active=1
    LIMIT 1
  ");

  if(!$q || mysqli_num_rows($q) <= 0){
    return 0;
  }

  $row = mysqli_fetch_assoc($q);
  $feeType = strtolower(trim((string)($row["fee_type"] ?? "")));
  $feeValue = floatval($row["fee_value"] ?? 0);

  if($feeType === "percent"){
    return intval(ceil($ongkir * ($feeValue / 100)));
  }

  return intval($feeValue);
}

function grossup_fee_from_db($conn, $price){
  $price = intval($price);

  $q = mysqli_query($conn, "
    SELECT fee
    FROM transfood_fee_rules
    WHERE is_active=1
    AND min_amount <= '$price'
    AND (
      max_amount IS NULL
      OR max_amount >= '$price'
    )
    ORDER BY min_amount DESC
    LIMIT 1
  ");

  if(!$q || mysqli_num_rows($q) <= 0){
    return 0;
  }

  $row = mysqli_fetch_assoc($q);
  return intval($row["fee"] ?? 0);
}

function food_voucher_discount($order){
  return vd_order_breakdown($order)['total_discount'];
}

function parse_food_fee($conn, $order){
  $note = tp_note_array($order["note"] ?? "");
  $deliveryFee = intval($note["delivery_fee"] ?? 0);

  if($deliveryFee <= 0){
    $deliveryFee = intval($order["price"] ?? 0) - intval($note["food_total"] ?? 0);
  }

  if($deliveryFee < 0){
    $deliveryFee = 0;
  }

  $appFee = fee_from_db($conn, "Transbike", $deliveryFee);
  $grossupFee = intval($note["grossup_fee"] ?? 0);

  if($grossupFee <= 0 && isset($note["items"]) && is_array($note["items"])){
    foreach($note["items"] as $item){
      $qty = intval($item["qty"] ?? 1);
      if($qty <= 0){ $qty = 1; }

      $original = intval($item["original_price"] ?? $item["merchant_price"] ?? $item["base_price"] ?? 0);
      $appPrice = intval($item["price"] ?? $item["app_price"] ?? 0);

      if($original > 0){
        $grossupFee += grossup_fee_from_db($conn, $original) * $qty;
      }elseif(isset($item["grossup_fee"])){
        $grossupFee += intval($item["grossup_fee"]) * $qty;
      }else{
        $grossupFee += 0;
      }
    }
  }

  return [
    "delivery_fee" => $deliveryFee,
    "app_fee" => $appFee,
    "merchant_grossup_fee" => $grossupFee,
    "total_fee" => $appFee + $grossupFee
  ];
}

if(!$data){
  out(false, "Data kosong");
}

$id = intval($data["id"] ?? 0);
$orderCode = tp_clean($conn, $data["order_id"] ?? "");
$userId = intval($data["user_id"] ?? 0);

if($id <= 0 && $orderCode === ""){
  out(false, "Order kosong");
}

require_once __DIR__.'/smart_waiting_helper.php';require_once __DIR__.'/payment_upgrade_helper.php';sw_bootstrap($conn);pu_ensure_schema($conn,'orders');
mysqli_begin_transaction($conn);

try{

  if($id > 0){
    $where = "id='$id'";
  }else{
    $where = "order_id='$orderCode'";
  }

  $getOrder = mysqli_query($conn, "
    SELECT *
    FROM orders
    WHERE $where
    LIMIT 1
    FOR UPDATE
  ");

  if(!$getOrder || mysqli_num_rows($getOrder) <= 0){
    throw new Exception("Order tidak ditemukan");
  }

  $order = mysqli_fetch_assoc($getOrder);

  if($userId > 0 && intval($order["user_id"] ?? 0) !== $userId){
    throw new Exception("Order bukan milik customer ini");
  }

  if(!tp_is_food_order($order)){
    throw new Exception("Konfirmasi ini hanya untuk pesanan food");
  }

  $status = strtolower(trim((string)($order["status"] ?? "")));

  if(in_array($status, ["finished", "completed", "finish"], true)){
    if(!mysqli_commit($conn))throw new RuntimeException('Komit transaksi belum terkonfirmasi.');
    out(true, "Pesanan sudah selesai", ["status" => "finished"]);
  }

  if($status !== "arrived_delivery"){
    throw new Exception("Pesanan belum tiba di lokasi customer");
  }

  $order=sw_finalize($conn,'orders',$order);
  $escrowAmount = tp_get_escrow_amount($order);
  $fee = parse_food_fee($conn, $order);
  $releaseAmount = $escrowAmount - intval($fee["total_fee"] ?? 0);

  if($releaseAmount < 0){
    $releaseAmount = 0;
  }

  $release = tp_release_held_order_to_driver($conn, $order, $releaseAmount);

  // Protected Driver Earning untuk TransFood:
  // customer membayar setelah voucher, selisih voucher dibayar Transiva ke wallet driver.
  $voucherDiscount = food_voucher_discount($order);
  $driver = tp_clean($conn, $order["driver"] ?? "");
  $promoSubsidy = ["applied"=>false, "amount"=>0];
  if($driver !== "" && $voucherDiscount > 0){
    $promoSubsidy = tp_adjust_driver_wallet(
      $conn,
      $driver,
      $voucherDiscount,
      "promo_subsidy",
      "PROMO-".($order["order_id"] ?? $order["id"]),
      "Subsidi voucher Transiva untuk pesanan TransFood ".($order["order_id"] ?? $order["id"]),
      "in"
    );
  }

  $updatedAt = date("Y-m-d H:i:s");
  $orderId = intval($order["id"]);

  $updateOrder = mysqli_query($conn, "
    UPDATE orders
    SET status='finished', updated_at='$updatedAt'
    WHERE id='$orderId'
    AND status='arrived_delivery'
    LIMIT 1
  ");

  if(!$updateOrder || mysqli_affected_rows($conn) < 1){
    throw new Exception("Gagal menyelesaikan pesanan");
  }

  if($driver !== ""){
    if(!driver_set_busy_by_username($conn, $driver, false)) {
      throw new Exception("Gagal mereset status sibuk driver");
    }
  }

  // Commit pembayaran/status dulu. Reward tidak boleh menggagalkan penyelesaian order.
  if(!mysqli_commit($conn))throw new RuntimeException('Komit transaksi belum terkonfirmasi.');
  require_once __DIR__.'/referral_cash_helper.php';rf_process_safe($conn,(int)($order['user_id']??$order['customer_id']??0));
  $coinReward = transiva_coin_award_safe($conn, $order);
  try{rm_sync($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('MISSION_POST_COMMIT_DEFERRED '.$e->getMessage());}
  try{ri_point_sync_from_sources($conn,(int)($order["user_id"]??0));}catch(Throwable $e){error_log('POINT_SYNC_NON_FATAL food order='.(string)($order['order_id']??$orderId).' error='.$e->getMessage());}

  out(true, "Pesanan selesai dan saldo tertahan sudah dicairkan ke driver", [
    "status" => "finished",
    "escrow_amount" => $escrowAmount,
    "released_to_driver" => $release,
    "fee" => $fee,
    "discount_breakdown_version" => 2,
    "voucher_discount" => vd_order_breakdown($order)["voucher_discount"],
    "point_discount" => vd_order_breakdown($order)["point_discount"],
    "total_discount" => $voucherDiscount,
    "promo_subsidy" => $voucherDiscount,
    "subsidy_mutation" => $promoSubsidy,
    "driver_receive_from_customer" => $releaseAmount,
    "driver_receive_total" => $releaseAmount + $voucherDiscount,
    "coin_reward" => $coinReward
  ]);

}catch(Throwable $e){

  mysqli_rollback($conn);
  out(false, $e->getMessage());

}

?>
