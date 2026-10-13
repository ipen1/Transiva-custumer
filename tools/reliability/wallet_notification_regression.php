<?php
if(PHP_SAPI!=='cli')exit(1);
$server=$argv[1]??'';$dir=sys_get_temp_dir().'/transiva-wallet-notification-'.bin2hex(random_bytes(8));mkdir($dir,0700);
putenv('FIXTURE_STATE='.$dir.'/state.json');putenv('FIXTURE_FAIL=');
file_put_contents($dir.'/state.json',json_encode(['users'=>[1=>['fcm_token'=>'sender'],2=>['fcm_token'=>'latest-receiver']]]));
require __DIR__.'/wallet_endpoint_fixture.php';require $server.'/customer_wallet_notification_helper.php';
$checks=0;function verify_notification($ok,$name){global $checks;if(!$ok)throw new RuntimeException($name);$checks++;echo 'PASS '.$name.PHP_EOL;}
try {
    $calls=[];$send=function($token,$title,$body,$data)use(&$calls){$calls[]=$token;return ['success'=>true];};
    $payload=['user_id'=>2,'title'=>'Saldo Masuk','body'=>'fixture','data'=>['type'=>'wallet_transfer_in']];
    $state=customer_wallet_deliver_notification($conn,$payload,$send);
    verify_notification($state==='sent'&&$calls===['latest-receiver'],'worker resolves recipient token after commit');
    $conn->state['users'][2]['fcm_token']='rotated-token';
    customer_wallet_deliver_notification($conn,$payload,$send);
    verify_notification(end($calls)==='rotated-token','retry uses current token rather than a token persisted in money receipt');
    $failed=false;try{customer_wallet_deliver_notification($conn,$payload,fn()=>['success'=>false]);}catch(RuntimeException $e){$failed=true;}
    verify_notification($failed,'delivery failure propagates to existing worker retry');
    $failed=false;try{customer_wallet_deliver_notification($conn,$payload,function(){throw new RuntimeException('fixture transport');});}catch(RuntimeException $e){$failed=true;}
    verify_notification($failed,'transport exception does not falsely mark a job sent');
    putenv('FIXTURE_FAIL=token_lookup');$failed=false;try{customer_wallet_deliver_notification($conn,$payload,$send);}catch(RuntimeException $e){$failed=true;}putenv('FIXTURE_FAIL=');
    verify_notification($failed,'database lookup failure remains retryable');
    $conn->state['users'][2]['fcm_token']='';$before=count($calls);
    verify_notification(customer_wallet_deliver_notification($conn,$payload,$send)==='unavailable'&&count($calls)===$before,'missing token does not send to another user');
    $worker=file_get_contents($server.'/maintenance/process_order_notifications.php');
    verify_notification(str_contains($worker,"\$job['kind']==='wallet_fcm'")&&str_contains($worker,'customer_wallet_deliver_notification($conn,$p)'),'existing worker dispatches the new wallet intent kind');
}finally{foreach(glob($dir.'/*') as $file)unlink($file);rmdir($dir);}
echo 'PASS '.$checks.' notification fixture scenarios'.PHP_EOL;
