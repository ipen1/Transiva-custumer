<?php
declare(strict_types=1);

require_once __DIR__.'/schema_runtime_guard.php';
require_once __DIR__.'/order_notification_outbox.php';
require_once __DIR__.'/transiva_cluster_helper.php';
require_once __DIR__.'/driver_offline_nudge_helper.php';

if (!function_exists('dd_clean')) {
function dd_clean($v): string { return trim((string)($v ?? '')); }
function dd_table_exists(mysqli $conn,string $table): bool {
  static $cache=[];
  $key=strtolower($table);
  if(array_key_exists($key,$cache)) return $cache[$key];
  $t=mysqli_real_escape_string($conn,$table);
  $q=transiva_schema_query($conn,"SHOW TABLES LIKE '$t'");
  $ok=$q&&mysqli_num_rows($q)>0;
  if($q) mysqli_free_result($q);
  return $cache[$key]=$ok;
}
function dd_column_exists(mysqli $conn,string $table,string $column): bool {
  static $cache=[];
  $key=strtolower($table.'|'.$column);
  if(array_key_exists($key,$cache)) return $cache[$key];
  $t=mysqli_real_escape_string($conn,$table);$c=mysqli_real_escape_string($conn,$column);
  $q=transiva_schema_query($conn,"SHOW COLUMNS FROM `$t` LIKE '$c'");
  $ok=$q&&mysqli_num_rows($q)>0;
  if($q) mysqli_free_result($q);
  return $cache[$key]=$ok;
}
function dd_normalize_type($v): string { $v=strtolower(dd_clean($v));return in_array($v,['car','mobil','driver_car','transcar'],true)?'car':'bike'; }
function dd_dispatch_wave_for_order(mysqli $conn,string $source,int $orderId): int {
  // Gelombang pencarian berbasis umur order, bukan jumlah offer.
  // 0-29 dtk: cluster asal, 30-59 dtk: cluster tetangga, >=60 dtk: seluruh koridor.
  if(in_array($source,['orders','pickup_orders'],true) && dd_column_exists($conn,$source,'created_at')){
    $q=transiva_schema_query($conn,"SELECT created_at FROM `".$source."` WHERE id=".(int)$orderId." LIMIT 1");
    $r=$q?mysqli_fetch_assoc($q):null;
    $ts=$r?strtotime((string)($r['created_at']??'')):false;
    if($ts!==false){
      $elapsed=max(0,time()-$ts);
      return $elapsed<30?1:($elapsed<60?2:3);
    }
  }
  return tc_offer_wave($conn,$source,$orderId);
}
function dd_type_sql(mysqli $conn,string $type): string {
  if(!dd_column_exists($conn,'driver_profiles','driver_type')) return '1=1';
  return $type==='car'
    ? "LOWER(COALESCE(dp.driver_type,'')) IN ('car','mobil','driver_car','transcar')"
    : "LOWER(COALESCE(dp.driver_type,'')) IN ('bike','motor','motorcycle','driver_bike','driver_motor','transbike','transride')";
}
function dd_haversine_sql(float $lat,float $lng): string { $lat=max(-90,min(90,$lat));$lng=max(-180,min(180,$lng));return "(6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(dp.latitude-$lat)/2),2)+COS(RADIANS($lat))*COS(RADIANS(dp.latitude))*POWER(SIN(RADIANS(dp.longitude-$lng)/2),2))))"; }
function dd_ensure_schema(mysqli $conn): void {
  static $ready=false; if($ready) return;
  $sql="CREATE TABLE IF NOT EXISTS driver_dispatch_offers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_table VARCHAR(32) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    driver_username VARCHAR(100) NOT NULL,
    action_token VARCHAR(128) NOT NULL,
    distance_km DECIMAL(8,3) NOT NULL DEFAULT 0,
    cluster_id INT NOT NULL DEFAULT 0,
    dispatch_wave TINYINT NOT NULL DEFAULT 1,
    batch_no INT NOT NULL DEFAULT 1,
    offered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME NOT NULL,
    outcome VARCHAR(24) NOT NULL DEFAULT 'offered',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_dispatch_offer_token (action_token),
    KEY idx_dispatch_offer_driver (driver_username,outcome,expired_at),
    KEY idx_dispatch_offer_order (source_table,order_id,outcome,expired_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
  if(!transiva_schema_query($conn,$sql)) error_log('DISPATCH BATCH SCHEMA: '.mysqli_error($conn));
  $ready=true;
}


/**
 * Hitung hanya order yang SUDAH diterima/berjalan.
 * Tawaran pending/offered tidak boleh membuat driver dianggap sibuk atau penuh.
 * Dibuat dinamis agar tidak gagal bila struktur tabel pickup berbeda.
 */
function dd_order_load_sql(mysqli $conn): string {
  $parts=[];

  if(dd_table_exists($conn,'orders') && dd_column_exists($conn,'orders','driver') && dd_column_exists($conn,'orders','status')){
    $parts[]="(SELECT COUNT(*) FROM orders ao WHERE ao.driver=u.username AND LOWER(COALESCE(ao.status,'')) IN ('driver_accepted','arrived_pickup','on_delivery','arrived_delivery','accepted','arrived','picked_up','on_trip','in_progress','ongoing','started'))";
  }

  if(dd_table_exists($conn,'pickup_orders') && dd_column_exists($conn,'pickup_orders','status')){
    $driverCols=[];
    if(dd_column_exists($conn,'pickup_orders','driver_username')) $driverCols[]="po.driver_username=u.username";
    if(dd_column_exists($conn,'pickup_orders','driver')) $driverCols[]="po.driver=u.username";
    if($driverCols){
      $parts[]="(SELECT COUNT(*) FROM pickup_orders po WHERE (".implode(' OR ',$driverCols).") AND LOWER(COALESCE(po.status,'')) IN ('taken','driver_accepted','arrived_pickup','on_delivery','arrived_delivery','accepted','arrived','picked_up','on_trip','in_progress','ongoing','started'))";
    }
  }

  return $parts ? '('.implode(' + ',$parts).')' : '0';
}

function dd_pending_offer_sql(mysqli $conn): string {
  dd_ensure_schema($conn);
  if(dd_table_exists($conn,'driver_dispatch_offers')){
    return "EXISTS(SELECT 1 FROM driver_dispatch_offers dbo WHERE dbo.driver_username=u.username AND dbo.outcome='offered' AND dbo.expired_at>NOW())";
  }
  return '0';
}

function dd_candidates(mysqli $conn,string $type,float $pickupLat,float $pickupLng,int $minBalance=0,bool $idleOnly=true,int $maxKm=20,string $capacityMode='available',array $allowedClusterIds=[]): array {
  if(!dd_table_exists($conn,'users')||!dd_table_exists($conn,'driver_profiles')) return [];
  if(!dd_column_exists($conn,'driver_profiles','latitude')||!dd_column_exists($conn,'driver_profiles','longitude')) return [];

  $distance=dd_haversine_sql($pickupLat,$pickupLng);
  $typeSql=dd_type_sql($conn,$type);
  $load=dd_order_load_sql($conn);
  $latDelta=max(0.01,$maxKm/111.0);
  $cosLat=max(0.15,abs(cos(deg2rad($pickupLat))));
  $lngDelta=max(0.01,$maxKm/(111.0*$cosLat));
  $minLat=$pickupLat-$latDelta;$maxLat=$pickupLat+$latDelta;
  $minLng=$pickupLng-$lngDelta;$maxLng=$pickupLng+$lngDelta;
  $where=["LOWER(u.role)='driver'", "dp.is_online=1", "COALESCE(u.balance,0)>=$minBalance", $typeSql,
          "dp.latitude IS NOT NULL", "dp.longitude IS NOT NULL", "dp.latitude<>0", "dp.longitude<>0",
          "dp.latitude BETWEEN $minLat AND $maxLat", "dp.longitude BETWEEN $minLng AND $maxLng"];

  if($idleOnly && dd_column_exists($conn,'driver_profiles','is_busy')) $where[]="COALESCE(dp.is_busy,0)=0";
  if(dd_column_exists($conn,'driver_profiles','last_location_at')) $where[]="(dp.last_location_at IS NULL OR dp.last_location_at>=DATE_SUB(NOW(),INTERVAL 10 MINUTE))";

  $capacityCondition=$capacityMode==='full' ? "$load>=2" : "$load<2";
  $busySelect=dd_column_exists($conn,'driver_profiles','is_busy')?'COALESCE(dp.is_busy,0)':'0';
  $lastOrderSort=dd_column_exists($conn,'driver_profiles','last_order_at')?"COALESCE(dp.last_order_at,'2000-01-01')":"u.id";
  $fcmSelect=dd_column_exists($conn,'users','fcm_token')?'u.fcm_token':"'' AS fcm_token";
  $pendingOffer=dd_pending_offer_sql($conn);

  $sql="SELECT u.id,u.username,$fcmSelect,$busySelect AS is_busy,dp.latitude,dp.longitude,$distance AS distance_km,$load AS active_load,
        COALESCE(hist.recent_offer_count,0) AS recent_offer_count,
        COALESCE(hist.last_offer_at,'2000-01-01') AS last_offer_at
        FROM users u
        INNER JOIN driver_profiles dp ON dp.user_id=u.id
        LEFT JOIN (
          SELECT driver_username,
                 SUM(offered_at>=DATE_SUB(NOW(),INTERVAL 30 MINUTE)) AS recent_offer_count,
                 MAX(offered_at) AS last_offer_at
          FROM driver_dispatch_history
          WHERE offered_at>=DATE_SUB(NOW(),INTERVAL 24 HOUR)
          GROUP BY driver_username
        ) hist ON hist.driver_username=u.username
        WHERE ".implode(' AND ',$where)." AND ($capacityCondition) AND NOT ($pendingOffer)
        HAVING distance_km<=$maxKm
        ORDER BY recent_offer_count ASC,last_offer_at ASC,active_load ASC,$lastOrderSort ASC,distance_km ASC,u.id ASC
        LIMIT 50";

  $q=transiva_schema_query($conn,$sql);
  if(!$q){
    error_log('DISPATCH CANDIDATE QUERY FAILED: '.mysqli_error($conn).' | SQL: '.$sql);
    return [];
  }
  $rows=[];while($r=mysqli_fetch_assoc($q)){
    if($allowedClusterIds){
      $cluster=tc_cluster_for((float)($r['latitude']??0),(float)($r['longitude']??0));
      $r['cluster_id']=(int)$cluster['id'];$r['cluster_name']=(string)$cluster['name'];
      if(!in_array((int)$cluster['id'],$allowedClusterIds,true)) continue;
    }
    $rows[]=$r;
  }
  return $rows;
}

function dd_pick_driver(mysqli $conn,string $source,int $orderId,string $type,float $pickupLat,float $pickupLng,int $minBalance=0,int $maxKm=20): ?array {
  dd_ensure_schema($conn);

  // Dispatch 5 cluster: gelombang 1 hanya cluster pickup, gelombang 2 cluster pickup
  // + tetangga, gelombang 3 seluruh koridor Sumbersari-Toboli. Perluasan terjadi pada detik 30 dan 60.
  $pickupCluster=tc_cluster_for($pickupLat,$pickupLng);
  $wave=dd_dispatch_wave_for_order($conn,$source,$orderId);
  $allowedClusters=tc_cluster_ids_for_wave((int)$pickupCluster['id'],$wave);
  $clusterRadius=$wave<=1?12:($wave===2?24:40);
  $effectiveMaxKm=max($maxKm,$clusterRadius);

  // Semua layanan, termasuk TransSend, memakai antrean dan kapasitas yang sama.
  // Prioritaskan driver idle; bila tidak ada, gunakan driver yang masih memiliki slot kedua.
  $candidates=dd_candidates($conn,$type,$pickupLat,$pickupLng,$minBalance,true,$effectiveMaxKm,'available',$allowedClusters);
  $fallbackBusy=false;
  $capacityLocked=false;

  if(!$candidates){
    $candidates=dd_candidates($conn,$type,$pickupLat,$pickupLng,$minBalance,false,$effectiveMaxKm,'available',$allowedClusters);
    $fallbackBusy=true;
  }

  if(!$candidates){
    $candidates=dd_candidates($conn,$type,$pickupLat,$pickupLng,$minBalance,false,$effectiveMaxKm,'full',$allowedClusters);
    $fallbackBusy=true;
    $capacityLocked=!empty($candidates);
  }
  if(!$candidates)return null;

  $sourceEsc=mysqli_real_escape_string($conn,$source);
  $history=[];$q=transiva_schema_query($conn,"SELECT driver_username FROM driver_dispatch_history WHERE source_table='$sourceEsc' AND order_id=$orderId");while($q&&($r=mysqli_fetch_assoc($q)))$history[strtolower($r['driver_username'])]=true;
  foreach($candidates as $r){if(!isset($history[strtolower($r['username'])])){$r['busy_fallback']=$fallbackBusy;$r['capacity_locked']=$capacityLocked;$r['dispatch_wave']=$wave;$r['pickup_cluster_id']=(int)$pickupCluster['id'];$r['pickup_cluster_name']=(string)$pickupCluster['name'];return $r;}}

  $r=$candidates[0];$r['busy_fallback']=$fallbackBusy;$r['capacity_locked']=$capacityLocked;$r['redispatched_same_driver']=true;$r['dispatch_wave']=$wave;$r['pickup_cluster_id']=(int)$pickupCluster['id'];$r['pickup_cluster_name']=(string)$pickupCluster['name'];return $r;
}
function dd_active_batch_offers(mysqli $conn,string $source,int $orderId): array {
  dd_ensure_schema($conn); $s=mysqli_real_escape_string($conn,$source); $rows=[];
  transiva_schema_query($conn,"UPDATE driver_dispatch_offers SET outcome='expired' WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered' AND expired_at<=NOW()");
  $q=transiva_schema_query($conn,"SELECT * FROM driver_dispatch_offers WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered' AND expired_at>NOW() ORDER BY id ASC");
  while($q&&($r=mysqli_fetch_assoc($q)))$rows[]=$r; return $rows;
}
function dd_driver_offer(mysqli $conn,string $source,int $orderId,string $driver,string $token=''): ?array {
  dd_ensure_schema($conn);$s=mysqli_real_escape_string($conn,$source);$d=mysqli_real_escape_string($conn,$driver);$t=mysqli_real_escape_string($conn,$token);
  $tokenSql=$token!==''?" AND action_token='$t'":'';
  $q=transiva_schema_query($conn,"SELECT * FROM driver_dispatch_offers WHERE source_table='$s' AND order_id=".(int)$orderId." AND driver_username='$d' AND outcome='offered' AND expired_at>NOW()$tokenSql ORDER BY id DESC LIMIT 1");
  return $q?mysqli_fetch_assoc($q)?:null:null;
}
function dd_close_order_offers(mysqli $conn,string $source,int $orderId,string $winner=''): void {
  dd_ensure_schema($conn);$s=mysqli_real_escape_string($conn,$source);$w=mysqli_real_escape_string($conn,$winner);
  if($winner!=='') {
    transiva_schema_query($conn,"UPDATE driver_dispatch_offers SET outcome=IF(driver_username='$w','accepted','lost') WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered'");
    transiva_schema_query($conn,"UPDATE driver_dispatch_history SET outcome=IF(driver_username='$w','accepted','lost') WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered'");
  } else {
    transiva_schema_query($conn,"UPDATE driver_dispatch_offers SET outcome='closed' WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered'");
    transiva_schema_query($conn,"UPDATE driver_dispatch_history SET outcome='closed' WHERE source_table='$s' AND order_id=".(int)$orderId." AND outcome='offered'");
  }
}
function dd_dispatch_batch_for_order(mysqli $conn,string $table,array $order,int $offerSeconds=15,int $maxKm=20,int $batchSize=3): array {
  dd_ensure_schema($conn);$id=(int)($order['id']??0); if($id<=0)return ['assigned'=>false,'reason'=>'invalid_order','offers'=>[]];
  $status=strtolower(dd_clean($order['status']??''));$allowed=$table==='orders'?['pending','merchant_accepted','offered']:['pending','offered'];
  if(!in_array($status,$allowed,true))return ['assigned'=>false,'reason'=>'status_not_dispatchable','offers'=>[]];
  $assigned=dd_clean($order['driver_username']??$order['driver']??'');if($assigned!=='')return ['assigned'=>false,'reason'=>'already_accepted','offers'=>[]];
  $active=dd_active_batch_offers($conn,$table,$id);if($active)return ['assigned'=>true,'unchanged'=>true,'offers'=>$active,'expires_at'=>$active[0]['expired_at']??''];
  $type=dd_normalize_type($order['driver_type']??$order['vehicle_type']??$order['order_type']??'bike');$lat=(float)($order['pickup_lat']??$order['user_lat']??0);$lng=(float)($order['pickup_lng']??$order['user_lng']??0);
  if($lat==0.0||$lng==0.0)return ['assigned'=>false,'reason'=>'pickup_coordinate_missing','offers'=>[]];
  $pickupCluster=tc_cluster_for($lat,$lng);$wave=dd_dispatch_wave_for_order($conn,$table,$id);$allowedClusters=tc_cluster_ids_for_wave((int)$pickupCluster['id'],$wave);$radius=max($maxKm,$wave<=1?12:($wave===2?24:40));$min=$type==='car'?2000:1000;
  $candidates=dd_candidates($conn,$type,$lat,$lng,$min,true,$radius,'available',$allowedClusters);$fallbackBusy=false;
  if(!$candidates){$candidates=dd_candidates($conn,$type,$lat,$lng,$min,false,$radius,'available',$allowedClusters);$fallbackBusy=true;}
  if(!$candidates)return ['assigned'=>false,'reason'=>'no_available_driver','offers'=>[],'dispatch_wave'=>$wave,'pickup_cluster_id'=>(int)$pickupCluster['id']];
  $sourceEsc=mysqli_real_escape_string($conn,$table);$used=[];$q=transiva_schema_query($conn,"SELECT driver_username FROM driver_dispatch_history WHERE source_table='$sourceEsc' AND order_id=$id");while($q&&($r=mysqli_fetch_assoc($q)))$used[strtolower((string)$r['driver_username'])]=true;
  $selected=[];foreach($candidates as $r){if(isset($used[strtolower((string)$r['username'])]))continue;$selected[]=$r;if(count($selected)>=max(1,$batchSize))break;}
  if(!$selected){foreach($candidates as $r){$selected[]=$r;if(count($selected)>=max(1,$batchSize))break;}}
  if(!$selected)return ['assigned'=>false,'reason'=>'no_new_driver','offers'=>[]];
  $batchNo=1;$q=transiva_schema_query($conn,"SELECT COALESCE(MAX(batch_no),0)+1 n FROM driver_dispatch_offers WHERE source_table='$sourceEsc' AND order_id=$id");$rr=$q?mysqli_fetch_assoc($q):null;$batchNo=max(1,(int)($rr['n']??1));$expires=date('Y-m-d H:i:s',time()+max(5,$offerSeconds));$offers=[];
  foreach($selected as $r){$u=(string)$r['username'];$ue=mysqli_real_escape_string($conn,$u);$token=bin2hex(random_bytes(32));$te=mysqli_real_escape_string($conn,$token);$dist=(float)$r['distance_km'];$cid=(int)($r['cluster_id']??tc_cluster_for((float)$r['latitude'],(float)$r['longitude'])['id']);$ee=mysqli_real_escape_string($conn,$expires);
    $ok=transiva_schema_query($conn,"INSERT INTO driver_dispatch_offers(source_table,order_id,driver_username,action_token,distance_km,cluster_id,dispatch_wave,batch_no,expired_at,outcome) VALUES('$sourceEsc',$id,'$ue','$te',$dist,$cid,$wave,$batchNo,'$ee','offered')");if(!$ok)continue;
    dd_record_offer($conn,$table,$id,$u,$dist,$expires);$r['action_token']=$token;$r['expires_at']=$expires;$r['dispatch_wave']=$wave;$r['batch_no']=$batchNo;$r['busy_fallback']=$fallbackBusy;$offers[]=$r;
  }
  if(!$offers)return ['assigned'=>false,'reason'=>'offer_insert_failed','offers'=>[]];
  $first=$offers[0];$sets=[];$fe=mysqli_real_escape_string($conn,(string)$first['username']);$ft=mysqli_real_escape_string($conn,(string)$first['action_token']);$ee=mysqli_real_escape_string($conn,$expires);
  if(dd_column_exists($conn,$table,'offered_driver'))$sets[]="offered_driver='$fe'";if(dd_column_exists($conn,$table,'offer_expired_at'))$sets[]="offer_expired_at='$ee'";if(dd_column_exists($conn,$table,'offer_action_token'))$sets[]="offer_action_token='$ft'";if($sets)transiva_schema_query($conn,"UPDATE `$table` SET ".implode(',',$sets)." WHERE id=$id LIMIT 1");
  return ['assigned'=>true,'offers'=>$offers,'drivers'=>array_map(fn($x)=>(string)$x['username'],$offers),'expires_at'=>$expires,'dispatch_wave'=>$wave,'batch_no'=>$batchNo,'pickup_cluster_id'=>(int)$pickupCluster['id'],'pickup_cluster_name'=>(string)$pickupCluster['name']];
}

function dd_dispatch_one_order(mysqli $conn,string $table,array $order,int $offerSeconds=15,int $maxKm=20): array {
  $r=dd_dispatch_batch_for_order($conn,$table,$order,$offerSeconds,$maxKm,3);
  if(!empty($r['offers'][0])){$f=$r['offers'][0];$r['driver']=$f['username']??'';$r['driver_row']=$f;$r['distance_km']=$f['distance_km']??0;$r['action_token']=$f['action_token']??'';}
  return $r;
}

function dd_record_offer(mysqli $conn,string $source,int $orderId,string $driver,float $distance,string $expiredAt): void {
  dd_ensure_schema($conn);$s=mysqli_real_escape_string($conn,$source);$d=mysqli_real_escape_string($conn,$driver);$e=mysqli_real_escape_string($conn,$expiredAt);transiva_schema_query($conn,"INSERT INTO driver_dispatch_history(source_table,order_id,driver_username,distance_km,expired_at,outcome) VALUES('$s',$orderId,'$d',$distance,'$e','offered')");
}
function dd_mark_outcome(mysqli $conn,string $source,int $orderId,string $driver,string $outcome): void {$s=mysqli_real_escape_string($conn,$source);$d=mysqli_real_escape_string($conn,$driver);$o=mysqli_real_escape_string($conn,$outcome);transiva_schema_query($conn,"UPDATE driver_dispatch_history SET outcome='$o' WHERE source_table='$s' AND order_id=$orderId AND driver_username='$d' ORDER BY id DESC LIMIT 1");}


/**
 * Jalankan redispatch order yang tawarannya sudah habis.
 * Aman dipanggil dari cron maupun polling dashboard karena memakai named lock
 * dan row lock per order. Dengan ini perpindahan A -> B tidak bergantung pada
 * cron hosting yang biasanya hanya berjalan setiap 1 menit.
 */
function dd_dispatch_due_orders(mysqli $conn,int $limit=20,int $offerSeconds=15,int $maxKm=20): array {
  dd_ensure_schema($conn);
  $results=[];
  foreach(['orders','pickup_orders'] as $table){
    if(!dd_table_exists($conn,$table) || !dd_column_exists($conn,$table,'offered_driver')) continue;
    $statusSql=$table==='orders'
      ? "LOWER(COALESCE(status,'')) IN ('pending','merchant_accepted','offered')"
      : "LOWER(COALESCE(status,'')) IN ('pending','offered')";
    $driverFree=[];
    if(dd_column_exists($conn,$table,'driver_username')) $driverFree[]="(driver_username IS NULL OR driver_username='')";
    if(dd_column_exists($conn,$table,'driver')) $driverFree[]="(driver IS NULL OR driver='')";
    $freeSql=$driverFree ? ' AND '.implode(' AND ',$driverFree) : '';
    $tableEsc=mysqli_real_escape_string($conn,$table);
    $expirySql="NOT EXISTS(SELECT 1 FROM driver_dispatch_offers dbo WHERE dbo.source_table='$tableEsc' AND dbo.order_id=`$table`.id AND dbo.outcome='offered' AND dbo.expired_at>NOW())";
    $q=transiva_schema_query($conn,"SELECT id FROM `$table` WHERE $statusSql $freeSql AND $expirySql ORDER BY id ASC LIMIT ".max(1,$limit));
    while($q && ($row=mysqli_fetch_assoc($q))){
      $id=(int)$row['id'];
      $lockName=mysqli_real_escape_string($conn,"transiva_dispatch_{$table}_{$id}");
      $lq=transiva_schema_query($conn,"SELECT GET_LOCK('$lockName',0) locked");
      $lr=$lq?mysqli_fetch_assoc($lq):null;
      if((int)($lr['locked']??0)!==1) continue;
      // Serialisasi hanya fase pemilihan/penetapan driver agar dua order tidak
      // menawarkan driver yang sama pada milidetik yang sama. Lock ini singkat.
      $sq=transiva_schema_query($conn,"SELECT GET_LOCK('transiva_dispatch_select_v2',0) locked");
      $sr=$sq?mysqli_fetch_assoc($sq):null;
      $selectionLocked=((int)($sr['locked']??0)===1);
      if(!$selectionLocked){ transiva_schema_query($conn,"SELECT RELEASE_LOCK('$lockName')"); continue; }
      mysqli_begin_transaction($conn);
      try{
        $fq=transiva_schema_query($conn,"SELECT * FROM `$table` WHERE id=$id LIMIT 1 FOR UPDATE");
        $fresh=$fq?mysqli_fetch_assoc($fq):null;
        if(!$fresh){ mysqli_rollback($conn); transiva_schema_query($conn,"SELECT RELEASE_LOCK('transiva_dispatch_select_v2')"); transiva_schema_query($conn,"SELECT RELEASE_LOCK('$lockName')"); continue; }
        $result=dd_dispatch_one_order($conn,$table,$fresh,$offerSeconds,$maxKm);
        /* Batch FCM: satu order dikirim paralel ke maksimal 3 driver pada wave yang sama. */
        if(!empty($result['assigned']) && empty($result['unchanged']) && !empty($result['offers'])){
          $service=$table==='pickup_orders' ? 'TransSend' : (string)($fresh['order_type']??'Order');
          foreach($result['offers'] as $offerRow){
            $token=trim((string)($offerRow['fcm_token']??'')); // Queue even while registration is recovering; worker resolves latest token.
            $distance=(float)($offerRow['distance_km']??0); $offerDriver=(string)($offerRow['username']??'');
            $payload=[
              'app'=>'transiva','type'=>'transiva_order','event'=>'new_order','screen'=>'driver_order',
              'source'=>$table,'order_db_id'=>(string)$id,'order_id'=>(string)$id,
              'order_type'=>$service,'offered_driver'=>$offerDriver,
              'offer_expired_at'=>(string)($offerRow['expires_at']??$result['expires_at']??''),
              'action_token'=>(string)($offerRow['action_token']??''),
              'dispatch_wave'=>(string)($offerRow['dispatch_wave']??$result['dispatch_wave']??1),
              'batch_no'=>(string)($offerRow['batch_no']??$result['batch_no']??1),
              'pickup_distance_km'=>number_format($distance,2,'.',''),'click_action'=>'OPEN_TRANSIVA'
            ];
            $event='redispatch:'.$table.':'.$id.':'.hash('sha256',$offerDriver.':'.$payload['action_token']);
            order_queue_notification($conn,$event,'fcm',['token'=>$token,'title'=>'Order Baru Transiva','body'=>$service.' • '.number_format($distance,1,',','.').' km dari Anda','data'=>$payload]);
          }
        }

        if(!mysqli_commit($conn))throw new RuntimeException('DISPATCH_COMMIT_FAILED');
        transiva_schema_query($conn,"SELECT RELEASE_LOCK('transiva_dispatch_select_v2')");
        transiva_schema_query($conn,"SELECT RELEASE_LOCK('$lockName')");

        // Smart nudge: bila tidak ada driver idle (fallback ke driver sibuk) atau
        // tidak ada driver online yang bisa ditawari, ajak beberapa driver OFFLINE
        // terdekat untuk online. Ini bukan offer dan tidak mengubah offered_driver.
        $nudgeReason='';
        if(empty($result['assigned'])) $nudgeReason='no_available_driver';
        elseif(!empty($result['driver_row']['busy_fallback'])) $nudgeReason='all_online_busy';
        if($nudgeReason!=='' && function_exists('don_notify_offline_nearby')){
          $service=$table==='pickup_orders' ? 'TransSend' : (string)($fresh['order_type']??'Transiva');
          try {
            $nudge=don_notify_offline_nearby(
              $conn,$table,$id,
              (string)($fresh['driver_type']??$fresh['vehicle_type']??'bike'),
              (float)($fresh['pickup_lat']??$fresh['user_lat']??0),
              (float)($fresh['pickup_lng']??$fresh['user_lng']??0),
              (string)($fresh['pickup_address']??'Lokasi pickup'),
              $service,$nudgeReason,8,5
            );
            $result['offline_nudge_sent']=(int)($nudge['sent']??0);
          } catch(Throwable $nudgeError){ error_log('Redispatch offline nudge gagal: '.$nudgeError->getMessage()); }
        }
        unset($result['driver_row']);
        $result['table']=$table;$result['id']=$id;
        $results[]=$result;
      }catch(Throwable $e){
        mysqli_rollback($conn);
        @transiva_schema_query($conn,"SELECT RELEASE_LOCK('transiva_dispatch_select_v2')");
        transiva_schema_query($conn,"SELECT RELEASE_LOCK('$lockName')");
        $results[]=['table'=>$table,'id'=>$id,'assigned'=>false,'error'=>$e->getMessage()];
      }
    }
  }
  return $results;
}

function dd_driver_active_count(mysqli $conn,string $driver,string $excludeTable='',int $excludeId=0): int {
  $d=mysqli_real_escape_string($conn,$driver);$parts=[];
  if(dd_table_exists($conn,'orders')&&dd_column_exists($conn,'orders','driver')&&dd_column_exists($conn,'orders','status')){
    $parts[]="SELECT id FROM orders WHERE driver='$d' AND LOWER(COALESCE(status,'')) IN ('driver_accepted','arrived_pickup','on_delivery','arrived_delivery','accepted','arrived','picked_up','on_trip','in_progress','ongoing','started')".($excludeTable==='orders'?" AND id<>$excludeId":'');
  }
  if(dd_table_exists($conn,'pickup_orders')&&dd_column_exists($conn,'pickup_orders','status')){
    $conds=[];if(dd_column_exists($conn,'pickup_orders','driver_username'))$conds[]="driver_username='$d'";if(dd_column_exists($conn,'pickup_orders','driver'))$conds[]="driver='$d'";
    if($conds)$parts[]="SELECT id FROM pickup_orders WHERE (".implode(' OR ',$conds).") AND LOWER(COALESCE(status,'')) IN ('taken','driver_accepted','arrived_pickup','on_delivery','arrived_delivery','accepted','arrived','picked_up','on_trip','in_progress','ongoing','started')".($excludeTable==='pickup_orders'?" AND id<>$excludeId":'');
  }
  if(!$parts)return 0;$q=transiva_schema_query($conn,"SELECT COUNT(*) AS total FROM (".implode(' UNION ALL ',$parts).") active_orders");$r=$q?mysqli_fetch_assoc($q):null;return (int)($r['total']??0);
}
function dd_driver_has_other_active(mysqli $conn,string $driver,string $excludeTable='',int $excludeId=0): bool {return dd_driver_active_count($conn,$driver,$excludeTable,$excludeId)>0;}
}
