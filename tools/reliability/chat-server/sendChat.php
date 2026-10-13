<?php
declare(strict_types=1);
error_reporting(0);ini_set('display_errors','0');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Device-UUID');
header('Access-Control-Allow-Methods: POST, OPTIONS');
if(($_SERVER['REQUEST_METHOD']??'')==='OPTIONS') exit;
require_once __DIR__.'/config.php';require_once __DIR__.'/chat_security_helper.php';require_once __DIR__.'/p0_security_helper.php';require_once __DIR__.'/send_fcm.php';require_once __DIR__.'/chat_delivery_helper.php';
$auth=native_require_user($conn);chat_bootstrap($conn);
$data=json_decode(file_get_contents('php://input')?:'{}',true);if(!is_array($data))chat_out(false,'Request JSON tidak valid.',[],400,'INVALID_JSON');
$room=chat_room((string)($data['room_id']??''));$public=trim((string)($data['order_id']??''));$db=trim((string)($data['order_db_id']??''));$source=trim((string)($data['source']??$data['order_source']??'orders'));$msg=trim((string)($data['message']??''));
if($room===''&&$public!=='')$room=chat_room($public);if($public===''&&$room!=='')$public=preg_replace('/^ROOM-/','',$room);
if($msg==='')chat_out(false,'Pesan tidak boleh kosong.',[],422,'EMPTY_MESSAGE');
$msg=preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/','',$msg)??'';if(mb_strlen($msg,'UTF-8')>1000)chat_out(false,'Pesan maksimal 1000 karakter.',[],422,'MESSAGE_TOO_LONG');
$order=chat_find_order($conn,$source,$room,$public,$db);if(!$order)chat_out(false,'Order chat tidak ditemukan.',[],404,'ORDER_NOT_FOUND');
$sender=chat_authorize($conn,$auth,$order);$status=(string)($order['status']??'');
$canonical=trim((string)($order['order_id']??''));if($canonical==='')$canonical=(string)$order['id'];
$dbRoom=chat_room((string)($order['room_id']??''));if($dbRoom!=='')$room=$dbRoom;if($room==='')$room=chat_room($canonical);
$client=trim((string)($data['client_message_id']??''));
try {
    chat_delivery_validate_id($client);
    if($client!=='') {
        $prior=chat_delivery_receipt($conn,(int)$auth['id'],$client,chat_delivery_hash($room,$sender,$msg));
        if($prior!==null)chat_out(true,'Pesan sudah terkirim.',['client_message_id'=>$client,'replayed'=>true,'chat'=>['id'=>$prior,'room_id'=>$room],'status'=>$status]);
    }
}catch(InvalidArgumentException $error){chat_out(false,$error->getMessage(),[],409,'CHAT_ID_CONFLICT');}
catch(Throwable $error){error_log('CHAT_RECEIPT_UNAVAILABLE');chat_out(false,'Penyimpanan chat belum siap. Pesan tetap dapat dikirim ulang dengan ID yang sama.',[],503,'CHAT_STORAGE_UNAVAILABLE');}
if(chat_ended($status))chat_out(false,'Percakapan order ini telah berakhir.',['ended'=>true,'status'=>$status],409,'CHAT_ENDED');
if(trim((string)($order['driver']??''))==='')chat_out(false,'Chat tersedia setelah driver menerima pesanan.',[],409,'DRIVER_NOT_ASSIGNED');
p0_rate_limit($conn,'chat_send',(int)$auth['id'].'|'.p0_client_ip(),80,60);
try {
    $delivery=chat_delivery_send($conn,(int)$auth['id'],$client,$room,$sender,$msg);$id=$delivery['id'];
    if($delivery['replayed'])chat_out(true,'Pesan sudah terkirim.',['client_message_id'=>$client,'replayed'=>true,'chat'=>['id'=>$id,'room_id'=>$room],'status'=>$status]);
}catch(InvalidArgumentException $error){chat_out(false,$error->getMessage(),[],409,'CHAT_ID_CONFLICT');}
catch(Throwable $error){error_log('CHAT_SEND_UNCONFIRMED');chat_out(false,'Hasil pesan belum terkonfirmasi. Kirim ulang pesan yang sama.',[],503,'CHAT_SEND_UNCONFIRMED');}
if(($order['_source']??'')==='orders' && native_column_exists($conn,'orders','room_id')){$u=mysqli_prepare($conn,'UPDATE orders SET room_id=? WHERE id=?');if($u){$oid=(int)$order['id'];mysqli_stmt_bind_param($u,'si',$room,$oid);mysqli_stmt_execute($u);mysqli_stmt_close($u);}}

