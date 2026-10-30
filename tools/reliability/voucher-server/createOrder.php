<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
require_once __DIR__.'/config.php';
require_once __DIR__.'/native_auth.php';
require_once __DIR__.'/order_reliability_helper.php';
require_once __DIR__.'/order_notification_outbox.php';
require_once __DIR__.'/transiva_pay_helper.php';
require_once __DIR__.'/ride_voucher_helper.php';
require_once __DIR__.'/loyalty_helper.php';
require_once __DIR__.'/customer_benefit_helper.php';
require_once __DIR__.'/customer_coin_helper.php';
require_once __DIR__.'/send_fcm.php';
require_once __DIR__.'/weather_pricing_helper.php';
require_once __DIR__.'/driver_dispatch_helper.php';
require_once __DIR__.'/transiva_regional_pricing.php';
require_once __DIR__.'/ride_ecosystem_helper.php';
require_once __DIR__.'/split_bill_helper.php';
require_once __DIR__.'/transiva_server_route.php';

$orderCommitted=false;$orderTransaction=false;$orderResponse=null;
function order_commit_response(array $payload): void {
    global $conn,$userId,$attemptKey,$requestHash,$orderCommitted,$orderTransaction,$orderResponse;
    order_attempt_store($conn,$userId,$attemptKey,$requestHash,$payload);
    if(!mysqli_commit($conn))throw new RuntimeException('Gagal menyimpan transaksi.');
    $orderCommitted=true;$orderTransaction=false;$orderResponse=$payload;
}
function jsonOut($success,$message,$extra=[]){
    global $orderTransaction;
    $payload=array_merge(['success'=>$success,'message'=>$message],$extra);
    if($success && $orderTransaction)order_commit_response($payload);
    echo json_encode($payload,JSON_UNESCAPED_UNICODE);exit;
}
function cleanText($v){return trim((string)($v??''));}
function validCoordinate($lat,$lng){return is_numeric($lat)&&is_numeric($lng)&&$lat>=-90&&$lat<=90&&$lng>=-180&&$lng<=180&&floatval($lat)!=0&&floatval($lng)!=0;}
function haversineKm($lat1,$lng1,$lat2,$lng2){$e=6371;$a=deg2rad($lat2-$lat1);$b=deg2rad($lng2-$lng1);$c=sin($a/2)**2+cos(deg2rad($lat1))*cos(deg2rad($lat2))*sin($b/2)**2;return $e*(2*atan2(sqrt($c),sqrt(1-$c)));}
function normalizeServiceType($orderTypeRaw,$driverTypeRaw){$a=strtolower(cleanText($orderTypeRaw));$b=strtolower(cleanText($driverTypeRaw));if(in_array($a,['transcar','car','mobil'],true)||$b==='car')return ['order_type'=>'Transcar','driver_type'=>'car','pricing_type'=>'Transcar','voucher_service'=>'TransCar'];if(in_array($a,['transshop','shop','transmart','mart'],true))return ['order_type'=>'TransShop','driver_type'=>'bike','pricing_type'=>'Transbike','voucher_service'=>'TransRide'];return ['order_type'=>'Transbike','driver_type'=>'bike','pricing_type'=>'Transbike','voucher_service'=>'TransRide'];}
function normalizePayment($v){$v=strtolower(cleanText($v));return in_array($v,['balance','saldo','wallet','transpay','transiva_pay','transivapay'],true)?'balance':'cash';}
function calculatePriceFromDb(mysqli $conn,string $serviceType,float $distanceKm,float $pickupLat=0,float $pickupLng=0): int {$r=trp_get_rule($conn,$serviceType,$pickupLat,$pickupLng);return trp_calculate($r,$distanceKm);}

