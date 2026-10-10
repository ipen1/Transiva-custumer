<?php
declare(strict_types=1);
/** CLI monitoring only: no personal data, provider tokens, or public status endpoint. */
function worker_health_path(): string {
    $home=getenv('HOME')?:dirname(__DIR__,2);
    return rtrim($home,'/').'/transiva-worker-health.json';
}
function worker_heartbeat(string $state): void {
    $path=worker_health_path();$tmp=$path.'.'.getmypid().'.tmp';
    $data=json_encode(['version'=>'2.5.29','pid'=>getmypid(),'last_active_at'=>gmdate('c'),'state'=>$state],JSON_THROW_ON_ERROR);
    $old=umask(0077);
    try{if(file_put_contents($tmp,$data,LOCK_EX)===false || !rename($tmp,$path))error_log('WORKER_HEARTBEAT_WRITE_FAILED');}
    finally{umask($old);if(is_file($tmp))@unlink($tmp);}
}
function worker_queue_health(mysqli $conn): array {
    $q=$conn->query("SELECT SUM(status='pending') pending, SUM(status='pending' AND available_at<=NOW()) due, SUM(status='failed') failed, SUM(status='processing') processing, SUM(status='processing' AND locked_at<DATE_SUB(NOW(),INTERVAL 5 MINUTE)) stuck, COALESCE(MAX(IF(status='pending' AND available_at<=NOW(),TIMESTAMPDIFF(SECOND,available_at,NOW()),0)),0) oldest_due_seconds FROM order_notification_outbox");
    $r=$q->fetch_assoc();foreach($r as $k=>$v)$r[$k]=(int)$v;return $r;
}
/** Batch offers can have multiple drivers. Check the exact token, not only offered_driver. */
function worker_offer_is_current(mysqli $conn,array $data): bool {
    if(($data['event']??'')!=='new_order')return true;
    $source=(string)($data['source']??'');$id=(int)($data['order_db_id']??$data['order_id']??0);
    $driver=(string)($data['offered_driver']??'');$token=(string)($data['action_token']??'');
    if(!in_array($source,['orders','pickup_orders'],true)||$id<=0||$driver===''||$token==='')return false;
    $st=$conn->prepare('SELECT 1 FROM driver_dispatch_offers WHERE source_table=? AND order_id=? AND driver_username=? AND action_token=? AND outcome=\'offered\' AND expired_at>NOW() LIMIT 1');
    $st->bind_param('siss',$source,$id,$driver,$token);$st->execute();$valid=$st->get_result()->num_rows===1;$st->close();
    if(!$valid)return false;
    $row=$conn->query("SELECT status FROM `$source` WHERE id=$id")->fetch_assoc();
    return $row && in_array(strtolower((string)$row['status']),['pending','merchant_accepted','offered'],true);
}