/*
 * Real-time chat notification.
 * Pesan tetap sukses walaupun FCM gagal: database adalah source of truth dan
 * poller Android tetap menjadi fallback. FCM hanya jalur realtime.
 */
$fcmResult=['success'=>false];$recipientRole=$sender==='customer'?'driver':'customer';$recipientToken='';
try {
$recipientToken='';
$recipientRole=$sender==='customer'?'driver':'customer';

if($sender==='customer'){
    $driverUsername=trim((string)($order['driver']??''));
    if($driverUsername!==''){
        $q=mysqli_prepare($conn,"SELECT fcm_token FROM users WHERE username=? AND role='driver' LIMIT 1");
        if($q){
            mysqli_stmt_bind_param($q,'s',$driverUsername);
            mysqli_stmt_execute($q);
            $rr=mysqli_fetch_assoc(mysqli_stmt_get_result($q));
            mysqli_stmt_close($q);
            $recipientToken=trim((string)($rr['fcm_token']??''));
        }
    }
}else{
    $customerId=(int)($order['user_id']??0);
    if($customerId>0){
        $q=mysqli_prepare($conn,"SELECT fcm_token FROM users WHERE id=? AND role='customer' LIMIT 1");
        if($q){
            mysqli_stmt_bind_param($q,'i',$customerId);
            mysqli_stmt_execute($q);
            $rr=mysqli_fetch_assoc(mysqli_stmt_get_result($q));
            mysqli_stmt_close($q);
            $recipientToken=trim((string)($rr['fcm_token']??''));
        }
    }
}

$preview=$msg;
if(strpos($msg,'[[IMAGE]]')===0 || strpos($msg,'[[IMAGE2]]')===0){
    $preview='📷 Mengirim sebuah foto';
}elseif(strpos($msg,'[[VOICE]]')===0 || strpos($msg,'[[VOICE2]]')===0){
    $preview='🎤 Mengirim pesan suara';
}
if(mb_strlen($preview,'UTF-8')>180) $preview=mb_substr($preview,0,177,'UTF-8').'...';

$pushTitle=$sender==='customer'?'💬 Pesan dari Customer':'💬 Pesan dari Driver';
$fcmResult=['success'=>false,'error_code'=>'EMPTY_FCM_TOKEN','message'=>'Token FCM penerima kosong'];

if($recipientToken!==''){
    $fcmResult=send_fcm_notification($recipientToken,$pushTitle,$preview,[
        'type'=>'chat_message',
        'event'=>'chat_message',
        'screen'=>'chat',
        'sender_role'=>$sender,
        'recipient_role'=>$recipientRole,
        'order_id'=>$canonical,
        'order_db_id'=>(string)($order['id']??''),
        'order_source'=>(string)($order['_source']??$source),
        'source'=>(string)($order['_source']??$source),
        'room_id'=>$room,
        'chat_id'=>(string)$id,
        'priority'=>'high',
        'sound'=>'default'
    ]);
}

if(empty($fcmResult['success'])){
    error_log('CHAT FCM: '.json_encode([
        'chat_id'=>$id,
        'recipient_role'=>$recipientRole,
        'has_token'=>$recipientToken!=='' ? 1 : 0,
        'http_code'=>$fcmResult['http_code']??0,
        'error_code'=>$fcmResult['error_code']??'',
        'response'=>$fcmResult['response']??($fcmResult['message']??'')
    ],JSON_UNESCAPED_UNICODE));
}


}catch(Throwable $error){error_log('CHAT_POST_COMMIT_NOTIFICATION_FAILED');}
chat_out(true,'Pesan terkirim.',['client_message_id'=>$client,'replayed'=>false,'chat'=>['id'=>$id,'room_id'=>$room,'sender_type'=>$sender,'message'=>$msg,'created_at'=>date('Y-m-d H:i:s'),'is_read'=>0],'status'=>$status,'fcm'=>[
    'success'=>(bool)($fcmResult['success']??false),
    'error_code'=>(string)($fcmResult['error_code']??''),
    'recipient_role'=>$recipientRole,
    'token_available'=>$recipientToken!==''
]]);
