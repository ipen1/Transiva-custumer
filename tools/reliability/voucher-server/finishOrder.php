<?php
require_once __DIR__.'/voucher_discount_rules.php';

header("Content-Type: application/json");

include "config.php";
require_once __DIR__."/transiva_regional_pricing.php";
require_once __DIR__.'/customer_coin_helper.php';
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

require_once __DIR__ . "/native_auth.php";
require_once __DIR__ . "/driver_busy_helper.php";
require_once __DIR__ . "/driver_dispatch_helper.php";
include "transiva_pay_helper.php";
require_once __DIR__ . "/payment_upgrade_helper.php";

require_once __DIR__.'/ride_extension_helper.php';rex_bootstrap($conn);
$data = json_decode(file_get_contents("php://input"), true);
$authDriver = native_require_driver($conn);
$authDriverUsername = trim((string)($authDriver["username"] ?? ""));

function sendRes($success, $message, $extra = []){
  echo json_encode(array_merge([
    "success" => $success,
    "message" => $message
  ], $extra));
  exit;
}

function toInt($value){
  return intval($value ?? 0);
}

function esc($conn, $value){
  return mysqli_real_escape_string($conn, trim((string)($value ?? "")));
}

function normalizeServiceType($type){
  $type = strtolower(trim((string)$type));

  if(in_array($type, ["transcar", "car", "mobil"])){
    return "Transcar";
  }

  if(in_array($type, ["food", "transfood"])){
    return "Transfood";
  }

  return "Transbike";
}

function getGrossupFeeFromDb($conn, $price){

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

  $row = mysqli_fetch_assoc($q);

  return $row ? intval($row["fee"]) : 0;

}

