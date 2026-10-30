<?php

ob_start();

ini_set("display_errors", "0");
ini_set("display_startup_errors", "0");
error_reporting(E_ALL);

header("Content-Type: application/json; charset=utf-8");
header("X-Content-Type-Options: nosniff");
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");

include "config.php";
require_once __DIR__."/transiva_server_route.php";
require_once __DIR__."/transiva_regional_pricing.php";
require_once __DIR__ . "/p0_security_helper.php";
$auth = p0_require_customer($conn);
p0_rate_limit($conn, "create_food_order", "u:".(int)$auth["id"], 8, 60);

include "transiva_pay_helper.php";
require_once __DIR__."/order_reliability_helper.php";
require_once __DIR__."/order_notification_outbox.php";
$foodCommitted=false;$foodResponse=null;$foodCommitStarted=false;$foodAttemptLocked=false;$foodReceiptChecked=false;
require_once "send_fcm.php";
require_once __DIR__ . "/merchant_p1_helper.php";
require_once __DIR__ . "/merchant_p3_helper.php";
require_once __DIR__ . "/merchant_order_index_helper.php";
require_once __DIR__ . '/loyalty_helper.php';
require_once __DIR__ . '/ride_voucher_helper.php';
require_once __DIR__ . '/customer_benefit_helper.php';
require_once __DIR__ . '/customer_coin_helper.php';
require_once __DIR__ . '/split_bill_helper.php';

$data = json_decode(file_get_contents("php://input"), true);

function out($success, $message, $extra = []){
  global $conn,$transactionStarted,$foodCommitStarted;
  if(!$success && empty($foodCommitStarted) && !array_key_exists("order_created",$extra)) $extra["order_created"]=false;
  if(!$success && !empty($transactionStarted)){mysqli_rollback($conn);$transactionStarted=false;}
  while(ob_get_level() > 0){
    ob_end_clean();
  }

  echo json_encode(array_merge([
    "success" => $success,
    "message" => $message
  ], $extra), JSON_UNESCAPED_UNICODE);
  exit;
}

function clean($conn, $value){
  return mysqli_real_escape_string($conn, trim((string)($value ?? "")));
}

function validCoordinate($lat, $lng){
  return (
    is_numeric($lat) &&
    is_numeric($lng) &&
    $lat >= -90 &&
    $lat <= 90 &&
    $lng >= -180 &&
    $lng <= 180 &&
    floatval($lat) != 0 &&
    floatval($lng) != 0
  );
}

function distanceKmServer($lat1,$lng1,$lat2,$lng2){
  try{return tsr_distance(tsr_route([['latitude'=>$lat1,'longitude'=>$lng1],['latitude'=>$lat2,'longitude'=>$lng2]]),0,160000);}
  catch(TransivaRouteException $e){http_response_code($e->getCode());out(false,$e->getMessage());}
}

function roundUp500($value){
  return (int)(ceil($value / 500) * 500);
}

function normalizeDeliveryMode($mode){
  $mode = strtolower(trim((string)$mode));

  if(in_array($mode, ["hemat", "economy", "ekonomi", "murah"], true)){
    return "hemat";
  }

  return "standard";
}

function normalizePaymentMethod($method){
  $method = strtolower(trim((string)$method));

  if(in_array($method, ["balance", "saldo", "wallet", "transpay", "transiva_pay", "transivapay"], true)){
    return "balance";
  }

  return "cash";
}

function walletTableExists($conn){
  $q = mysqli_query($conn, "SHOW TABLES LIKE 'wallet_transactions'");
  return $q && mysqli_num_rows($q) > 0;
}

function calculateStandardOngkirFromDb($conn, $distanceKm, $pickupLat=0, $pickupLng=0){
  return trp_calculate(trp_get_rule($conn,'Transbike',(float)$pickupLat,(float)$pickupLng),(float)$distanceKm);
}


function calculateOngkirFromDb($conn, $distanceKm, $deliveryMode = "standard", $pickupLat=0, $pickupLng=0){
  // Mode Hemat tidak mengubah ongkir dasar. Coin dipotong satu kali dari total checkout.
  return calculateStandardOngkirFromDb($conn, $distanceKm, $pickupLat, $pickupLng);
}

