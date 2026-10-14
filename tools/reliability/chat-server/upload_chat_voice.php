<?php
error_reporting(0);
ini_set('display_errors', 0);
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Content-Type: application/json; charset=UTF-8');
if($_SERVER['REQUEST_METHOD']==='OPTIONS') exit;
require_once __DIR__.'/config.php';
require_once __DIR__.'/native_auth.php';
$auth=native_require_user($conn);

function out($data, $code=200){ http_response_code($code); echo json_encode($data, JSON_UNESCAPED_UNICODE); exit; }
function clean_room($value){
    $value=strtoupper(trim((string)$value));
    $value=str_replace('_','-',$value);
    $value=preg_replace('/[^A-Z0-9\-]/','',$value);
    if($value!=='' && strpos($value,'ROOM-')!==0) $value='ROOM-'.$value;
    return $value;
}

// Old driver versions upload audio first, then call sendChat. Preserve that contract.
// New clients provide operation_id and commit the voice message with its receipt/outbox.
if(($_SERVER['REQUEST_METHOD']??'')==='POST' && trim((string)($_POST['operation_id']??''))!==''){
 require_once __DIR__.'/chat_media_helper.php';
 [$order,$room,$sender]=chat_media_context($conn,$auth);
 try{$response=chat_media_send($conn,$auth,$order,$room,$sender,'voice',[chat_media_file('audio',12*1024*1024,false)],max(0,min(600000,(int)($_POST['duration_ms']??0))));out($response);}
 catch(InvalidArgumentException $e){out(['success'=>false,'message'=>$e->getMessage(),'code'=>'CHAT_ID_CONFLICT'],409);}
 catch(DomainException $e){out(['success'=>false,'message'=>$e->getMessage(),'code'=>'CHAT_ENDED'],409);}
 catch(Throwable $e){error_log('CHAT_VOICE_UNCONFIRMED');out(['success'=>false,'message'=>'Voice belum terkonfirmasi. Kirim ulang dengan ID yang sama.','code'=>'CHAT_SEND_UNCONFIRMED'],503);}
}
if($_SERVER['REQUEST_METHOD']!=='POST') out(['success'=>false,'message'=>'Method tidak didukung'],405);
$room=clean_room($_POST['room_id'] ?? '');
$sender=strtolower(trim((string)($_POST['sender_type'] ?? '')));
$duration=max(0,intval($_POST['duration_ms'] ?? 0));
if($room==='' || !in_array($sender,['customer','driver'],true)) out(['success'=>false,'message'=>'Data voice note tidak lengkap'],400);
require_once __DIR__.'/chat_security_helper.php';
$order=chat_find_order($conn,'',$room,'','');if(!$order)out(['success'=>false,'message'=>'Percakapan tidak ditemukan'],404);
$sender=chat_authorize($conn,$auth,$order);
if(chat_ended((string)$order['status']))out(['success'=>false,'message'=>'Percakapan sudah selesai'],409);
if(!isset($_FILES['audio']) || !is_uploaded_file($_FILES['audio']['tmp_name'])) out(['success'=>false,'message'=>'File audio tidak ditemukan'],400);
$file=$_FILES['audio'];
if(($file['error'] ?? UPLOAD_ERR_OK)!==UPLOAD_ERR_OK) out(['success'=>false,'message'=>'Upload audio gagal'],400);
if(($file['size'] ?? 0)<=0 || $file['size']>12*1024*1024) out(['success'=>false,'message'=>'Ukuran voice note maksimal 12 MB'],400);

$finfo=function_exists('finfo_open') ? finfo_open(FILEINFO_MIME_TYPE) : false;
$mime=$finfo ? (string)finfo_file($finfo,$file['tmp_name']) : 'audio/mp4';
if($finfo) finfo_close($finfo);
$allowed=['audio/mp4','video/mp4','audio/m4a','audio/x-m4a','application/octet-stream'];
$header=@file_get_contents($file['tmp_name'],false,null,0,12);
if(!is_string($header)||strlen($header)<12||substr($header,4,4)!=='ftyp')out(['success'=>false,'message'=>'Container audio tidak valid'],400);
if(!in_array(strtolower($mime),$allowed,true)) out(['success'=>false,'message'=>'Format audio tidak didukung'],400);

$relative='uploads/chat_voice/'.date('Y/m');
$root=dirname(__DIR__);
$dir=$root.'/'.$relative;
if(!is_dir($dir) && !@mkdir($dir,0755,true)) out(['success'=>false,'message'=>'Folder voice note tidak dapat dibuat'],500);
$name=strtolower($sender).'_'.preg_replace('/[^A-Z0-9\-]/','',$room).'_'.date('Ymd_His').'_'.bin2hex(random_bytes(5)).'.m4a';
$dest=$dir.'/'.$name;
if(!@move_uploaded_file($file['tmp_name'],$dest)) out(['success'=>false,'message'=>'Gagal menyimpan voice note'],500);
@chmod($dest,0644);
$playFile=date('Y/m').'/'.$name;
$url='https://transiva.my.id/server/play_chat_voice.php?file='.rawurlencode($playFile);
out(['success'=>true,'message'=>'Voice note terupload','url'=>$url,'audio_url'=>$url,'duration_ms'=>$duration]);
