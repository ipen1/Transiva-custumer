<?php
if(PHP_SAPI!=='cli')exit(1);
require ($argv[1]??'').'/worker_monitor_delivery_helper.php';
function check_alert($ok,$title){if(!$ok)throw new RuntimeException($title);echo 'PASS '.$title.PHP_EOL;}
check_alert(str_contains(file_get_contents(($argv[1]??'').'/maintenance/check_worker_health.php'), 'worker_monitor_delivery_alert('), 'health monitor routes to retry delivery helper');
$dir=sys_get_temp_dir().'/transiva-wallet-alert-'.bin2hex(random_bytes(8));mkdir($dir,0700);
try {
 $state=$dir.'/state';$calls=0;
 $send=function($to,$subject,$body)use(&$calls){$calls++;return $calls>1;};
 $bad=['healthy'=>false,'reasons'=>['late']];
 $a=worker_monitor_delivery_alert($bad,'admin@example.test',$state,$send,1000);
 check_alert($a['logged']&&!$a['email_accepted']&&$calls===1,'failed mail still records incident');
 $a=worker_monitor_delivery_alert($bad,'admin@example.test',$state,$send,1059);
 check_alert($a['suppressed']&&$calls===1,'backoff avoids immediate repeated mail');
 $a=worker_monitor_delivery_alert($bad,'admin@example.test',$state,$send,1060);
 check_alert(!$a['logged']&&$a['email_accepted']&&$calls===2,'mail retries before 15 minute log reminder');
 $a=worker_monitor_delivery_alert($bad,'admin@example.test',$state,$send,1100);
 check_alert($a['suppressed']&&$calls===2,'successful mail ends retry');
 $state=$dir.'/unconfigured';
 worker_monitor_delivery_alert($bad,'',$state,$send,1200);
 $a=worker_monitor_delivery_alert($bad,'admin@example.test',$state,$send,1201);
 check_alert(!$a['logged']&&$a['email_accepted'],'new recipient immediately receives pending incident');
 $state=$dir.'/recovery';$subjects=[];
 $fail=function($to,$subject,$body)use(&$subjects){$subjects[]=$subject;return false;};
 worker_monitor_delivery_alert($bad,'admin@example.test',$state,$fail,1300);
 $a=worker_monitor_delivery_alert(['healthy'=>true],'admin@example.test',$state,$fail,1301);
 check_alert($a['logged']&&end($subjects)==='Transiva: worker pulih','recovery supersedes unsent incident');
 $a=worker_monitor_delivery_alert(['healthy'=>true],'admin@example.test',$state,fn()=>true,1361);
 check_alert(!$a['logged']&&$a['email_accepted'],'failed recovery notification is retried');
} finally {foreach(glob($dir.'/*') as $file)unlink($file);rmdir($dir);}
