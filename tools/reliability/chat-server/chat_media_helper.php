<?php
declare(strict_types=1);
require_once __DIR__.'/chat_security_helper.php';
require_once __DIR__.'/chat_delivery_helper.php';
require_once __DIR__.'/chat_notification_helper.php';
function chat_media_context(mysqli $conn,array $auth):array {
    $room=chat_room((string)($_POST['room_id']??''));
    $order=chat_find_order($conn,(string)($_POST['source']??'orders'),$room,(string)($_POST['order_id']??''),(string)($_POST['order_db_id']??''));
    if(!$order)chat_out(false,'Order chat tidak ditemukan.',[],404,'ORDER_NOT_FOUND');
    $sender=chat_authorize($conn,$auth,$order);
    $canonical=chat_room((string)($order['room_id']?:($order['order_id']?:$order['id'])));
    return [$order,$canonical,$sender];
}
function chat_media_file(string $name,int $max,bool $image):array {
    $f=$_FILES[$name]??[];$tmp=(string)($f['tmp_name']??'');
    if(($f['error']??UPLOAD_ERR_NO_FILE)!==UPLOAD_ERR_OK||!is_uploaded_file($tmp))chat_out(false,'Lampiran tidak valid.',[],422,'INVALID_MEDIA');
    $size=filesize($tmp);if(!$size||$size>$max)chat_out(false,'Ukuran lampiran terlalu besar.',[],422,'INVALID_MEDIA');
    if($image){$info=@getimagesize($tmp);$mime=$info['mime']??'';
        if(!$info||!in_array($mime,['image/jpeg','image/png','image/webp'],true)||(int)$info[0]*(int)$info[1]>40000000)chat_out(false,'Format atau dimensi foto tidak valid.',[],422,'INVALID_MEDIA');
    }else{$mime=(new finfo(FILEINFO_MIME_TYPE))->file($tmp);$header=file_get_contents($tmp,false,null,0,12);
        if(!in_array($mime,['audio/mp4','video/mp4','audio/m4a','audio/x-m4a','application/octet-stream'],true)||strlen($header)<12||substr($header,4,4)!=='ftyp')chat_out(false,'Format voice tidak valid.',[],422,'INVALID_MEDIA');
    }
    return ['tmp'=>$tmp,'size'=>$size,'mime'=>$mime,'hash'=>hash_file('sha256',$tmp)];
}
/** Files are moved while the unique receipt is locked. A rollback may leave an
 * orphan file; it must never delete files on an ambiguous commit response. */
function chat_media_send(mysqli $conn,array $auth,array $order,string $room,string $sender,string $kind,array $files,int $duration=0):array {
    $operation=trim((string)($_POST['operation_id']??''));
    if($operation==='')$operation='legacy-'.bin2hex(random_bytes(16));
    chat_delivery_validate_id($operation);
    $hash=hash('sha256',json_encode([$order['_source'],$order['id'],$room,$sender,$kind,array_column($files,'hash'),$duration],JSON_THROW_ON_ERROR));
    chat_notification_require_storage($conn,true);
    $uid=(int)$auth['id'];if(!mysqli_begin_transaction($conn))throw new RuntimeException("CHAT_TRANSACTION_UNAVAILABLE");
    try{
        $st=chat_delivery_prepare($conn,'INSERT INTO chat_media_receipts(user_id,operation_id,request_hash) VALUES(?,?,?) ON DUPLICATE KEY UPDATE operation_id=operation_id');
        mysqli_stmt_bind_param($st,'iss',$uid,$operation,$hash);chat_delivery_execute($st);mysqli_stmt_close($st);
        $st=chat_delivery_prepare($conn,'SELECT request_hash,response_json FROM chat_media_receipts WHERE user_id=? AND operation_id=? FOR UPDATE');
        mysqli_stmt_bind_param($st,'is',$uid,$operation);chat_delivery_execute($st);$row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
        if(!$row||!hash_equals($row['request_hash'],$hash))throw new InvalidArgumentException('ID lampiran telah digunakan untuk isi berbeda.');
        if($row['response_json']!==null){$response=json_decode($row['response_json'],true,512,JSON_THROW_ON_ERROR);mysqli_commit($conn);$response['replayed']=true;return $response;}
        if(chat_ended((string)$order['status'])||trim((string)$order['driver'])==='')throw new DomainException('Percakapan tidak aktif.');
        $paths=[];
        foreach($files as $i=>$f){
            $ext=$kind==='voice'?'m4a':($f['mime']==='image/png'?'png':($f['mime']==='image/webp'?'webp':'jpg'));
            $name=$sender.'_'.$room.'_'.date('Ymd_His').'_'.bin2hex(random_bytes(6)).'.'.$ext;
            $dir=$kind==='voice'?dirname(__DIR__).'/uploads/chat_voice/'.date('Y/m'):__DIR__.'/uploads/chat/'.($i===0?'preview':'hd');
            if(!is_dir($dir)&&!mkdir($dir,0755,true)&&!is_dir($dir))throw new RuntimeException('MEDIA_DIRECTORY');
            if(!move_uploaded_file($f['tmp'],$dir.'/'.$name))throw new RuntimeException('MEDIA_MOVE');
            $paths[]=$kind==='voice'?'https://transiva.my.id/server/play_chat_voice.php?file='.rawurlencode(date('Y/m').'/'.$name):'server/view_chat_image.php?file='.rawurlencode(($i===0?'preview':'hd').'/'.$name);
        }
        $message=$kind==='voice'?'[[VOICE]]'.$paths[0].'|'.$duration:'[[IMAGE2]]'.$paths[0].'|'.$paths[1];
        $st=chat_delivery_prepare($conn,'INSERT INTO chat_messages(room_id,sender_type,message,created_at,is_read,read_at) VALUES(?,?,?,NOW(),0,NULL)');
        mysqli_stmt_bind_param($st,'sss',$room,$sender,$message);chat_delivery_execute($st);$id=(int)mysqli_insert_id($conn);mysqli_stmt_close($st);
        chat_notification_enqueue($conn,$order,$room,$sender,$id,$message);
        $response=['success'=>true,'id'=>$id,'operation_id'=>$operation,'client_message_id'=>$operation,'room_id'=>$room,'message_value'=>$message,'message'=>'Lampiran terkirim.','replayed'=>false];
        if($kind==='voice'){$response['url']=$paths[0];$response['audio_url']=$paths[0];$response['duration_ms']=$duration;}else{$response['preview_url']=$paths[0];$response['hd_url']=$paths[1];}
        $json=json_encode($response,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
        $st=chat_delivery_prepare($conn,'UPDATE chat_media_receipts SET response_json=? WHERE user_id=? AND operation_id=?');mysqli_stmt_bind_param($st,'sis',$json,$uid,$operation);chat_delivery_execute($st);mysqli_stmt_close($st);
        if(!mysqli_commit($conn))throw new RuntimeException('MEDIA_COMMIT_UNCONFIRMED');return $response;
    }catch(Throwable $e){try{mysqli_rollback($conn);}catch(Throwable $ignored){}throw $e;}
}
