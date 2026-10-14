<?php
declare(strict_types=1);
function chat_notification_require_storage(mysqli $conn,bool $media=false):void {
    $tables=$media?["chat_messages","chat_media_receipts","order_notification_outbox"]:["chat_messages","order_notification_outbox"];
    $quoted="'".implode("','",$tables)."'";
    $result=mysqli_query($conn,"SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ($quoted)");
    if(!$result)throw new RuntimeException('CHAT_STORAGE_UNAVAILABLE');
    $count=0;while($row=mysqli_fetch_assoc($result)){if(strcasecmp((string)$row['ENGINE'],'InnoDB')!==0)throw new RuntimeException('CHAT_TRANSACTIONAL_STORAGE_REQUIRED');$count++;}
    mysqli_free_result($result);if($count!==count($tables))throw new RuntimeException('CHAT_MIGRATION_REQUIRED');
}
/** Must be called inside the message transaction. Never store a device token here. */
function chat_notification_enqueue(mysqli $conn,array $order,string $room,string $sender,int $id,string $message):void {
    $driver=trim((string)($order['driver']??''));
    $payload=['customer_id'=>(int)$order['user_id'],'driver'=>$driver,'sender'=>$sender,
        'title'=>$sender==='customer'?'Pesan dari Customer':'Pesan dari Driver',
        'body'=>strpos($message,'[[IMAGE')===0?'📷 Mengirim sebuah foto':(strpos($message,'[[VOICE')===0?'🎤 Mengirim pesan suara':mb_substr($message,0,180,'UTF-8')),
        'data'=>['type'=>'chat_message','event'=>'chat_message','screen'=>'chat','room_id'=>$room,
            'order_id'=>(string)($order['order_id']?:$order['id']),'order_db_id'=>(string)$order['id'],
            'source'=>$order['_source']??'orders','order_source'=>$order['_source']??'orders',
            'chat_id'=>(string)$id,'sender_role'=>$sender,'recipient_role'=>$sender==='customer'?'driver':'customer']];
    $key='chat:'.$id;$kind='chat_fcm';$json=json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
    $st=mysqli_prepare($conn,'INSERT INTO order_notification_outbox(event_key,kind,payload_json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE event_key=event_key');
    if(!$st)throw new RuntimeException('CHAT_OUTBOX_UNAVAILABLE');
    mysqli_stmt_bind_param($st,'sss',$key,$kind,$json);
    if(!mysqli_stmt_execute($st))throw new RuntimeException('CHAT_OUTBOX_WRITE_FAILED');mysqli_stmt_close($st);
}
function chat_notification_deliver(mysqli $conn,array $p):void {
    $st=$p['sender']==='customer'?mysqli_prepare($conn,"SELECT fcm_token FROM users WHERE username=? AND role='driver' LIMIT 1"):mysqli_prepare($conn,"SELECT fcm_token FROM users WHERE id=? AND role='customer' LIMIT 1");
    if(!$st)throw new RuntimeException('CHAT_RECIPIENT_LOOKUP');
    if($p['sender']==='customer'){$v=$p['driver'];mysqli_stmt_bind_param($st,'s',$v);}else{$v=(int)$p['customer_id'];mysqli_stmt_bind_param($st,'i',$v);}
    if(!mysqli_stmt_execute($st))throw new RuntimeException('CHAT_RECIPIENT_LOOKUP');
    $row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$token=trim((string)($row['fcm_token']??''));
    if($token==='')throw new RuntimeException('CHAT_TOKEN_UNAVAILABLE');
    $result=send_fcm_notification($token,$p['title'],$p['body'],$p['data']);
    if(!is_array($result)||empty($result['success']))throw new RuntimeException('CHAT_FCM_RETRY');
}