function getRestaurantFromDb($conn, $restaurantId){
  $restaurantId = intval($restaurantId);

  $q = mysqli_query($conn, "
    SELECT id, name, latitude, longitude, is_open
    FROM food_restaurants
    WHERE id='$restaurantId'
    LIMIT 1
  ");

  if(!$q){
    out(false, "Gagal membaca data restoran");
  }

  $restaurant = mysqli_fetch_assoc($q);

  if(!$restaurant){
    out(false, "Restoran tidak ditemukan");
  }

  return $restaurant;
}

function getUserDeliveryFromDb($conn, $userId){
  $userId = intval($userId);

  $q = mysqli_query($conn, "
    SELECT id, delivery_address, delivery_lat, delivery_lng
    FROM users
    WHERE id='$userId'
    LIMIT 1
  ");

  if(!$q){
    out(false, "Gagal membaca alamat customer");
  }

  $user = mysqli_fetch_assoc($q);

  if(!$user){
    out(false, "User tidak ditemukan");
  }

  return $user;
}

function calculateFoodTotalFromDb($conn, $restaurantId, $items){

  if(!is_array($items) || count($items) <= 0){
    out(false, "Item makanan kosong");
  }

  $restaurantId = intval($restaurantId);
  $safeItems = [];
  $foodTotal = 0;
  $requestedByMenu = [];
  $grossupRules = merchant_p3_fee_rules($conn);

  foreach($items as $item){

    $menuId = intval($item["id"] ?? 0);
    $qty = intval($item["qty"] ?? 0);

    if($menuId <= 0 || $qty <= 0){
      continue;
    }

    if($qty > 99){
      $qty = 99;
    }

    $q = mysqli_query($conn, "
      SELECT id, restaurant_id, name, price, category
      FROM food_menus
      WHERE id='$menuId'
      AND restaurant_id='$restaurantId'
      AND is_active=1
      LIMIT 1
    ");

    if(!$q){
      out(false, "Gagal membaca menu makanan");
    }

    $menu = mysqli_fetch_assoc($q);

    if(!$menu){
      out(false, "Ada menu yang tidak tersedia atau bukan milik restoran ini");
    }

    $meta = merchant_p1_menu_meta($conn, $restaurantId, $menuId);
    $requestedByMenu[$menuId] = intval($requestedByMenu[$menuId] ?? 0) + $qty;
    if(intval($meta["track_stock"] ?? 0) === 1 && intval($meta["stock"] ?? 0) < $requestedByMenu[$menuId]){
      out(false, "Stok " . ($menu["name"] ?? "menu") . " tidak mencukupi");
    }

    $selected = $item["selected_options"] ?? [];
    if(!is_array($selected)) $selected = [];

    $allowed = [];
    $groups = is_array($meta["options"] ?? null) ? $meta["options"] : [];
    foreach($groups as $group){
      if(!is_array($group)) continue;
      $type = strtolower(trim((string)($group["type"] ?? "option")));
      $label = trim((string)($group["label"] ?? $type));
      $groupItems = is_array($group["items"] ?? null) ? $group["items"] : [];
      foreach($groupItems as $option){
        if(!is_array($option)) continue;
        $name = trim((string)($option["name"] ?? ""));
        if($name === "") continue;
        $key = $type . "|" . mb_strtolower($name);
        $allowed[$key] = [
          "type" => $type,
          "label" => $label,
          "name" => $name,
          "price" => max(0, intval($option["price"] ?? 0))
        ];
      }
    }

    $safeSelected = [];
    $optionExtra = 0;
    $variantSeen = [];
    $dedupe = [];

    foreach($selected as $choice){
      if(!is_array($choice)) continue;
      $type = strtolower(trim((string)($choice["type"] ?? "option")));
      $name = trim((string)($choice["name"] ?? ""));
      if($name === "") continue;
      $key = $type . "|" . mb_strtolower($name);

      if(isset($dedupe[$key])) continue;
      if(!isset($allowed[$key])){
        out(false, "Pilihan menu tidak valid untuk " . ($menu["name"] ?? "menu"));
      }
      if($type === "variant"){
        if(isset($variantSeen[$type])) out(false, "Pilih hanya satu varian untuk " . ($menu["name"] ?? "menu"));
        $variantSeen[$type] = true;
      }

      $safe = $allowed[$key];
      $safeSelected[] = $safe;
      $optionExtra += intval($safe["price"]);
      $dedupe[$key] = true;
    }

    $basePrice = intval($menu["price"]);
    [$fallbackOriginalPrice, $fallbackGrossupFee] = merchant_p3_original_price($basePrice, $grossupRules);
    $fullOriginalPrice = isset($meta['original_price']) && (int)$meta['original_price'] > 0 ? (int)$meta['original_price'] : $fallbackOriginalPrice;
    $discountPercent = ((int)($meta['discount_active'] ?? 0) === 1) ? max(0.0,min(90.0,(float)($meta['discount_percent'] ?? 0))) : 0.0;
    $baseOriginalPrice = $discountPercent > 0 ? max(1,(int)round($fullOriginalPrice*(1.0-$discountPercent/100.0))) : $fullOriginalPrice;
    $baseGrossupFee = isset($meta['grossup_fee']) && $meta['grossup_fee'] !== null ? (int)$meta['grossup_fee'] : $fallbackGrossupFee;
    $unitPrice = $basePrice + $optionExtra;
    $subtotal = $unitPrice * $qty;
    $foodTotal += $subtotal;

    $optionNames = [];
    foreach($safeSelected as $safeChoice){
      $choiceName = trim((string)($safeChoice["name"] ?? ""));
      if($choiceName !== "") $optionNames[] = $choiceName;
    }

    $safeItems[] = [
      "id" => intval($menu["id"]),
      "restaurant_id" => intval($menu["restaurant_id"]),
      "name" => $menu["name"],
      "base_price" => $basePrice,
      "original_price" => $fullOriginalPrice,
      "discount_percent" => $discountPercent,
      "discount_amount" => max(0,$fullOriginalPrice-$baseOriginalPrice),
      "merchant_price" => $baseOriginalPrice,
      "grossup_fee" => $baseGrossupFee,
      "option_total" => $optionExtra,
      "price" => $unitPrice,
      "unit_price" => $unitPrice,
      "category" => $menu["category"],
      "options" => implode(", ", $optionNames),
      "selected_options" => $safeSelected,
      "qty" => $qty,
      "subtotal" => $subtotal
    ];
  }

  if($foodTotal <= 0 || count($safeItems) <= 0){
    out(false, "Total makanan tidak valid");
  }

  return [
    "total" => $foodTotal,
    "items" => $safeItems
  ];
}

function sendFoodNotifToMerchant($conn, $restaurantId, $restaurant, $orderDbId, $orderId, $foodTotal, $deliveryFee, $price, $paymentMethod, $paymentStatus){

  $restaurantId = intval($restaurantId);

  $getMerchant = mysqli_query($conn, "
    SELECT id, username, fcm_token
    FROM users
    WHERE role='merchant'
    AND restaurant_id='$restaurantId'
    LIMIT 1
  ");

  if(!$getMerchant || mysqli_num_rows($getMerchant) <= 0){
    return [
      "success" => false,
      "response" => "Merchant restoran tidak ditemukan"
    ];
  }

  $merchant = mysqli_fetch_assoc($getMerchant);
  $token = trim((string)($merchant["fcm_token"] ?? ""));

  $restaurantName = trim((string)($restaurant["name"] ?? "restoran kamu"));

  order_queue_notification($conn,'food:'.$orderId.':merchant','fcm',[
    'token'=>$token,'title'=>'Pesanan Food Baru','body'=>'Ada pesanan baru masuk di '.$restaurantName,'data'=>[
      "app" => "transiva",
      "type" => "merchant_food_order",
      "event" => "new_food_order",
      "target_user_id" => strval($merchant["id"]),
      "target_app_scope" => "merchant",
      "merchant_expires_epoch" => strval(time()+300),
      "screen" => "merchant_orders",
      "order_id" => strval($orderId),
      "order_db_id" => strval($orderDbId),
      "order_type" => "food",
      "restaurant_id" => strval($restaurantId),
      "restaurant_name" => $restaurantName,
      "food_total" => strval($foodTotal),
      "delivery_fee" => strval($deliveryFee),
      "price" => strval($price),
      "payment_method" => strval($paymentMethod),
      "payment_status" => strval($paymentStatus),
      "channel_id" => "transiva_merchant_order_v3",
      "click_action" => "OPEN_TRANSIVA"
    ]
  ]);
  return ['success'=>false,'queued'=>true];
}

try{

  if(!$data){
    out(false, "Data kosong");
  }

  $user_id = (int)$auth["id"];
  mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
  if(isset($data['user_id']) && (int)$data['user_id']!==$user_id){http_response_code(403);out(false,'Akun berubah. Login kembali.');}
  $attemptKey=trim((string)($_SERVER['HTTP_IDEMPOTENCY_KEY']??$_SERVER['HTTP_X_IDEMPOTENCY_KEY']??$data['id']??''));
  if($attemptKey==='' || strlen($attemptKey)>120 || !preg_match('/^[A-Za-z0-9._:-]+$/D',$attemptKey)){http_response_code(422);out(false,'Perbarui aplikasi untuk membuat order dengan aman.');}
  order_attempt_lock($conn,$user_id,$attemptKey);
  $foodAttemptLocked=true;
  $requestHash=hash('sha256','food:'.order_request_hash($data));
  $replay=order_attempt_read($conn,$user_id,$attemptKey,$requestHash);if($replay)out(true,$replay['message'],$replay);
  $foodReceiptChecked=true;
  order_require_transactional($conn,['users','orders','wallet_transactions','order_attempts','customer_coin_ledger','transiva_point_ledger','split_bill_payments','order_notification_outbox','merchant_order_lookup']);

  $restaurant_id = intval($data["restaurant_id"] ?? 0);
  $delivery_mode = normalizeDeliveryMode($data["delivery_mode"] ?? "standard");
  $payment_method = normalizePaymentMethod($data["payment_method"] ?? "cash");
  $split_session_key = trim((string)($data["split_session_key"] ?? ""));
  $split_required = !empty($data["split_bill_required"]);
  if($split_session_key!=="" && $payment_method!=="balance") out(false,"Split Bill hanya tersedia dengan Transiva Pay");
  if($split_required && $split_session_key==="") out(false,"Sesi Split Bill hilang. Pembayaran penuh tidak akan dibebankan ke pemesan. Buka kembali Split Bill.");

  if($user_id <= 0){
    out(false, "User ID kosong");
  }

  if($restaurant_id <= 0){
    out(false, "Restoran kosong");
  }

  $restaurant = getRestaurantFromDb($conn, $restaurant_id);
  $availability = merchant_p1_is_accepting($conn, $restaurant_id, intval($restaurant["is_open"] ?? 1));
  if(!$availability["accepting"]) out(false, $availability["reason"] ?: "Restoran sedang tidak menerima pesanan");
  $user = getUserDeliveryFromDb($conn, $user_id);

  $pickup_lat = floatval($restaurant["latitude"] ?? 0);
  $pickup_lng = floatval($restaurant["longitude"] ?? 0);
  $pickup_address = clean($conn, $restaurant["name"] ?? "Pesanan Makanan");

  $delivery_lat = floatval($user["delivery_lat"] ?? 0);
  $delivery_lng = floatval($user["delivery_lng"] ?? 0);
  $delivery_address = clean($conn, $user["delivery_address"] ?? "");

  if(!validCoordinate($pickup_lat, $pickup_lng)){
    out(false, "Koordinat restoran belum valid");
  }

  if(!validCoordinate($delivery_lat, $delivery_lng)){
    out(false, "Koordinat customer belum valid");
  }

  if($delivery_address === ""){
    out(false, "Alamat delivery belum disimpan");
  }

  $distance_km = distanceKmServer($pickup_lat, $pickup_lng, $delivery_lat, $delivery_lng);

  // Bila customer berada sangat dekat dengan merchant, hasil pembulatan dapat 0.00 km.
  // Itu bukan koordinat invalid; gunakan jarak minimum agar pricing memakai min_price.
  if($distance_km < 0){
    out(false, "Jarak order tidak valid");
  }
  if($distance_km == 0){
    $distance_km = 0.01;
  }

  $cartResult = calculateFoodTotalFromDb($conn, $restaurant_id, $data["items"] ?? []);

  $food_total = intval($cartResult["total"]);
  $items = $cartResult["items"];
  $duration_minutes = 0;

  $standard_delivery_fee = calculateOngkirFromDb($conn, $distance_km, "standard", $pickup_lat, $pickup_lng);
  $coinStats = coin_status($conn,$user_id);
  $hematStats = ["tier"=>$coinStats["tier"],"limit"=>$coinStats["min_redeem_coins"],"used"=>0,"remaining"=>$coinStats["balance"]];
  $hemat_discount = 0;
  // Pada TransFood koin boleh memotong total makanan + ongkir. Hak merchant/driver tetap dilindungi sebagai subsidi Transiva.
  $delivery_fee = $standard_delivery_fee;
  $voucher_code = strtoupper(trim((string)($data["voucher_code"] ?? "")));
  if($voucher_code !== ""){ try { cb_assert_voucher_verified($conn,$user_id,$voucher_code); } catch(Throwable $verifyError) { out(false,$verifyError->getMessage()); } }
  $order_total_before_discount = $food_total + $delivery_fee;
  $loyaltyVoucher = ly_apply_voucher($conn, $user_id, $voucher_code, $order_total_before_discount, $delivery_fee, false);
  $promoVoucher = null;
  if($voucher_code !== "" && !$loyaltyVoucher){
    try {
      $candidate = rv_apply($conn, $user_id, $voucher_code, $order_total_before_discount, 'TransFood', false, $delivery_fee);
      if(!empty($candidate['code'])) $promoVoucher = $candidate;
    } catch(Throwable $voucherError) {
      out(false, $voucherError->getMessage());
    }
  }
  if($voucher_code !== "" && !$loyaltyVoucher && !$promoVoucher){ out(false, "Voucher tidak ditemukan atau tidak berlaku untuk TransFood"); }
  $appliedVoucher = $loyaltyVoucher ?: $promoVoucher;
  $voucher_discount = $appliedVoucher ? intval($appliedVoucher["discount"] ?? 0) : 0;
  $price_before_coin = max(0, $order_total_before_discount - $voucher_discount);
  $order_id = "FOOD-" . date("YmdHis") . "-" . random_int(100000, 999999);
  $transactionStarted = false;
  if(!mysqli_begin_transaction($conn)) out(false, "Server sedang sibuk memulai transaksi order");
  $transactionStarted = true;
  $userLock=mysqli_prepare($conn,'SELECT id FROM users WHERE id=? FOR UPDATE');mysqli_stmt_bind_param($userLock,'i',$user_id);mysqli_stmt_execute($userLock);mysqli_stmt_close($userLock);
  $replay=order_attempt_read($conn,$user_id,$attemptKey,$requestHash);if($replay){mysqli_rollback($conn);$transactionStarted=false;out(true,$replay['message'],$replay);}
  $loyaltyVoucher=ly_apply_voucher($conn,$user_id,$voucher_code,$order_total_before_discount,$delivery_fee,true);
  $promoVoucher=$loyaltyVoucher?null:rv_apply($conn,$user_id,$voucher_code,$order_total_before_discount,'TransFood',true,$delivery_fee);
  $appliedVoucher=$loyaltyVoucher?:$promoVoucher;
  $voucher_discount=(int)($appliedVoucher['discount']??0);
  $price_before_coin=max(0,$order_total_before_discount-$voucher_discount);
  if($delivery_mode === "hemat" && $price_before_coin > 0){
    try { $coinRedeem=coin_redeem($conn,$user_id,$order_id,$price_before_coin); $hemat_discount=(int)$coinRedeem["discount"]; }
    catch(Throwable $benefitError) { mysqli_rollback($conn); $transactionStarted=false; out(false,$benefitError->getMessage()); }
  }
  // Coin/promo adalah subsidi Transiva; nilai customer boleh turun di bawah food_total tanpa mengurangi hak merchant.
  $total_discount = $voucher_discount + $hemat_discount;
  $price = max(0, $price_before_coin - $hemat_discount);

  $payment_status = $payment_method === "balance" ? "paid_held" : "unpaid";
  $escrow_amount = $payment_method === "balance" ? $price : 0;
  $driver_settlement_status = $payment_method === "balance" ? "held" : "none";
  $payment_label = $payment_method === "balance" ? "Transiva Pay" : "Tunai";
  $balance_after_payment = null;

  $order_type = "food";
  $driver_type = "bike";
  $user_lat = $delivery_lat;
  $user_lng = $delivery_lng;
  $status = "pending";
  $driver = "";
  $driver_lat = 0;
  $driver_lng = 0;
  $room_id = "ROOM-" . $order_id;

  $noteData = [
    "type" => "food",
    "restaurant_id" => $restaurant_id,
    "restaurant_name" => $restaurant["name"] ?? "Resto",
    "food_total" => $food_total,
    "delivery_fee" => $delivery_fee,
    "original_delivery_fee" => $standard_delivery_fee,
    "hemat_discount" => $hemat_discount,
    "hemat_tier" => $hematStats["tier"],
    "voucher_code" => $appliedVoucher ? ($appliedVoucher["code"] ?? "") : "",
    "voucher_title" => $appliedVoucher ? ($appliedVoucher["title"] ?? "") : "",
    "discount_breakdown_version" => 2,
    "voucher_discount" => $voucher_discount,
    "point_discount" => $hemat_discount,
    "total_discount" => $total_discount,
    "voucher_sponsor" => $total_discount > 0 ? "TRANSIVA" : "",
    "driver_income_affected" => false,
    "delivery_mode" => $delivery_mode,
    "delivery_label" => $delivery_mode === "hemat" ? "Pengantaran Hemat" : "Pengantaran Standar",
    "payment_method" => $payment_method,
    "split_session_key" => $split_session_key,
    "payment_label" => $payment_label,
    "payment_status" => $payment_status,
    "escrow_amount" => $escrow_amount,
    "driver_settlement_status" => $driver_settlement_status,
    "total" => $price,
    "items" => $items
  ];

  $note = clean($conn, json_encode($noteData, JSON_UNESCAPED_UNICODE));

  // Driver TransFood baru dicari setelah merchant menerima pesanan.
  // Ini mencegah countdown habis saat merchant belum merespons dan menyamakan
  // redispatch dengan TransRide/TransCar melalui assignNextDriver.php.
  $offeredDriver = null;
  $offerExpiredAt = null;

  $offeredDriverSql = $offeredDriver ? "'$offeredDriver'" : "NULL";
  $offerExpiredSql = $offerExpiredAt ? "'$offerExpiredAt'" : "NULL";

  $query = "
  INSERT INTO orders
  (
    user_id,
    order_id,
    order_type,
    driver_type,

    pickup_lat,
    pickup_lng,
    pickup_address,

    delivery_lat,
    delivery_lng,
    delivery_address,

    distance_km,
    duration_minutes,
    price,
    payment_method,
    payment_status,
    escrow_amount,
    driver_settlement_status,

    user_lat,
    user_lng,

    status,
    created_at,

    driver,
    driver_lat,
    driver_lng,

    note,
    room_id,

    offered_driver,
    offer_expired_at
  )
  VALUES
  (
    '$user_id',
    '$order_id',
    '$order_type',
    '$driver_type',

    '$pickup_lat',
    '$pickup_lng',
    '$pickup_address',

    '$delivery_lat',
    '$delivery_lng',
    '$delivery_address',

    '$distance_km',
    '$duration_minutes',
    '$price',
    '$payment_method',
    '$payment_status',
    '$escrow_amount',
    '$driver_settlement_status',

    '$user_lat',
    '$user_lng',

    '$status',
    NOW(),

    '$driver',
    '$driver_lat',
    '$driver_lng',

    '$note',
    '$room_id',

    $offeredDriverSql,
    $offerExpiredSql
  )
  ";

  // V14 BURST SHIELD: cash maupun balance tetap berada di transaksi atomik yang sama dengan debit Coin.
  $currentBalance = 0;

  $splitPayments = [];
  if($payment_method === "balance"){
    if($split_session_key!==""){
      try { $splitPayments=sb_apply_payment($conn,$split_session_key,$order_id,(int)$price,(int)$user_id,'food_split_payment'); }
      catch(Throwable $splitError){ mysqli_rollback($conn); out(false,$splitError->getMessage()); }
      $lockUser=mysqli_query($conn,"SELECT balance FROM users WHERE id='".(int)$user_id."' LIMIT 1");
      $lockedUser=$lockUser?mysqli_fetch_assoc($lockUser):null;$currentBalance=intval($lockedUser["balance"]??0);$balance_after_payment=$currentBalance;
    } else {
      $lockUser = mysqli_query($conn, "SELECT id, balance FROM users WHERE id='$user_id' LIMIT 1 FOR UPDATE");
      if(!$lockUser){mysqli_rollback($conn);out(false, "Gagal memeriksa saldo customer");}
      $lockedUser = mysqli_fetch_assoc($lockUser);if(!$lockedUser){mysqli_rollback($conn);out(false, "User tidak ditemukan");}
      $currentBalance = intval($lockedUser["balance"] ?? 0);if($currentBalance < $price){mysqli_rollback($conn);out(false, "Saldo Transiva Pay tidak cukup");}
      $balance_after_payment = $currentBalance - $price;
      $cutBalance = mysqli_query($conn, "UPDATE users SET balance = balance - $price WHERE id='$user_id' AND balance >= $price LIMIT 1");
      if(!$cutBalance || ($price > 0 && mysqli_affected_rows($conn) <= 0)){mysqli_rollback($conn);out(false, "Gagal memotong saldo Transiva Pay");}
    }
  }

  $insert = mysqli_query($conn, $query);

  if(!$insert){
    if($transactionStarted){
      mysqli_rollback($conn);
    }

    error_log("CREATE FOOD ORDER ERROR: " . mysqli_error($conn));
    out(false, "Gagal membuat pesanan makanan");
  }

  $orderDbId = mysqli_insert_id($conn);

  // V8: simpan fast lookup restaurant -> order agar endpoint Merchant tidak perlu
  // memindai JSON note untuk setiap refresh. Best effort, sumber data tetap orders.
  if (!merchant_order_index_record($conn, $orderDbId, $restaurant_id)) {
    error_log('CREATE_FOOD_ORDER_INDEX_RECORD_FAILED order_db_id=' . $orderDbId . ' restaurant_id=' . $restaurant_id);
  }

  // V14 BURST SHIELD: atomic conditional stock decrement.
  // Dua order paralel tidak dapat sama-sama memakai stok terakhir.
  $qtyByMenu = [];
  foreach($items as $orderedItem){
    $mid = intval($orderedItem["id"] ?? 0);
    $qtyOrdered = intval($orderedItem["qty"] ?? 0);
    if($mid<=0 || $qtyOrdered<=0) continue;
    $qtyByMenu[$mid] = intval($qtyByMenu[$mid] ?? 0) + $qtyOrdered;
  }
  foreach($qtyByMenu as $mid => $qtyOrdered){
    $meta = merchant_p1_menu_meta($conn, $restaurant_id, (int)$mid);
    if(intval($meta["track_stock"] ?? 0)!==1) continue;

    $stockStmt = mysqli_prepare($conn, "UPDATE merchant_menu_meta SET stock=stock-? WHERE menu_id=? AND restaurant_id=? AND track_stock=1 AND stock>=?");
    if(!$stockStmt){ mysqli_rollback($conn); $transactionStarted=false; out(false, "Gagal mengunci stok menu"); }
    mysqli_stmt_bind_param($stockStmt, "iiii", $qtyOrdered, $mid, $restaurant_id, $qtyOrdered);
    mysqli_stmt_execute($stockStmt);
    $changed = mysqli_stmt_affected_rows($stockStmt);
    mysqli_stmt_close($stockStmt);
    if($changed < 1){
      mysqli_rollback($conn); $transactionStarted=false;
      out(false, "Stok salah satu menu baru saja habis. Silakan perbarui keranjang.", ["code"=>"STOCK_RACE_REJECTED", "menu_id"=>(int)$mid]);
    }

    $stockQ = mysqli_query($conn, "SELECT stock FROM merchant_menu_meta WHERE menu_id='".(int)$mid."' AND restaurant_id='".$restaurant_id."' LIMIT 1");
    $stockRow = $stockQ ? mysqli_fetch_assoc($stockQ) : null;
    if((int)($stockRow["stock"] ?? 0) <= 0){
      mysqli_query($conn, "UPDATE food_menus SET is_active=0 WHERE id='".(int)$mid."' AND restaurant_id='".$restaurant_id."' LIMIT 1");
    }
  }

  // Coin sudah didebit atomik oleh coin_redeem().
  if(!empty($loyaltyVoucher)){ ly_mark_used($conn,$loyaltyVoucher,$order_id); } elseif(!empty($promoVoucher)){ rv_record_usage($conn,$promoVoucher,$user_id,$order_id); }

  if($payment_method === "balance" && $price > 0 && $split_session_key==="" && walletTableExists($conn)){

    $reference = clean($conn, $order_id);
    $description = clean($conn, "Saldo ditahan untuk pesanan food " . $order_id);

    $walletInsert = mysqli_query($conn, "
      INSERT INTO wallet_transactions
      (user_id, type, amount, balance_before, balance_after, status, reference, description, created_at)
      VALUES
      ('$user_id', 'payment_hold', '$price', '$currentBalance', '$balance_after_payment', 'success', '$reference', '$description', NOW())
    ");

    if(!$walletInsert){
      mysqli_rollback($conn);
      out(false, "Gagal mencatat riwayat saldo");
    }
  }

  order_queue_notification($conn,'food:'.$order_id.':stats','food_stats',['restaurant_id'=>$restaurant_id,'items'=>$items]);
  $merchantFcmResult = sendFoodNotifToMerchant($conn,$restaurant_id,$restaurant,$orderDbId,$order_id,$food_total,$delivery_fee,$price,$payment_method,$payment_status);
  $foodResponse=array_merge(['success'=>true,'message'=>'Pesanan makanan berhasil dibuat'],[
    "order_id" => $order_id,
    "order_db_id" => $orderDbId,
    "order_type" => $order_type,
    "driver_type" => $driver_type,
    "room_id" => $room_id,
    "food_total" => $food_total,
    "delivery_fee" => $delivery_fee,
    "original_delivery_fee" => $standard_delivery_fee,
    "hemat_discount" => $hemat_discount,
    "hemat_tier" => $hematStats["tier"],
    "hemat_limit" => $hematStats["limit"],
    "hemat_remaining" => $delivery_mode === "hemat" ? max(0,(int)$hematStats["remaining"]-1) : (int)$hematStats["remaining"],
    "voucher_code" => $appliedVoucher ? ($appliedVoucher["code"] ?? "") : "",
    "voucher_title" => $appliedVoucher ? ($appliedVoucher["title"] ?? "") : "",
    "discount_breakdown_version" => 2,
    "voucher_discount" => $voucher_discount,
    "point_discount" => $hemat_discount,
    "total_discount" => $total_discount,
    "voucher_sponsor" => $total_discount > 0 ? "TRANSIVA" : "",
    "driver_income_affected" => false,
    "delivery_mode" => $delivery_mode,
    "delivery_label" => $delivery_mode === "hemat" ? "Pengantaran Hemat" : "Pengantaran Standar",
    "distance_km" => $distance_km,
    "price" => $price,
    "payment_method" => $payment_method,
    "split_session_key" => $split_session_key,
    "split_payments" => $splitPayments,
    "payment_label" => $payment_label,
    "payment_status" => $payment_status,
    "escrow_amount" => $escrow_amount,
    "driver_settlement_status" => $driver_settlement_status,
    "balance" => $balance_after_payment,
    "offered_driver" => $offeredDriver,
    "offer_expired_at" => $offerExpiredAt,
    "channel_id" => "transiva_order_channel",
    "merchant_fcm_sent" => is_array($merchantFcmResult) ? ($merchantFcmResult["success"] ?? false) : false,
    "merchant_fcm_result" => $merchantFcmResult
  ]);
  order_attempt_store($conn,$user_id,$attemptKey,$requestHash,$foodResponse);
  $foodCommitStarted=true;
  if(!mysqli_commit($conn))throw new RuntimeException('Gagal menyimpan transaksi food.');
  $transactionStarted=false;$foodCommitted=true;
  out(true,$foodResponse['message'],$foodResponse);

}catch(Throwable $e){
  if(!empty($transactionStarted)){
    try { mysqli_rollback($conn); } catch(Throwable $rollbackError) { /* Connection may already be gone. */ }
    $transactionStarted=false;
  }
  $reference=strtoupper(bin2hex(random_bytes(4)));
  error_log("CREATE FOOD ORDER FATAL [".$reference."]: ".$e->getMessage());
  if($foodCommitted && is_array($foodResponse)) out(true,$foodResponse['message'],$foodResponse);
  http_response_code(503);
  if($e instanceof DomainException){
    http_response_code(409);
    out(false,'Permintaan berbeda dari pesanan sebelumnya. Periksa Aktivitas terlebih dahulu.',['code'=>'ORDER_ATTEMPT_CONFLICT','order_created'=>null,'reference'=>$reference]);
  }
  if($foodCommitStarted || !$foodAttemptLocked || !$foodReceiptChecked){
    out(false,"Konfirmasi pesanan belum diterima. Periksa Aktivitas atau ulangi pesanan yang sama.",['code'=>'ORDER_OUTCOME_UNKNOWN','order_created'=>null,'reference'=>$reference]);
  }
  $schemaError=str_contains($e->getMessage(),'Migrasi database transaksi') || str_contains($e->getMessage(),'Tabel transaksi tambahan');
  $message=$schemaError ? 'Layanan pesanan memerlukan pembaruan database server. Hubungi admin.' : 'Pesanan makanan belum dibuat karena server mengalami kendala. Coba lagi. Kode: '.$reference;
  out(false,$message,['code'=>$schemaError?'FOOD_SCHEMA_NOT_READY':'FOOD_ORDER_FAILED','reference'=>$reference]);
}
