<?php
declare(strict_types=1);
// Run every minute through cron; CLI only, no HTTP trigger or secrets in arguments.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require_once dirname(__DIR__).'/config.php';
define('TRANSIVA_FCM_WORKER',true);
require_once dirname(__DIR__).'/send_fcm.php';
require_once dirname(__DIR__).'/driver_dispatch_helper.php';
require_once dirname(__DIR__).'/worker_reliability_helper.php';
require_once dirname(__DIR__).'/customer_wallet_notification_helper.php';
require_once dirname(__DIR__).'/chat_notification_helper.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$lock=mysqli_query($conn,"SELECT GET_LOCK('transiva_outbox_worker_v28',0) AS acquired");
if((int)(mysqli_fetch_assoc($lock)['acquired']??0)!==1)exit;
worker_heartbeat('running');
register_shutdown_function(function()use($conn){worker_heartbeat(error_get_last()?'interrupted':'stopped');try{mysqli_query($conn,"SELECT RELEASE_LOCK('transiva_outbox_worker_v28')");}catch(Throwable $ignored){}});
$nextDispatch=0.0;$nextHeartbeat=0.0;
$watch=in_array('--watch',$argv,true);$deadline=microtime(true)+55;
for($i=0;$i<200 && microtime(true)<$deadline;$i++){
    if(microtime(true)>=$nextHeartbeat){worker_heartbeat('running');$nextHeartbeat=microtime(true)+10;}
    if(microtime(true)>=$nextDispatch){
        foreach(dd_dispatch_due_orders($conn,20,15,20) as $dispatch){if(isset($dispatch['error']))error_log('DISPATCH_RETRY source='.$dispatch['table'].' id='.$dispatch['id']);}
        $nextDispatch=microtime(true)+3;
    }
    mysqli_begin_transaction($conn);
    $q=mysqli_query($conn,"SELECT * FROM order_notification_outbox WHERE (status='pending' AND available_at<=NOW()) OR (status='processing' AND locked_at<DATE_SUB(NOW(),INTERVAL 5 MINUTE)) ORDER BY id LIMIT 1 FOR UPDATE");
    $job=mysqli_fetch_assoc($q);
    if(!$job){mysqli_rollback($conn);if($watch){sleep(1);continue;}break;}
    $id=(int)$job['id'];$attempt=(int)$job['attempts']+1;
    mysqli_query($conn,"UPDATE order_notification_outbox SET status='processing',locked_at=NOW(),attempts=$attempt WHERE id=$id");
    mysqli_commit($conn);
    try{
        $p=json_decode((string)$job['payload_json'],true,512,JSON_THROW_ON_ERROR);
        if($job['kind']==='fcm'){
            $expired=strtotime((string)($p['data']['offer_expired_at']??''));
            if(($expired && $expired<=time()) || !worker_offer_is_current($conn,$p['data']??[])){mysqli_query($conn,"UPDATE order_notification_outbox SET status='expired',payload_json='{}' WHERE id=$id");continue;}
            $result=send_fcm_notification($p['token'],$p['title'],$p['body'],$p['data']);
            if(!is_array($result)||empty($result['success']))throw new RuntimeException('FCM_DELIVERY_FAILED');
        }elseif($job['kind']==='chat_fcm'){
            chat_notification_deliver($conn,$p);
        }elseif($job['kind']==='wallet_fcm'){
            $delivery=customer_wallet_deliver_notification($conn,$p);
            if($delivery==='unavailable'){
                mysqli_query($conn,"UPDATE order_notification_outbox SET status='expired',payload_json='{}',last_error='RECIPIENT_TOKEN_UNAVAILABLE' WHERE id=$id");
                continue;
            }
        }elseif($job['kind']==='food_stats'){
            mysqli_begin_transaction($conn);
            foreach($p['items'] as $item){$mid=(int)$item['id'];$qty=max(1,(int)$item['qty']);$restaurant=(int)$p['restaurant_id'];
                $st=mysqli_prepare($conn,'UPDATE merchant_menu_meta SET sales_count=sales_count+? WHERE menu_id=? AND restaurant_id=?');mysqli_stmt_bind_param($st,'iii',$qty,$mid,$restaurant);mysqli_stmt_execute($st);mysqli_stmt_close($st);
            }
            require_once dirname(__DIR__).'/merchant_daily_stats_helper.php';merchant_daily_stats_mark_dirty($conn,(int)$p['restaurant_id']);
        }elseif($job['kind']==='offline_nudge'){
            don_notify_offline_nearby($conn,($p['source']??'orders'),(int)$p['id'],$p['driver_type'],(float)$p['latitude'],(float)$p['longitude'],$p['address'],$p['order_type'],$p['reason'],8,5);
        }else throw new RuntimeException('UNKNOWN_NOTIFICATION_KIND');
        mysqli_query($conn,"UPDATE order_notification_outbox SET status='sent',sent_at=NOW(),payload_json='{}',last_error=NULL WHERE id=$id");
        if($job['kind']==='food_stats')mysqli_commit($conn);
    }catch(Throwable $e){
        if($job['kind']==='food_stats')mysqli_rollback($conn);
        $delay=min(3600,15*(2**min(8,$attempt)));$state=$attempt>=8?'failed':'pending';
        $safeError=substr(get_class($e),0,80); // No tokens, addresses or provider response in persistent errors.
        $st=mysqli_prepare($conn,'UPDATE order_notification_outbox SET status=?,available_at=DATE_ADD(NOW(),INTERVAL ? SECOND),last_error=? WHERE id=?');
        mysqli_stmt_bind_param($st,'sisi',$state,$delay,$safeError,$id);mysqli_stmt_execute($st);mysqli_stmt_close($st);
        error_log('ORDER_OUTBOX_RETRY id='.$id.' attempt='.$attempt);
    }
}
