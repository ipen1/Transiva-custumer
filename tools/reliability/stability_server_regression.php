<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
// Supply the server source directory. Transport is stubbed: this test never sends email.
$server=$argv[1]??'';if(!is_dir($server))throw new RuntimeException('Usage: php stability_server_regression.php /path/to/server');
require_once $server.'/customer_action_response_helper.php';
require_once $server.'/worker_monitor_alert_helper.php';
function verify(bool $ok,string $name):void {if(!$ok)throw new RuntimeException('FAIL '.$name);echo 'PASS '.$name.PHP_EOL;}
$hydrated=customer_action_committed_response('Saved',static fn()=>['status'=>'arrived_delivery']);
verify($hydrated['success']&&$hydrated['committed']&&$hydrated['order']['status']==='arrived_delivery','commit success + hydration');
$failed=customer_action_committed_response('Saved',static function(){throw new RuntimeException('DB read failed after commit');});
verify($failed['success']&&$failed['committed']&&$failed['refresh_required']&&$failed['order']===null,'post-commit exception remains success');
$invalid=customer_action_committed_response('Saved',static fn()=>null);
verify($invalid['success']&&$invalid['refresh_required'],'post-commit missing order remains success');
$dir=sys_get_temp_dir().'/transiva-alert-test-'.bin2hex(random_bytes(8));mkdir($dir,0700);
$state=$dir.'/state.json';$deliveries=[];
$transport=static function(string $to,string $subject,string $body)use(&$deliveries):bool{$deliveries[]=$subject;return true;};
$bad=['healthy'=>false,'reasons'=>['heartbeat_late','failed_jobs'],'queue'=>['failed'=>2]];
$a=worker_monitor_alert($bad,'admin@example.test',$state,$transport,1000);
verify($a['logged']&&$a['email_accepted']&&count($deliveries)===1,'first incident alerts');
$a=worker_monitor_alert($bad,'admin@example.test',$state,$transport,1059);
verify($a['suppressed']&&count($deliveries)===1,'same incident deduplicates');
$a=worker_monitor_alert($bad,'admin@example.test',$state,$transport,1900);
verify($a['email_accepted']&&count($deliveries)===2,'incident reminder after 15 minutes');
$a=worker_monitor_alert(['healthy'=>true],'admin@example.test',$state,$transport,1910);
verify($a['logged']&&$a['email_accepted']&&count($deliveries)===3,'recovery notification');
$a=worker_monitor_alert(['healthy'=>true],'admin@example.test',$state,$transport,1920);
verify($a['suppressed']&&count($deliveries)===3,'recovery deduplicates');
$a=worker_monitor_alert($bad,"injected@example.test\r\nBcc:other@example.test",$state,$transport,1930);
verify($a['logged']&&!$a['email_configured']&&count($deliveries)===3,'invalid recipient never sent');
foreach(glob($dir.'/*') as $file)unlink($file);rmdir($dir);
