<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require_once dirname(__DIR__).'/worker_reliability_helper.php';
require_once dirname(__DIR__).'/worker_monitor_delivery_helper.php';
$monitorFinished=false;
$alertFile=dirname(worker_health_path()).'/transiva-worker-alert-state.json';
register_shutdown_function(static function()use(&$monitorFinished,$alertFile){
    // config.php can exit on unavailable configuration/DB instead of throwing.
    if(!$monitorFinished){
        $settings=$GLOBALS['env']??[];
        $email=(string)($settings['WORKER_ALERT_EMAIL']??getenv('TRANSIVA_WORKER_ALERT_EMAIL')?:'');
        worker_monitor_delivery_alert(['healthy'=>false,'reasons'=>['health_check_unavailable']],$email,$alertFile);
        exit(2);
    }
});
try {
    require_once dirname(__DIR__).'/config.php';
    mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
    $heartbeat=json_decode((string)@file_get_contents(worker_health_path()),true)?:[];
    $stamp=strtotime((string)($heartbeat['last_active_at']??''));$age=$stamp?time()-$stamp:null;
    $queue=worker_queue_health($conn);$limit=max(1,(int)($env['WORKER_ALERT_BACKLOG']??500));$reasons=[];
    if($age===null||$age<0||$age>120)$reasons[]='heartbeat_late';
    if(($heartbeat['version']??'')!=='2.5.29'||!in_array($heartbeat['state']??'',['running','stopped'],true))$reasons[]='worker_state_invalid';
    if($queue['failed']>0)$reasons[]='failed_jobs';if($queue['stuck']>0)$reasons[]='stuck_jobs';
    if($queue['oldest_due_seconds']>120)$reasons[]='queue_late';if($queue['pending']>=$limit)$reasons[]='queue_backlog';
    $result=['healthy'=>count($reasons)===0,'heartbeat_age_seconds'=>$age,'worker'=>$heartbeat,'queue'=>$queue,'reasons'=>$reasons];
    $email=(string)($env['WORKER_ALERT_EMAIL']??getenv('TRANSIVA_WORKER_ALERT_EMAIL')?:'');
    $result['alert']=worker_monitor_delivery_alert($result,$email,$alertFile);$monitorFinished=true;
    echo json_encode($result,JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES).PHP_EOL;exit($result['healthy']?0:1);
}catch(Throwable $error){
    $email=(string)(($env??[])['WORKER_ALERT_EMAIL']??getenv('TRANSIVA_WORKER_ALERT_EMAIL')?:'');
    worker_monitor_delivery_alert(['healthy'=>false,'reasons'=>['health_check_unavailable']],$email,$alertFile);$monitorFinished=true;
    fwrite(STDERR,'Worker health unavailable: '.get_class($error).PHP_EOL);exit(2);
}
