<?php
declare(strict_types=1);
require_once __DIR__.'/chat_security_helper.php';
/** Server-derived order evidence, never a client-supplied owner/status. */
function support_order_evidence(mysqli $conn,array $auth,array $data):string {
    $public=trim((string)($data['order_id']??''));$db=trim((string)($data['order_db_id']??''));
    if($public===''&&$db==='')return '';
    $order=chat_find_order($conn,(string)($data['source']??'orders'),'',$public,$db);
    if(!$order)chat_out(false,'Pesanan tidak ditemukan.',[],404,'ORDER_NOT_FOUND');
    chat_authorize($conn,$auth,$order);
    $fields=[];foreach(['order_type','pickup_address','delivery_address','from_address','to_address','price','payment_method','payment_status','created_at'] as $field)if(native_column_exists($conn,$order['_source'],$field))$fields[]='`'.$field.'`';
    $extra=[];if($fields){$st=mysqli_prepare($conn,'SELECT '.implode(',',$fields).' FROM `'.$order['_source'].'` WHERE id=?');$id=(int)$order['id'];mysqli_stmt_bind_param($st,'i',$id);mysqli_stmt_execute($st);$extra=mysqli_fetch_assoc(mysqli_stmt_get_result($st))?:[];mysqli_stmt_close($st);}
    $lines=["\n\n--- Bukti pesanan otomatis ---",'Order: '.($order['order_id']?:$order['id']),'Sumber: '.$order['_source'],'Status: '.$order['status'],'Driver: '.$order['driver'],'Dicatat: '.date('Y-m-d H:i:s')];
    foreach($extra as $key=>$value)$lines[]=$key.': '.mb_substr((string)$value,0,500,'UTF-8');
    return implode("\n",$lines);
}
function support_save_photo(array $data):?string {
    $raw=$data['attachment_base64']??'';if($raw==='')return null;
    if(!is_string($raw)||strlen($raw)>2800000)throw new InvalidArgumentException('Bukti foto maksimal 2 MB.');
    $bytes=base64_decode($raw,true);if($bytes===false||strlen($bytes)>2*1024*1024)throw new InvalidArgumentException('Bukti foto tidak valid.');
    $info=@getimagesizefromstring($bytes);$mime=$info['mime']??'';
    if(!$info||!in_array($mime,['image/jpeg','image/png','image/webp'],true)||(int)$info[0]*(int)$info[1]>12000000)throw new InvalidArgumentException('Format bukti foto tidak valid.');
    $ext=$mime==='image/png'?'png':($mime==='image/webp'?'webp':'jpg');$dir=__DIR__.'/uploads/support';
    if(!is_dir($dir)&&!mkdir($dir,0755,true)&&!is_dir($dir))throw new RuntimeException('SUPPORT_FOLDER');
    $name=bin2hex(random_bytes(20)).'.'.$ext;
    if(file_put_contents($dir.'/'.$name,$bytes,LOCK_EX)!==strlen($bytes))throw new RuntimeException('SUPPORT_PHOTO_WRITE');
    return 'server/uploads/support/'.$name;
}