function getDriverFeeFromDb($conn, $serviceType, $ongkir){

  $serviceType = esc($conn, $serviceType);
  $ongkir = intval($ongkir);

  $q = mysqli_query($conn, "
    SELECT *
    FROM service_fee
    WHERE service_type='$serviceType'
    AND is_active=1
    LIMIT 1
  ");

  $row = mysqli_fetch_assoc($q);

  if(!$row){
    return 0;
  }

  $feeType = strtolower(trim((string)$row["fee_type"]));
  $feeValue = floatval($row["fee_value"]);

  if($feeType === "percent"){
    return ceil($ongkir * ($feeValue / 100));
  }

  return intval($feeValue);

}

function calculateDeliveryFeeFromDb($conn,$serviceType,$distanceKm){
  return trp_calculate(trp_get_rule($conn,(string)$serviceType),(float)$distanceKm);
}

function getVoucherDiscount($order){
  return vd_order_breakdown($order)['total_discount'];
}

function saveDriverReceipt($conn, $id, $driver, $receipt){
  $orderCode = esc($conn, $receipt["order"]["order_id"] ?? ($receipt["order"]["id"] ?? $id));
  $receiptJson = esc($conn, json_encode($receipt, JSON_UNESCAPED_UNICODE));
  $ongkir = intval($receipt["ongkir"] ?? 0);
  $merchantOrder = intval($receipt["merchant_order"] ?? 0);
  $appFee = intval($receipt["app_fee"] ?? 0);
  $merchantGrossupFee = intval($receipt["merchant_grossup_fee"] ?? 0);
  $totalPendapatan = intval($receipt["total_pendapatan"] ?? 0);
  $totalPotongan = intval($receipt["total_potongan"] ?? 0);
  $saldoSebelum = intval($receipt["saldo_sebelum"] ?? 0);
  $saldoSaatIni = intval($receipt["saldo_saat_ini"] ?? 0);
  $itemsCount = intval($receipt["items_count"] ?? 0);
  $isFood = !empty($receipt["is_food"]) ? 1 : 0;
  $driverSafe = esc($conn, $driver);

  $exists = mysqli_query($conn, "SELECT id FROM driver_transaction_receipts WHERE order_id='".intval($id)."' LIMIT 1");
  if($exists && mysqli_num_rows($exists) > 0) return true;

  $ok = mysqli_query($conn, "
    INSERT INTO driver_transaction_receipts
    (order_id,order_code,driver,ongkir,merchant_order,app_fee,merchant_grossup_fee,total_pendapatan,total_potongan,saldo_sebelum,saldo_saat_ini,sisa_saldo,items_count,is_food,receipt_json)
    VALUES
    ('".intval($id)."','$orderCode','$driverSafe','$ongkir','$merchantOrder','$appFee','$merchantGrossupFee','$totalPendapatan','$totalPotongan','$saldoSebelum','$saldoSaatIni','$saldoSaatIni','$itemsCount','$isFood','$receiptJson')
  ");
  if(!$ok) throw new Exception(mysqli_error($conn));
  return true;
}

function parseFoodData($conn, $note){

  $result = [
    "is_food" => false,
    "food_total" => 0,
    "app_food_total" => 0,
    "grossup_fee" => 0,
    "items_count" => 0,
    "delivery_fee" => 0,
    "delivery_mode" => "standard",
    "delivery_label" => "Pengantaran Standar"
  ];

  $json = json_decode($note ?? "", true);

  if(
    !$json ||
    !is_array($json) ||
    strtolower($json["type"] ?? "") !== "food"
  ){
    return $result;
  }

  $items = $json["items"] ?? [];

  if(!is_array($items)){
    $items = [];
  }

  $foodTotal = 0;
  $appFoodTotal = 0;
  $grossupFee = 0;
  $itemsCount = 0;

  foreach($items as $item){

    $qty = intval($item["qty"] ?? 1);

    if($qty <= 0){
      $qty = 1;
    }

    $originalPrice = intval(
      $item["original_price"] ??
      $item["merchant_price"] ??
      $item["base_price"] ??
      0
    );

    $appPrice = intval(
      $item["price"] ??
      $item["app_price"] ??
      0
    );

    if($originalPrice <= 0 && $appPrice > 0){

      $oldFee = intval(
        $item["grossup_fee"] ??
        $item["markup"] ??
        0
      );

      $originalPrice = max(0, $appPrice - $oldFee);

    }

    if($originalPrice <= 0){
      $originalPrice = $appPrice;
    }

    $itemGrossup = getGrossupFeeFromDb($conn, $originalPrice);
    $realAppPrice = $originalPrice + $itemGrossup;

    $foodTotal += $originalPrice * $qty;
    $appFoodTotal += $realAppPrice * $qty;
    $grossupFee += $itemGrossup * $qty;
    $itemsCount += $qty;

  }

  if($foodTotal <= 0){
    $foodTotal = intval($json["food_total"] ?? 0);
  }

  if($appFoodTotal <= 0 && $foodTotal > 0){
    $appFoodTotal = $foodTotal + $grossupFee;
  }

  $deliveryMode = strtolower(trim((string)($json["delivery_mode"] ?? "standard")));

  if(in_array($deliveryMode, ["hemat", "economy", "ekonomi", "murah"])){
    $deliveryMode = "hemat";
  }else{
    $deliveryMode = "standard";
  }

  $deliveryLabel = $deliveryMode === "hemat"
    ? "Pengantaran Hemat"
    : "Pengantaran Standar";

  return [
    "is_food" => true,
    "food_total" => $foodTotal,
    "app_food_total" => $appFoodTotal,
    "grossup_fee" => $grossupFee,
    "items_count" => $itemsCount,
    "delivery_fee" => intval($json["delivery_fee"] ?? 0),
    "delivery_mode" => $deliveryMode,
    "delivery_label" => $deliveryLabel
  ];

}

if(!$data){
  sendRes(false, "Data kosong");
}

$id = intval($data["id"] ?? 0);

if($id <= 0){
  sendRes(false, "ID order kosong");
}

require_once __DIR__.'/smart_waiting_helper.php';sw_bootstrap($conn);
$finishCols=pu_ensure_schema($conn,"orders");
mysqli_begin_transaction($conn);

try{

  $getOrder = mysqli_query($conn, "
    SELECT *
    FROM orders
    WHERE id='$id'
    LIMIT 1
    FOR UPDATE
  ");

  if(!$getOrder || mysqli_num_rows($getOrder) <= 0){
    throw new Exception("Order tidak ditemukan");
  }

  $order = mysqli_fetch_assoc($getOrder);
  rex_guard_finish($conn,$order);

  $orderOwner = trim((string)($order["driver"] ?? $order["driver_username"] ?? ""));
  if ($authDriverUsername === "" || $orderOwner === "" || strcasecmp($orderOwner, $authDriverUsername) !== 0) {
    throw new Exception("ORDER_OWNERSHIP: Order bukan milik driver yang login");
  }

  // Semua pembayaran wajib dikonfirmasi diterima oleh customer sebelum driver menyelesaikan order.

  if((int)($order["customer_received"] ?? 0) !== 1){
    throw new Exception("Tunggu customer menekan Terima Pesanan terlebih dahulu");
  }
  if(strtolower(trim((string)($order["price_change_status"] ?? "none"))) === "pending"){
    throw new Exception("Perubahan harga masih menunggu konfirmasi customer");
  }

  $driver = esc($conn, $order["driver"] ?? "");

  if($driver === ""){
    throw new Exception("Driver tidak ditemukan di order");
  }

  $serviceType = normalizeServiceType($order["order_type"] ?? "");
  $foodData = parseFoodData($conn, $order["note"] ?? "");

  if($foodData["is_food"]){
    $serviceType = "Transfood";
  }

  $merchantOrder = $foodData["is_food"] ? $foodData["food_total"] : 0;
  $merchantGrossupFee = $foodData["is_food"] ? $foodData["grossup_fee"] : 0;

  $distanceKm = floatval($order["distance_km"] ?? 0);

  if($serviceType === "Transfood"){

    /*
      Untuk order makanan, ongkir final sudah dihitung aman saat create_food_order.php
      dan disimpan di note.delivery_fee. Di sini jangan hitung ulang standar,
      supaya fee aplikasi mengikuti ongkir final: standar atau hemat.
    */
    $ongkir = intval($foodData["delivery_fee"] ?? 0);

    if($ongkir <= 0){
      $ongkir = intval($order["price"] ?? 0) - intval($merchantOrder);
    }

    if($ongkir <= 0){
      throw new Exception("Ongkir makanan tidak valid");
    }

    $appFee = getDriverFeeFromDb($conn, "Transbike", $ongkir);

  }else{

    $ongkir = intval($order['original_price'] ?? 0);
    if($ongkir <= 0){
      $ongkir = calculateDeliveryFeeFromDb($conn, $serviceType, $distanceKm);
    }

    if($ongkir <= 0){
      throw new Exception("Aturan ongkir belum diatur di database");
    }

    $appFee = getDriverFeeFromDb($conn, $serviceType, $ongkir);

  }

  $waitingState=sw_snapshot($conn,'orders',$order);
  $waitingFee=(int)$waitingState['applied_fee'];
  $ongkir += $waitingFee;
  $totalPendapatan = $ongkir + $merchantOrder;
  $totalPotongan = $appFee + $merchantGrossupFee;
  $voucherDiscount = getVoucherDiscount($order);
  // Voucher resmi Transiva tidak boleh mengurangi hak driver, termasuk TransFood.
  // Pada TransFood driver membawa nilai order merchant + ongkir; seluruh voucher disubsidi Transiva.
  $driverVoucherSubsidy = $foodData["is_food"]
    ? min($voucherDiscount, max(0, $merchantOrder + $merchantGrossupFee + $ongkir))
    : min($voucherDiscount, $ongkir);
  $customerContribution = max(0, intval($order["price"] ?? 0));
  if($customerContribution <= 0 && $voucherDiscount > 0){
    $customerContribution = max(0, $ongkir - $voucherDiscount);
  }

  $getUser = mysqli_query($conn, "
    SELECT *
    FROM users
    WHERE username='$driver'
    AND role='driver'
    LIMIT 1
    FOR UPDATE
  ");

  if(!$getUser || mysqli_num_rows($getUser) <= 0){
    throw new Exception("Data driver tidak ditemukan");
  }

  $user = mysqli_fetch_assoc($getUser);
  $saldoSebelum = toInt($user["balance"]);

  if($order["status"] === "finished"){
    if (!driver_set_busy_by_username($conn, $driver, dd_driver_has_other_active($conn,$driver,'orders',$id))) {
      throw new Exception("Gagal mereset status sibuk driver");
    }

    if(!mysqli_commit($conn))throw new RuntimeException('Komit penyelesaian belum terkonfirmasi.');
    require_once __DIR__.'/referral_cash_helper.php';rf_process_safe($conn,(int)($order['user_id']??$order['customer_id']??0));
  $coinReward=transiva_coin_award_safe($conn,$order);
    try{rm_sync($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('MISSION_POST_COMMIT_DEFERRED '.$e->getMessage());}
    try{ri_point_sync_from_sources($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('POINT_SYNC_NON_FATAL finish order='.(string)($order['order_id']??$id).' error='.$e->getMessage());}

    sendRes(true, "Order sudah selesai", [
      "status" => "finished",
      "order" => $order,
      "fee" => $totalPotongan,
      "receipt" => [
        "order" => $order,
        "service_type" => $serviceType,
        "delivery_mode" => $foodData["delivery_mode"],
        "delivery_label" => $foodData["delivery_label"],
        "ongkir" => $ongkir,
        "merchant_order" => $merchantOrder,
        "app_fee" => $appFee,
        "merchant_grossup_fee" => $merchantGrossupFee,
        "total_pendapatan" => $totalPendapatan,
        "total_potongan" => $totalPotongan,
        "saldo_sebelum" => $saldoSebelum,
        "saldo_saat_ini" => $saldoSebelum,
        "sisa_saldo" => $saldoSebelum,
        "items_count" => $foodData["items_count"],
        "is_food" => $foodData["is_food"]
      ]
    ]);

  }

  if($order["status"] !== "arrived_delivery"){
    throw new Exception("Driver belum tiba di lokasi delivery");
  }

  if(
    $foodData["is_food"] &&
    tp_is_balance_method(tp_get_payment_method($order)) &&
    in_array(tp_get_payment_status($order), ["paid", "paid_held", "held"], true)
  ){
    throw new Exception("Pesanan non-tunai menunggu customer menekan Pesanan Diterima agar saldo tertahan cair ke driver");
  }

  if(
    tp_is_ride_order($order) &&
    tp_is_balance_method(tp_get_payment_method($order)) &&
    in_array(tp_get_payment_status($order), ["paid", "paid_held", "held"], true)
  ){
    $escrowAmount = tp_get_escrow_amount($order);
    $releaseAmount = max(0, $escrowAmount);

    $updateOrder = mysqli_query($conn, "
      UPDATE orders
      SET status='finished', updated_at=NOW()
      WHERE id='$id'
      AND status='arrived_delivery'
      LIMIT 1
    ");

    if(!$updateOrder || mysqli_affected_rows($conn) < 1){
      throw new Exception("Order gagal diselesaikan");
    }

    $release = tp_release_held_order_to_driver($conn, $order, $releaseAmount);
    $subsidy = ["applied"=>false,"amount"=>0];
    if($driverVoucherSubsidy > 0){
      $subsidy = tp_adjust_driver_wallet($conn, $driver, $driverVoucherSubsidy, "promo_subsidy", "PROMO-".($order["order_id"] ?? $id), "Subsidi voucher Transiva untuk pesanan ".($order["order_id"] ?? $id), "in");
    }
    $feeMutation = ["applied"=>false,"amount"=>0];
    if($totalPotongan > 0){
      $feeMutation = tp_adjust_driver_wallet($conn, $driver, $totalPotongan, "app_fee", "FEE-".($order["order_id"] ?? $id), "Biaya layanan pesanan ".($order["order_id"] ?? $id), "out");
    }

    $getUpdatedUser = mysqli_query($conn, "SELECT balance FROM users WHERE username='$driver' AND role='driver' LIMIT 1");
    $updatedUser = mysqli_fetch_assoc($getUpdatedUser);
    $saldoSaatIni = toInt($updatedUser["balance"] ?? 0);
    $getUpdatedOrder = mysqli_query($conn, "SELECT * FROM orders WHERE id='$id' LIMIT 1");
    $updatedOrder = mysqli_fetch_assoc($getUpdatedOrder);
    $receipt = [
      "smart_waiting"=>$waitingState,"waiting_fee"=>$waitingFee,"fare_before_waiting"=>$ongkir-$waitingFee,
      "order"=>$updatedOrder, "service_type"=>$serviceType,
      "delivery_mode"=>$foodData["delivery_mode"], "delivery_label"=>$foodData["delivery_label"],
      "ongkir"=>$ongkir, "merchant_order"=>$merchantOrder, "app_fee"=>$appFee,
      "merchant_grossup_fee"=>$merchantGrossupFee, "total_pendapatan"=>$totalPendapatan,
      "total_potongan"=>$totalPotongan, "discount_breakdown_version"=>2, "voucher_discount"=>vd_order_breakdown($order)["voucher_discount"], "point_discount"=>vd_order_breakdown($order)["point_discount"], "total_discount"=>$driverVoucherSubsidy,
      "promo_subsidy"=>$driverVoucherSubsidy, "customer_contribution"=>$releaseAmount,
      "driver_gross_protected"=>$ongkir, "driver_net"=>max(0,$ongkir-$totalPotongan),
      "saldo_sebelum"=>$saldoSebelum, "saldo_saat_ini"=>$saldoSaatIni, "sisa_saldo"=>$saldoSaatIni,
      "items_count"=>$foodData["items_count"], "is_food"=>$foodData["is_food"]
    ];
    saveDriverReceipt($conn, $id, $driver, $receipt);

    if (!driver_set_busy_by_username($conn, $driver, dd_driver_has_other_active($conn,$driver,'orders',$id))) {
      throw new Exception("Gagal mereset status sibuk driver");
    }

    if(!mysqli_commit($conn))throw new RuntimeException('Komit penyelesaian belum terkonfirmasi.');
    require_once __DIR__.'/referral_cash_helper.php';rf_process_safe($conn,(int)($order['user_id']??$order['customer_id']??0));
  $coinReward=transiva_coin_award_safe($conn,$order);
    try{rm_sync($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('MISSION_POST_COMMIT_DEFERRED '.$e->getMessage());}
    try{ri_point_sync_from_sources($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('POINT_SYNC_NON_FATAL finish order='.(string)($order['order_id']??$id).' error='.$e->getMessage());}

    sendRes(true, "Order non-tunai selesai dan saldo dicairkan ke driver", [
      "status" => "finished",
      "payment_method" => tp_get_payment_method($order),
      "payment_status" => "released",
      "released_amount" => $releaseAmount,
      "promo_subsidy" => $driverVoucherSubsidy,
      "release" => $release,
      "subsidy" => $subsidy,
      "fee_mutation" => $feeMutation,
      "receipt" => $receipt,
      "coin_reward" => $coinReward
    ]);
  }

  $cashSubsidy = ["applied"=>false,"amount"=>0];
  if($driverVoucherSubsidy > 0){
    $cashSubsidy = tp_adjust_driver_wallet($conn, $driver, $driverVoucherSubsidy, "promo_subsidy", "PROMO-".($order["order_id"] ?? $id), "Subsidi voucher Transiva untuk pesanan ".($order["order_id"] ?? $id), "in");
  }

  $balanceForFeeQ = mysqli_query($conn, "SELECT balance FROM users WHERE username='$driver' AND role='driver' LIMIT 1");
  $balanceForFeeRow = mysqli_fetch_assoc($balanceForFeeQ);
  $balanceForFee = toInt($balanceForFeeRow["balance"] ?? 0);
  if($balanceForFee < $totalPotongan){
    throw new Exception("Saldo driver tidak cukup untuk menyelesaikan order");
  }

  $updatedAt = date("Y-m-d H:i:s");

  $updateOrder = mysqli_query($conn, "
    UPDATE orders
    SET
      status='finished',
      updated_at='$updatedAt'
    WHERE id='$id'
    AND status='arrived_delivery'
    LIMIT 1
  ");

  if(!$updateOrder || mysqli_affected_rows($conn) < 1){
    throw new Exception("Order gagal diselesaikan");
  }

  $feeMutation = ["applied"=>false,"amount"=>0];
  if($totalPotongan > 0){
    $feeMutation = tp_adjust_driver_wallet($conn, $driver, $totalPotongan, "app_fee", "FEE-".($order["order_id"] ?? $id), "Biaya layanan pesanan ".($order["order_id"] ?? $id), "out");
  }

  if (!driver_set_busy_by_username($conn, $driver, dd_driver_has_other_active($conn,$driver,'orders',$id))) {
    throw new Exception("Gagal mereset status sibuk driver");
  }

  $getUpdatedUser = mysqli_query($conn, "
    SELECT balance
    FROM users
    WHERE username='$driver'
    AND role='driver'
    LIMIT 1
  ");

  $updatedUser = mysqli_fetch_assoc($getUpdatedUser);
  $saldoSaatIni = toInt($updatedUser["balance"] ?? 0);

  $getUpdatedOrder = mysqli_query($conn, "
    SELECT *
    FROM orders
    WHERE id='$id'
    LIMIT 1
  ");

  $updatedOrder = mysqli_fetch_assoc($getUpdatedOrder);

  $receipt = [
    "smart_waiting" => $waitingState,"waiting_fee"=>$waitingFee,"fare_before_waiting"=>$ongkir-$waitingFee,
    "order" => $updatedOrder,
    "service_type" => $serviceType,
    "delivery_mode" => $foodData["delivery_mode"],
    "delivery_label" => $foodData["delivery_label"],
    "ongkir" => $ongkir,
    "merchant_order" => $merchantOrder,
    "app_fee" => $appFee,
    "merchant_grossup_fee" => $merchantGrossupFee,
    "total_pendapatan" => $totalPendapatan,
    "total_potongan" => $totalPotongan,
    "discount_breakdown_version" => 2,
    "voucher_discount" => vd_order_breakdown($order)["voucher_discount"],
    "point_discount" => vd_order_breakdown($order)["point_discount"],
    "total_discount" => $driverVoucherSubsidy,
    "promo_subsidy" => $driverVoucherSubsidy,
    "customer_contribution" => $foodData["is_food"] ? max(0, intval($order["price"] ?? 0)) : max(0, $ongkir - $driverVoucherSubsidy),
    "driver_gross_protected" => $ongkir,
    "driver_net" => max(0, $ongkir - $totalPotongan),
    "saldo_sebelum" => $saldoSebelum,
    "saldo_saat_ini" => $saldoSaatIni,
    "sisa_saldo" => $saldoSaatIni,
    "items_count" => $foodData["items_count"],
    "is_food" => $foodData["is_food"]
  ];

  saveDriverReceipt($conn, $id, $driver, $receipt);

  if(!mysqli_commit($conn))throw new RuntimeException('Komit penyelesaian belum terkonfirmasi.');
  require_once __DIR__.'/referral_cash_helper.php';rf_process_safe($conn,(int)($order['user_id']??$order['customer_id']??0));
  $coinReward=transiva_coin_award_safe($conn,$order);
  try{rm_sync($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('MISSION_POST_COMMIT_DEFERRED '.$e->getMessage());}
    try{ri_point_sync_from_sources($conn,(int)($order['user_id']??0));}catch(Throwable $e){error_log('POINT_SYNC_NON_FATAL finish order='.(string)($order['order_id']??$id).' error='.$e->getMessage());}

  sendRes(true, "Order selesai", [
    "status" => "finished",
    "smart_waiting" => $waitingState,"waiting_fee"=>$waitingFee,"fare_before_waiting"=>$ongkir-$waitingFee,
    "order" => $updatedOrder,
    "fee" => $totalPotongan,
    "receipt" => $receipt,
    "coin_reward" => $coinReward
  ]);

}catch(Throwable $e){

  mysqli_rollback($conn);

  sendRes(false, $e->getMessage());

}

?>