try{
  $auth=native_require_user($conn);
  if(strtolower((string)($auth['role']??''))!=='customer')jsonOut(false,'Endpoint ini khusus customer');
  $data=native_input();
  mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
  $userId=intval($auth['id']??0);
  if(isset($data['user_id']) && (int)$data['user_id']!==$userId){http_response_code(403);jsonOut(false,'Akun berubah. Login kembali.',['code'=>'ORDER_ACCOUNT_MISMATCH']);}
  $attemptKey=trim((string)($_SERVER['HTTP_IDEMPOTENCY_KEY']??$_SERVER['HTTP_X_IDEMPOTENCY_KEY']??$data['id']??''));
  if($attemptKey==='' || strlen($attemptKey)>120 || !preg_match('/^[A-Za-z0-9._:-]+$/D',$attemptKey)){http_response_code(422);jsonOut(false,'ID percobaan order wajib valid. Perbarui aplikasi.',['code'=>'ORDER_ATTEMPT_REQUIRED']);}
  order_attempt_lock($conn,$userId,$attemptKey);
  $requestHash=order_request_hash($data);
  order_require_transactional($conn,['users','orders','wallet_transactions','order_attempts','customer_coin_ledger','transiva_point_ledger','order_notification_outbox','ride_server_routes','ride_feature_meta','ride_waypoints']);
  $replay=order_attempt_read($conn,$userId,$attemptKey,$requestHash);if($replay){echo json_encode($replay,JSON_UNESCAPED_UNICODE);exit;}
  $orderId=cleanText($data['id']??'');if($orderId==='')$orderId='ORD-'.substr(hash('sha256',$userId.':'.$attemptKey),0,32);
  if(strlen($orderId)>80 || !preg_match('/^[A-Za-z0-9._:-]+$/D',$orderId)){http_response_code(422);jsonOut(false,'Order ID tidak valid.');}
  $orderLock='order:'.substr(hash('sha256',$orderId),0,50);
  $lock=mysqli_prepare($conn,'SELECT GET_LOCK(?,10) AS acquired');mysqli_stmt_bind_param($lock,'s',$orderLock);mysqli_stmt_execute($lock);$locked=mysqli_fetch_assoc(mysqli_stmt_get_result($lock));mysqli_stmt_close($lock);
  if((int)($locked['acquired']??0)!==1){http_response_code(503);jsonOut(false,'Order sedang diproses. Coba ulang dengan permintaan yang sama.');}
  register_shutdown_function(function()use($conn,$orderLock){try{$st=mysqli_prepare($conn,'SELECT RELEASE_LOCK(?)');mysqli_stmt_bind_param($st,'s',$orderLock);mysqli_stmt_execute($st);mysqli_stmt_close($st);}catch(Throwable $ignored){}});
  $type=normalizeServiceType($data['order_type']??'TransRide',$data['driver_type']??'bike');
  $orderType=$type['order_type'];$driverType=$type['driver_type'];$pricingType=$type['pricing_type'];$voucherService=$type['voucher_service'];
  $pickupLat=floatval($data['pickup']['latitude']??0);$pickupLng=floatval($data['pickup']['longitude']??0);$pickupAddress=cleanText($data['pickup']['address']??'Lokasi Jemput');
  $deliveryLat=floatval($data['delivery']['latitude']??0);$deliveryLng=floatval($data['delivery']['longitude']??0);$deliveryAddress=cleanText($data['delivery']['address']??'Lokasi Tujuan');
  if(!validCoordinate($pickupLat,$pickupLng)||!validCoordinate($deliveryLat,$deliveryLng))jsonOut(false,'Lokasi pickup atau tujuan tidak valid');
  $ecosystem=eco_extract($data);
  $points=[['latitude'=>$pickupLat,'longitude'=>$pickupLng]];foreach($ecosystem['waypoints'] as $w)$points[]=$w;$points[]=['latitude'=>$deliveryLat,'longitude'=>$deliveryLng];
  $serverRoute=tsr_route($points);$distanceKm=tsr_distance($serverRoute,0,160000);
  $durationMinutes=max(1,(int)ceil($serverRoute['routes'][0]['duration']/60));$baseOriginalPrice=calculatePriceFromDb($conn,$pricingType,$distanceKm,$pickupLat,$pickupLng);$weatherRule=transiva_weather_rule($conn,$pricingType);$originalPrice=transiva_apply_weather_price($conn,$pricingType,$baseOriginalPrice);$rainSurcharge=$weatherRule['active']?$weatherRule['surcharge']:0;
  $paymentMethod=normalizePayment($data['payment_method']??'cash');$voucherCode=cleanText($data['voucher_code']??'');$splitSessionKey=cleanText($data['split_session_key']??'');$splitRequired=!empty($data['split_bill_required'])||((int)($ecosystem['group_size']??1)>1);if($splitSessionKey!==''&&$paymentMethod!=='balance')jsonOut(false,'Split Bill hanya tersedia dengan Transiva Pay');if($splitRequired&&$splitSessionKey==='')jsonOut(false,'Sesi Split Bill hilang. Buka kembali Group/Split Bill dan pastikan semua peserta sudah terhubung. Pembayaran penuh tidak akan dibebankan ke pemesan.');
  $priceMode=strtolower(cleanText($data['price_mode']??'standard')); if(!in_array($priceMode,['standard','hemat'],true))$priceMode='standard';
  $scheduledAt=cleanText($data['scheduled_at']??''); $scheduledTs=$scheduledAt!==''?strtotime($scheduledAt):false; $isScheduled=$scheduledTs!==false && $scheduledTs>(time()+300); $scheduledAtDb=$isScheduled?date('Y-m-d H:i:s',$scheduledTs):null;
  if($isScheduled && $paymentMethod==='balance') jsonOut(false,'Schedule Ride sementara menggunakan pembayaran Tunai untuk mencegah saldo tertahan terlalu lama');
  $familyMemberId=intval($data['family_member_id']??0); if($familyMemberId>0){$fm=mysqli_prepare($conn,'SELECT id FROM customer_family_members WHERE id=? AND customer_id=? AND is_active=1 LIMIT 1');mysqli_stmt_bind_param($fm,'ii',$familyMemberId,$userId);mysqli_stmt_execute($fm);if(!mysqli_fetch_assoc(mysqli_stmt_get_result($fm)))jsonOut(false,'Anggota Transiva Family tidak valid');}
  $priceGuaranteed=1;
  $userLat=floatval($data['userLocation']['latitude']??$pickupLat);$userLng=floatval($data['userLocation']['longitude']??$pickupLng);if(!validCoordinate($userLat,$userLng)){$userLat=$pickupLat;$userLng=$pickupLng;}
  $noteText=cleanText($data['note']??'');
  if(in_array($voucherService,['TransRide','TransCar'],true) && $noteText==='') jsonOut(false,'Catatan untuk driver wajib diisi sebelum membuat order TransRide atau TransCar.');
  $roomId='room_'.$orderId;$status=$isScheduled?'scheduled':'pending';$driver='';$driverLat='';$driverLng='';$createdAt=date('Y-m-d H:i:s');
  $offeredDriver=null;$offeredDriverToken='';$offerExpiredAt=null;$offerActionToken='';$minBalance=$driverType==='car'?2000:1000;
  $dispatchLock=false;
  tsr_bootstrap($conn);eco_bootstrap($conn);
  mysqli_begin_transaction($conn);$orderTransaction=true;
  $u=mysqli_prepare($conn,'SELECT id,balance FROM users WHERE id=? AND role=\'customer\' LIMIT 1 FOR UPDATE');mysqli_stmt_bind_param($u,'i',$userId);mysqli_stmt_execute($u);$user=mysqli_fetch_assoc(mysqli_stmt_get_result($u));if(!$user)throw new Exception('Customer tidak ditemukan');
  $replay=order_attempt_read($conn,$userId,$attemptKey,$requestHash);if($replay){mysqli_rollback($conn);$orderTransaction=false;echo json_encode($replay,JSON_UNESCAPED_UNICODE);exit;}
$check=mysqli_prepare($conn,'SELECT id FROM orders WHERE order_id=? LIMIT 1');mysqli_stmt_bind_param($check,'s',$orderId);mysqli_stmt_execute($check);if(mysqli_num_rows(mysqli_stmt_get_result($check))>0)throw new Exception('Order ID sudah digunakan');
  $loyaltyVoucher=ly_apply_voucher($conn,$userId,$voucherCode,$originalPrice,$originalPrice,true); if($loyaltyVoucher){$voucher=['voucher_id'=>0,'promo_id'=>0,'source'=>'loyalty','code'=>$loyaltyVoucher['code'],'discount'=>$loyaltyVoucher['discount'],'final_price'=>$originalPrice-$loyaltyVoucher['discount'],'title'=>$loyaltyVoucher['title']];}else{$voucher=rv_apply($conn,$userId,$voucherCode,$originalPrice,$voucherService,true);} $price=intval($voucher['final_price']);$voucherDiscount=intval($voucher['discount']);$discount=$voucherDiscount; $hematDiscount=0;$coinRedeem=null; if($priceMode==='hemat' && $price>0){ $coinRedeem=coin_redeem($conn,$userId,$orderId,$price); $hematDiscount=(int)$coinRedeem['discount']; $price=max(0,$price-$hematDiscount); $discount+=$hematDiscount; } $balanceBefore=intval($user['balance']);$balanceAfter=$balanceBefore;
  $paymentStatus=$paymentMethod==='balance'?'paid_held':'unpaid';$escrowAmount=$paymentMethod==='balance'?$price:0;$settlement=$paymentMethod==='balance'?'held':'none';
  $splitPayments=[];
  if($paymentMethod==='balance'){
    if($splitSessionKey!==''){
      $splitPayments=sb_apply_payment($conn,$splitSessionKey,$orderId,$price,$userId,'ride_split_payment');
      $fresh=mysqli_prepare($conn,'SELECT balance FROM users WHERE id=? LIMIT 1');mysqli_stmt_bind_param($fresh,'i',$userId);mysqli_stmt_execute($fresh);$fr=mysqli_fetch_assoc(mysqli_stmt_get_result($fresh));mysqli_stmt_close($fresh);$balanceAfter=(int)($fr['balance']??$balanceBefore);
    }else{
      if($balanceBefore<$price)throw new Exception('Saldo Transiva Pay tidak cukup');
      $balanceAfter=$balanceBefore-$price;
      if($price>0){
      $debit=mysqli_prepare($conn,'UPDATE users SET balance=balance-? WHERE id=? AND balance>=? LIMIT 1');mysqli_stmt_bind_param($debit,'iii',$price,$userId,$price);mysqli_stmt_execute($debit);if(mysqli_stmt_affected_rows($debit)<1)throw new Exception('Gagal memotong saldo customer');
      if(!tp_add_wallet_log($conn,$userId,'ride_payment',$price,$balanceBefore,$balanceAfter,'PAY-'.$orderId,'Pembayaran '.$orderType.' '.$orderId))throw new Exception('Gagal mencatat transaksi saldo');
      }
    }
  }
  $note=json_encode(['text'=>$noteText,'type'=>($orderType==='TransShop'?'shop':'ride'),'payment_method'=>$paymentMethod,'payment_status'=>$paymentStatus,'escrow_amount'=>$escrowAmount,'discount_breakdown_version'=>2,'voucher_code'=>$voucher['code'],'voucher_title'=>$voucher['title'],'voucher_discount'=>$voucherDiscount,'point_discount'=>$hematDiscount,'hemat_discount'=>$hematDiscount,'total_discount'=>$discount,'original_price'=>$originalPrice,'base_original_price'=>$baseOriginalPrice,'rain_surcharge'=>$rainSurcharge,'rain_active'=>$weatherRule['active']],JSON_UNESCAPED_UNICODE);
  $stmt=mysqli_prepare($conn,"INSERT INTO orders(user_id,order_id,order_type,driver_type,pickup_lat,pickup_lng,pickup_address,delivery_lat,delivery_lng,delivery_address,distance_km,duration_minutes,price,user_lat,user_lng,note,status,driver,driver_lat,driver_lng,room_id,created_at,scheduled_at,offered_driver,offer_expired_at,offer_action_token,payment_method,payment_status,escrow_amount,driver_settlement_status,voucher_code,voucher_discount,original_price,price_mode,price_guaranteed,family_member_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
  mysqli_stmt_bind_param($stmt,'isssddsddsdiiddsssssssssssssissiisii',$userId,$orderId,$orderType,$driverType,$pickupLat,$pickupLng,$pickupAddress,$deliveryLat,$deliveryLng,$deliveryAddress,$distanceKm,$durationMinutes,$price,$userLat,$userLng,$note,$status,$driver,$driverLat,$driverLng,$roomId,$createdAt,$scheduledAtDb,$offeredDriver,$offerExpiredAt,$offerActionToken,$paymentMethod,$paymentStatus,$escrowAmount,$settlement,$voucher['code'],$voucherDiscount,$originalPrice,$priceMode,$priceGuaranteed,$familyMemberId);
  if(!mysqli_stmt_execute($stmt))throw new Exception('Gagal menyimpan order: '.mysqli_stmt_error($stmt));
  $orderDbId=mysqli_insert_id($conn);
  eco_persist($conn,$orderId,$ecosystem,false);tsr_store($conn,$orderDbId,$serverRoute);
  if(!empty($loyaltyVoucher))ly_mark_used($conn,$loyaltyVoucher,$orderId);else rv_record_usage($conn,$voucher,$userId,$orderId);
  // Voucher dan poin dicatat dalam transaksi yang sama, termasuk Schedule Ride.
  if($isScheduled){ jsonOut(true,'Schedule Ride berhasil dibuat',['id'=>$orderDbId,'order_id'=>$orderId,'status'=>'scheduled','scheduled_at'=>date('Y-m-d H:i:s',$scheduledTs),'price'=>$price,'discount_breakdown_version'=>2,'voucher_code'=>$voucher['code'],'voucher_discount'=>$voucherDiscount,'point_discount'=>$hematDiscount,'total_discount'=>$discount,'price_mode'=>$priceMode,'price_guaranteed'=>true,'split_amount_each'=>$ecosystem['group_size']>1?(int)ceil($price/$ecosystem['group_size']):$price,'ecosystem'=>$ecosystem]); }
  // Batch dispatch cluster: maksimal 3 driver menerima tawaran paralel per batch.
  $lockQ=mysqli_query($conn,"SELECT GET_LOCK('transiva_dispatch_select_v2',0) AS locked");
  $lockR=$lockQ?mysqli_fetch_assoc($lockQ):null;
  $dispatchLock=((int)($lockR['locked']??0)===1);
  $batchResult=['assigned'=>false,'offers'=>[]];$nearest=null;
  if($dispatchLock){
    $freshOrderQ=mysqli_query($conn,"SELECT * FROM orders WHERE id=$orderDbId LIMIT 1");
    $freshOrder=$freshOrderQ?mysqli_fetch_assoc($freshOrderQ):null;
    if($freshOrder)$batchResult=dd_dispatch_batch_for_order($conn,'orders',$freshOrder,15,20,3);
    if(!empty($batchResult['offers'][0]))$nearest=$batchResult['offers'][0];
  }
  if(!empty($batchResult['offers'][0])){
    $offeredDriver=cleanText($batchResult['offers'][0]['username']??'');
    $offeredDriverToken=cleanText($batchResult['offers'][0]['fcm_token']??'');
    $offerExpiredAt=cleanText($batchResult['expires_at']??'');
    $offerActionToken=cleanText($batchResult['offers'][0]['action_token']??'');
  }
  $orderResponse=array_merge(['success'=>true,'message'=>'Order berhasil disimpan'],['id'=>$orderDbId,'order_id'=>$orderId,'order_type'=>$orderType,'driver_type'=>$driverType,'room_id'=>$roomId,'distance_km'=>$distanceKm,'duration_minutes'=>$durationMinutes,'original_price'=>$originalPrice,'base_original_price'=>$baseOriginalPrice,'rain_surcharge'=>$rainSurcharge,'rain_active'=>$weatherRule['active'],'voucher_code'=>$voucher['code'],'voucher_title'=>$voucher['title'],'discount_breakdown_version'=>2,'voucher_discount'=>$voucherDiscount,'point_discount'=>$hematDiscount,'total_discount'=>$discount,'price'=>$price,'payment_method'=>$paymentMethod,'payment_label'=>$paymentMethod==='balance'?'Transiva Pay':'Tunai','payment_status'=>$paymentStatus,'escrow_amount'=>$escrowAmount,'balance'=>$balanceAfter,'offered_driver'=>$offeredDriver,'offer_expired_at'=>$offerExpiredAt,'notification_queued'=>true,'fcm_sent'=>false,'fcm_sent_count'=>0,'dispatch_batch_size'=>count($batchResult['offers']??[]),'dispatch_wave'=>(int)($batchResult['dispatch_wave']??1),'dispatch_cluster'=>$batchResult['pickup_cluster_name']??'','offline_nudge_sent'=>0,'price_mode'=>$priceMode,'price_guaranteed'=>true,'hemat_discount'=>$hematDiscount,'hemat_tier'=>$hematStats['tier']??cb_tier($conn,$userId),'hemat_limit'=>$hematStats['limit']??cb_hemat_limit_for_tier(cb_tier($conn,$userId),$conn),'hemat_remaining'=>$priceMode==='hemat'?max(0,(int)($hematStats['remaining']??1)-1):(int)(cb_hemat_stats($conn,$userId,$voucherService)['remaining']??0),'family_member_id'=>$familyMemberId,'split_session_key'=>$splitSessionKey,'split_payments'=>$splitPayments,'split_amount_each'=>$ecosystem['group_size']>1?(int)ceil($price/$ecosystem['group_size']):$price,'ecosystem'=>$ecosystem]);
  // Queue notifications atomically with the order; CLI worker sends them independently.
  foreach(($batchResult['offers']??[]) as $offerRow){
    $driverToken=cleanText($offerRow['fcm_token']??''); // Persist delivery intent even while token registration recovers.
    $driverName=cleanText($offerRow['username']??'');$driverActionToken=cleanText($offerRow['action_token']??'');
    order_queue_notification($conn,'ride:'.$orderId.':'.$driverName,'fcm',['token'=>$driverToken,'title'=>'Order Baru Transiva','body'=>'Ada order '.$orderType.'. Tarif Rp '.number_format($price,0,',','.'),'data'=>[
      'app'=>'transiva','type'=>'transiva_order','event'=>'new_order','screen'=>'driver_order','order_db_id'=>(string)$orderDbId,'order_id'=>$orderId,
      'order_type'=>$orderType,'driver_type'=>$driverType,'room_id'=>$roomId,'pickup_address'=>$pickupAddress,'delivery_address'=>$deliveryAddress,
      'price'=>(string)$price,'payment_method'=>$paymentMethod,'voucher_discount'=>(string)$voucherDiscount,'point_discount'=>(string)$hematDiscount,'total_discount'=>(string)$discount,'distance_km'=>(string)$distanceKm,
      'duration_minutes'=>(string)$durationMinutes,'offered_driver'=>$driverName,'offer_expired_at'=>(string)($offerRow['expires_at']??$offerExpiredAt),
      'dispatch_wave'=>(string)($offerRow['dispatch_wave']??$batchResult['dispatch_wave']??1),'batch_no'=>(string)($offerRow['batch_no']??$batchResult['batch_no']??1),
      'has_action'=>'1','action_accept'=>'driver_accept','action_reject'=>'driver_reject','action_endpoint'=>'https://transiva.my.id/server/notification_action.php',
      'action_token'=>$driverActionToken,'channel_id'=>'transiva_order_channel','click_action'=>'OPEN_TRANSIVA'
    ]]);
  }
  if(!$nearest && isset($lockR) && (int)($lockR['locked']??0)===1 || is_array($nearest) && !empty($nearest['busy_fallback'])) {
    order_queue_notification($conn,'ride:'.$orderId.':offline','offline_nudge',['id'=>$orderDbId,'driver_type'=>$driverType,'latitude'=>$pickupLat,'longitude'=>$pickupLng,'address'=>$pickupAddress,'order_type'=>$orderType,'reason'=>$nearest?'all_online_busy':'no_available_driver']);
  }
  order_commit_response($orderResponse);
  if($dispatchLock)mysqli_query($conn,"SELECT RELEASE_LOCK('transiva_dispatch_select_v2')");$dispatchLock=false;
  echo json_encode($orderResponse,JSON_UNESCAPED_UNICODE);exit;
}catch(Throwable $e){
  if($orderCommitted && is_array($orderResponse)){error_log('ORDER_POST_COMMIT_NON_FATAL '.$e->getMessage());echo json_encode($orderResponse,JSON_UNESCAPED_UNICODE);exit;}
  if($orderTransaction){try{mysqli_rollback($conn);}catch(Throwable $ignored){}}
  if(!empty($dispatchLock)){try{mysqli_query($conn,"SELECT RELEASE_LOCK('transiva_dispatch_select_v2')");}catch(Throwable $ignored){}}
  error_log('CREATE RIDE ORDER: '.$e->getMessage());
  if($e instanceof mysqli_sql_exception){http_response_code(503);jsonOut(false,'Order belum dapat diproses. Coba ulang dengan permintaan yang sama.',['code'=>'ORDER_STORAGE_UNAVAILABLE']);}
  jsonOut(false,$e->getMessage());
}
