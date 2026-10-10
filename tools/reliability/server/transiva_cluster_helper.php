<?php
declare(strict_types=1);


require_once __DIR__.'/schema_runtime_guard.php';
/**
 * Transiva Regional + Cluster engine (database synchronized).
 * Backward compatible with the previous tc_* API.
 * Source of truth: transiva_regions / transiva_clusters / transiva_cluster_neighbors.
 */
if (!function_exists('tc_definitions')) {
function tc_db(): ?mysqli {
    global $conn;
    return (isset($conn) && $conn instanceof mysqli) ? $conn : null;
}
function tc_haversine(float $lat1,float $lng1,float $lat2,float $lng2): float {
    $r=6371.0;$dLat=deg2rad($lat2-$lat1);$dLng=deg2rad($lng2-$lng1);
    $a=sin($dLat/2)**2+cos(deg2rad($lat1))*cos(deg2rad($lat2))*sin($dLng/2)**2;
    return $r*2*atan2(sqrt($a),sqrt(max(0.0000001,1-$a)));
}
function tc_ensure_schema(?mysqli $db=null): void {
    static $done=false;if($done)return;$db=$db?:tc_db();if(!$db)return;
    transiva_schema_query($db,"CREATE TABLE IF NOT EXISTS transiva_regions (
      id INT UNSIGNED NOT NULL AUTO_INCREMENT,
      code VARCHAR(50) NOT NULL,
      name VARCHAR(100) NOT NULL,
      center_lat DECIMAL(10,7) NOT NULL DEFAULT 0,
      center_lng DECIMAL(10,7) NOT NULL DEFAULT 0,
      coverage_km DECIMAL(8,2) NOT NULL DEFAULT 80.00,
      is_active TINYINT(1) NOT NULL DEFAULT 1,
      sort_order INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY(id), UNIQUE KEY uq_region_code(code), KEY idx_region_active(is_active,sort_order)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    transiva_schema_query($db,"CREATE TABLE IF NOT EXISTS transiva_clusters (
      id INT UNSIGNED NOT NULL AUTO_INCREMENT,
      region_id INT UNSIGNED NOT NULL,
      code VARCHAR(60) NOT NULL,
      name VARCHAR(120) NOT NULL,
      center_lat DECIMAL(10,7) NOT NULL,
      center_lng DECIMAL(10,7) NOT NULL,
      coverage_km DECIMAL(8,2) NOT NULL DEFAULT 18.00,
      is_active TINYINT(1) NOT NULL DEFAULT 1,
      sort_order INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY(id), UNIQUE KEY uq_cluster_code(code), KEY idx_cluster_region(region_id,is_active,sort_order)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    transiva_schema_query($db,"CREATE TABLE IF NOT EXISTS transiva_cluster_neighbors (
      cluster_id INT UNSIGNED NOT NULL,
      neighbor_cluster_id INT UNSIGNED NOT NULL,
      PRIMARY KEY(cluster_id,neighbor_cluster_id), KEY idx_neighbor_reverse(neighbor_cluster_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $q=transiva_schema_query($db,"SELECT COUNT(*) n FROM transiva_regions");$n=$q?(int)(mysqli_fetch_assoc($q)['n']??0):0;
    if($n===0){
      transiva_schema_query($db,"INSERT INTO transiva_regions(code,name,center_lat,center_lng,coverage_km,is_active,sort_order) VALUES('PARIGI','Parigi',-0.8024100,120.1710800,85,1,1)");
      $rid=(int)mysqli_insert_id($db);
      $seed=[
       ['SUMBERSARI','Sumbersari',-0.9291806,120.2289806,1],['DOLAGO_RIBAMBA','Dolago / Ribamba',-0.8748000,120.2040000,2],
       ['PARIGI','Parigi',-0.8024100,120.1710800,3],['PANGI','Pangi',-0.7416889,120.0681194,4],['TOBOLI','Toboli',-0.6999500,120.0805500,5]
      ];$ids=[];
      $st=mysqli_prepare($db,"INSERT INTO transiva_clusters(region_id,code,name,center_lat,center_lng,coverage_km,is_active,sort_order) VALUES(?,?,?,?,?,18,1,?)");
      if($st){foreach($seed as $s){[$code,$name,$lat,$lng,$ord]=$s;mysqli_stmt_bind_param($st,'issddi',$rid,$code,$name,$lat,$lng,$ord);mysqli_stmt_execute($st);$ids[$ord]=(int)mysqli_insert_id($db);}mysqli_stmt_close($st);}
      $pairs=[[1,2],[2,3],[3,4],[4,5]];foreach($pairs as [$a,$b]){if(!isset($ids[$a],$ids[$b]))continue;$aa=$ids[$a];$bb=$ids[$b];transiva_schema_query($db,"INSERT IGNORE INTO transiva_cluster_neighbors(cluster_id,neighbor_cluster_id) VALUES($aa,$bb),($bb,$aa)");}
    }
    $done=true;
}
function tc_regions(bool $activeOnly=true): array {
    $db=tc_db();if(!$db)return [];tc_ensure_schema($db);$where=$activeOnly?'WHERE is_active=1':'';
    $q=transiva_schema_query($db,"SELECT id,code,name,center_lat lat,center_lng lng,coverage_km,is_active,sort_order FROM transiva_regions $where ORDER BY sort_order,id");$out=[];
    while($q&&$r=mysqli_fetch_assoc($q)){$r['id']=(int)$r['id'];$r['lat']=(float)$r['lat'];$r['lng']=(float)$r['lng'];$r['coverage_km']=(float)$r['coverage_km'];$r['is_active']=(int)$r['is_active'];$out[]=$r;}return $out;
}
function tc_definitions(?int $regionId=null,bool $activeOnly=true): array {
    $db=tc_db();if(!$db)return [];tc_ensure_schema($db);$where=[];if($activeOnly)$where[]='c.is_active=1';if($regionId!==null)$where[]='c.region_id='.(int)$regionId;$sql=$where?'WHERE '.implode(' AND ',$where):'';
    $q=transiva_schema_query($db,"SELECT c.id,c.region_id,c.code,c.name,c.center_lat lat,c.center_lng lng,c.coverage_km,c.is_active,c.sort_order,r.code region_code,r.name region_name,r.is_active region_active FROM transiva_clusters c JOIN transiva_regions r ON r.id=c.region_id $sql ORDER BY c.sort_order,c.id");$defs=[];
    while($q&&$r=mysqli_fetch_assoc($q)){$id=(int)$r['id'];$r['id']=$id;$r['region_id']=(int)$r['region_id'];$r['lat']=(float)$r['lat'];$r['lng']=(float)$r['lng'];$r['coverage_km']=(float)$r['coverage_km'];$r['is_active']=(int)$r['is_active'];$r['neighbors']=[];$defs[$id]=$r;}
    if($defs){$ids=implode(',',array_map('intval',array_keys($defs)));$nq=transiva_schema_query($db,"SELECT cluster_id,neighbor_cluster_id FROM transiva_cluster_neighbors WHERE cluster_id IN ($ids)");while($nq&&$n=mysqli_fetch_assoc($nq)){ $cid=(int)$n['cluster_id'];$nid=(int)$n['neighbor_cluster_id'];if(isset($defs[$cid],$defs[$nid]))$defs[$cid]['neighbors'][]=$nid; }}
    return $defs;
}
function tc_region_for(float $lat,float $lng): array {
    $regions=tc_regions(true);$best=null;$bestKm=INF;foreach($regions as $r){$km=tc_haversine($lat,$lng,$r['lat'],$r['lng']);if($km<$bestKm){$best=$r;$bestKm=$km;}}
    if(!$best)return ['id'=>0,'code'=>'OUTSIDE','name'=>'Di luar regional','distance_km'=>0.0,'inside_region'=>false];$best['distance_km']=round($bestKm,2);$best['inside_region']=$bestKm<=(float)$best['coverage_km'];return $best;
}
function tc_cluster_for(float $lat,float $lng): array {
    $region=tc_region_for($lat,$lng);$regionId=(int)($region['id']??0);$defs=$regionId?tc_definitions($regionId,true):tc_definitions(null,true);$best=null;$bestKm=INF;
    foreach($defs as $c){$km=tc_haversine($lat,$lng,(float)$c['lat'],(float)$c['lng']);if($km<$bestKm){$best=$c;$bestKm=$km;}}
    if(!$best)return ['id'=>0,'code'=>'OUTSIDE','name'=>'Di luar cluster','distance_km'=>0.0,'neighbors'=>[],'region'=>$region,'inside_launch_area'=>false];
    $best['distance_km']=round($bestKm,2);$best['inside_launch_area']=($region['inside_region']??false)&&$bestKm<=(float)($best['coverage_km']??18);$best['region']=$region;return $best;
}
function tc_cluster_ids_for_wave(int $originId,int $wave): array {
    $defs=tc_definitions(null,true);if(!isset($defs[$originId]))return array_keys($defs);$origin=$defs[$originId];$rid=(int)$origin['region_id'];
    if($wave<=1)return [$originId];if($wave===2)return array_values(array_unique(array_merge([$originId],array_map('intval',$origin['neighbors']??[]))));
    return array_values(array_map('intval',array_keys(tc_definitions($rid,true))));
}
function tc_offer_wave(mysqli $conn,string $source,int $orderId): int {
    $s=mysqli_real_escape_string($conn,$source);$q=transiva_schema_query($conn,"SELECT COUNT(*) total FROM driver_dispatch_history WHERE source_table='$s' AND order_id=".(int)$orderId);$r=$q?mysqli_fetch_assoc($q):null;$n=(int)($r['total']??0);return $n<=0?1:($n===1?2:3);
}
function tc_online_driver_counts(mysqli $conn): array {
    tc_ensure_schema($conn);$defs=tc_definitions(null,true);$counts=[];foreach($defs as $id=>$c)$counts[$id]=0;
    $q=transiva_schema_query($conn,"SELECT latitude,longitude FROM driver_profiles WHERE is_online=1 AND latitude IS NOT NULL AND longitude IS NOT NULL AND latitude<>0 AND longitude<>0");
    while($q&&($r=mysqli_fetch_assoc($q))){$c=tc_cluster_for((float)$r['latitude'],(float)$r['longitude']);$id=(int)($c['id']??0);if(isset($counts[$id]))$counts[$id]++;}
    $out=[];foreach($defs as $id=>$c)$out[]=['id'=>$id,'code'=>$c['code'],'name'=>$c['name'],'region_id'=>(int)$c['region_id'],'region_name'=>$c['region_name'],'active_drivers'=>$counts[$id],'neighbors'=>$c['neighbors']];return $out;
}
function tc_driver_cluster_payload(mysqli $conn,float $lat,float $lng): array {$cluster=tc_cluster_for($lat,$lng);return ['current'=>$cluster,'region'=>$cluster['region']??null,'clusters'=>tc_online_driver_counts($conn)];}
}
