<?php
declare(strict_types=1);

require_once __DIR__.'/schema_runtime_guard.php';
/* All mutations require the owned order row locked by the caller. No DDL in transactions. */
function sw_query(mysqli $connection,string $sql) {
    $result=transiva_schema_query($connection,$sql);if($result===false)throw new RuntimeException('Database Smart Waiting belum dapat diproses',500);return $result;
}
function sw_bootstrap(mysqli $c): void {
    static $ready=[];$connectionKey=spl_object_id($c);if(isset($ready[$connectionKey]))return;
    sw_query($c,"CREATE TABLE IF NOT EXISTS smart_waiting_sessions (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, source VARCHAR(24) NOT NULL, order_id BIGINT NOT NULL, point_key VARCHAR(80) NOT NULL, label VARCHAR(255) NOT NULL, started_at BIGINT NOT NULL, ended_at BIGINT NULL, free_seconds INT NOT NULL DEFAULT 300, rate_per_minute INT NOT NULL DEFAULT 500, UNIQUE KEY point_once(source,order_id,point_key), KEY active_order(source,order_id,ended_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    sw_query($c,"CREATE TABLE IF NOT EXISTS smart_waiting_bills (source VARCHAR(24) NOT NULL,order_id BIGINT NOT NULL,applied_fee BIGINT NOT NULL DEFAULT 0,credited_fee BIGINT NOT NULL DEFAULT 0,PRIMARY KEY(source,order_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    foreach(['orders','pickup_orders'] as $table){
        $exists=sw_query($c,"SHOW TABLES LIKE '$table'");if(!$exists||!$exists->num_rows)continue;
        $columns=[];$result=sw_query($c,"SHOW COLUMNS FROM `$table`");while($row=$result->fetch_assoc())$columns[$row['Field']]=true;
        foreach(['escrow_amount'=>'BIGINT NOT NULL DEFAULT 0','payment_status'=>"VARCHAR(30) DEFAULT ''",'driver_settlement_status'=>"VARCHAR(30) DEFAULT ''"] as $name=>$definition){
            if(!isset($columns[$name]))sw_query($c,"ALTER TABLE `$table` ADD COLUMN `$name` $definition");
        }
    }
    $ready[$connectionKey]=true;

}
function sw_now(mysqli $c): int { return (int)sw_query($c,'SELECT UNIX_TIMESTAMP() AS n')->fetch_assoc()['n']; }
function sw_fee(int $seconds,int $free=300,int $rate=500): int { return (int)ceil(max(0,$seconds-$free)/60)*$rate; }
function sw_close(mysqli $c,string $source,int $id): void {
    $st=$c->prepare('UPDATE smart_waiting_sessions SET ended_at=UNIX_TIMESTAMP() WHERE source=? AND order_id=? AND ended_at IS NULL');$st->bind_param('si',$source,$id);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$st->close();
}
function sw_start(mysqli $c,string $source,array $order,string $key,string $label): void {
    $id=(int)$order['id'];
    $st=$c->prepare('SELECT id FROM smart_waiting_sessions WHERE source=? AND order_id=? AND point_key=?');$st->bind_param('sis',$source,$id,$key);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$existing=$st->get_result()->fetch_assoc();$st->close();if($existing)return;
    sw_close($c,$source,$id);
    $st=$c->prepare('INSERT INTO smart_waiting_sessions(source,order_id,point_key,label,started_at) VALUES(?,?,?,?,UNIX_TIMESTAMP())');$st->bind_param('siss',$source,$id,$key,$label);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$st->close();
}
function sw_snapshot(mysqli $c,string $source,array $order): array {
    $id=(int)$order['id'];$now=sw_now($c);$rows=[];$active=null;$fee=0;$elapsed=0;
    $st=$c->prepare('SELECT * FROM smart_waiting_sessions WHERE source=? AND order_id=? ORDER BY id');$st->bind_param('si',$source,$id);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$r=$st->get_result();
    while($w=$r->fetch_assoc()){
        $secs=max(0,(int)($w['ended_at']??$now)-(int)$w['started_at']);$amount=sw_fee($secs,(int)$w['free_seconds'],(int)$w['rate_per_minute']);
        $item=['id'=>(int)$w['id'],'point_key'=>$w['point_key'],'label'=>$w['label'],'started_at'=>(int)$w['started_at'],'ended_at'=>$w['ended_at']===null?null:(int)$w['ended_at'],'elapsed_seconds'=>$secs,'free_seconds'=>(int)$w['free_seconds'],'free_remaining_seconds'=>max(0,(int)$w['free_seconds']-$secs),'paid_minutes'=>(int)ceil(max(0,$secs-(int)$w['free_seconds'])/60),'rate_per_minute'=>(int)$w['rate_per_minute'],'fee'=>$amount];
        $rows[]=$item;$fee+=$amount;$elapsed+=$secs;if($w['ended_at']===null)$active=$item;
    }$st->close();
    $st=$c->prepare('SELECT applied_fee FROM smart_waiting_bills WHERE source=? AND order_id=?');$st->bind_param('si',$source,$id);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$bill=$st->get_result()->fetch_assoc();$st->close();$applied=(int)($bill['applied_fee']??0);
    if(in_array(strtolower((string)($order['status']??'')),['canceled','cancelled'],true)){ $active=null;$fee=$applied; }
    $base=max(0,(int)($order['price']??0)-$applied);
    return ['server_epoch'=>$now,'active'=>$active,'sessions'=>$rows,'elapsed_seconds'=>$elapsed,'total_fee'=>$fee,'applied_fee'=>$applied,'base_total'=>$base,'estimated_total'=>$base+$fee,'free_seconds'=>300,'rate_per_minute'=>500,'rounding'=>'started_minute','billing_status'=>$applied>0?'applied':($active?'running':'stopped')];
}
function sw_finalize(mysqli $c,string $source,array $order): array {
    sw_close($c,$source,(int)$order['id']);$state=sw_snapshot($c,$source,$order);$delta=max(0,$state['total_fee']-$state['applied_fee']);if($delta===0)return $order;
    require_once __DIR__.'/payment_upgrade_helper.php';require_once __DIR__.'/transiva_pay_helper.php';
    $id=(int)$order['id'];$uid=(int)$order['user_id'];$public=(string)($order['order_id']??$id);
    $extra='';
    if(pu_payment_method($order)==='balance'){
        if(!tp_wallet_table_exists($c))throw new RuntimeException('Tabel mutasi saldo belum tersedia. Hubungi admin.',500);
        $q=sw_query($c,"SELECT balance FROM users WHERE id=$uid FOR UPDATE");$u=$q->fetch_assoc();$before=(int)($u['balance']??0);
        if($before<$delta)throw new RuntimeException('Saldo TransPay belum cukup untuk biaya tunggu Rp '.number_format($delta,0,',','.').'. Isi saldo lalu konfirmasi kembali.',409);
        $after=$before-$delta;sw_query($c,"UPDATE users SET balance=$after WHERE id=$uid");
        if(!tp_add_wallet_log($c,$uid,'smart_waiting',$delta,$before,$after,'WAIT-'.$source.'-'.$id.'-'.$state['total_fee'],'Smart Waiting '.$public))throw new RuntimeException('Gagal mencatat biaya tunggu',500);
        $old=(int)$order['price'];$extra=",escrow_amount=IF(COALESCE(escrow_amount,0)>0,escrow_amount,$old)+$delta,payment_status='paid_held',driver_settlement_status='held'";
    }
    sw_query($c,"UPDATE `$source` SET price=price+$delta$extra WHERE id=$id");
    $fee=(int)$state['total_fee'];$st=$c->prepare('INSERT INTO smart_waiting_bills(source,order_id,applied_fee) VALUES(?,?,?) ON DUPLICATE KEY UPDATE applied_fee=VALUES(applied_fee)');$st->bind_param('sii',$source,$id,$fee);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$st->close();
    return sw_query($c,"SELECT * FROM `$source` WHERE id=$id")->fetch_assoc();
}
function sw_distance(float $a,float $b,float $x,float $y): float {
    $d=sin(deg2rad($x-$a)/2)**2+cos(deg2rad($a))*cos(deg2rad($x))*sin(deg2rad($y-$b)/2)**2;return 6371000*2*asin(min(1,sqrt(max(0,$d))));
}
function sw_near(array $o,array $data,string $point): bool {
    $a=filter_var($data['driver_lat']??$o['driver_lat']??null,FILTER_VALIDATE_FLOAT);$b=filter_var($data['driver_lng']??$o['driver_lng']??null,FILTER_VALIDATE_FLOAT);
    $x=(float)($o[$point.'_lat']??($point==='pickup'?($o['user_lat']??0):0));$y=(float)($o[$point.'_lng']??($point==='pickup'?($o['user_lng']??0):0));
    return $a!==false&&$b!==false&&abs($a)<=90&&abs($b)<=180&&($x!=0||$y!=0)&&sw_distance($a,$b,$x,$y)<=200;
}
function sw_auto_depart(mysqli $c,string $source,array $o,float $lat,float $lng): void {
    $w=sw_snapshot($c,$source,$o)['active'];if(!$w||strpos($w['point_key'],'stop:')!==0)return;
    $sequence=(int)substr($w['point_key'],5);$public=(string)($o['order_id']??$o['id']);
    $st=$c->prepare('SELECT latitude,longitude FROM ride_waypoints WHERE order_id=? AND sequence_no=?');$st->bind_param('si',$public,$sequence);if(!$st->execute())throw new RuntimeException('Gagal membaca atau menyimpan Smart Waiting',500);$stop=$st->get_result()->fetch_assoc();$st->close();
    if($stop&&sw_distance($lat,$lng,(float)$stop['latitude'],(float)$stop['longitude'])>200)sw_close($c,$source,(int)$o['id']);
}

function sw_settle_pickup(mysqli $c,array $order): void {
    require_once __DIR__.'/transiva_pay_helper.php';require_once __DIR__.'/payment_upgrade_helper.php';
    if(pu_payment_method($order)!=='balance')return;
    $id=(int)$order['id'];$bill=sw_query($c,"SELECT * FROM smart_waiting_bills WHERE source='pickup_orders' AND order_id=$id FOR UPDATE")->fetch_assoc();
    $delta=max(0,(int)($bill['applied_fee']??0)-(int)($bill['credited_fee']??0));if(!$delta)return;
    if(!tp_wallet_table_exists($c))throw new RuntimeException('Tabel mutasi saldo belum tersedia',500);
    $driver=trim((string)($order['driver_username']??''));if($driver==='')$driver=trim((string)($order['driver']??$order['assigned_driver']??''));
    tp_adjust_driver_wallet($c,$driver,$delta,'escrow_release','WAIT-PAYOUT-pickup-'.$id,'Pendapatan Smart Waiting '.($order['order_id']??$id),'in');
    sw_query($c,"UPDATE pickup_orders SET escrow_amount=GREATEST(0,COALESCE(escrow_amount,0)-$delta) WHERE id=$id");
    sw_query($c,"UPDATE smart_waiting_bills SET credited_fee=applied_fee WHERE source='pickup_orders' AND order_id=$id");
}
