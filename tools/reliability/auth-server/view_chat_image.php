<?php
declare(strict_types=1);
require_once __DIR__.'/chat_security_helper.php';require_once __DIR__.'/legacy_auth_bridge.php';
$auth=legacy_require_user($conn,['customer','driver']);
$file=(string)($_GET['file']??'');
if(!preg_match('#^(preview|hd)/[A-Za-z0-9_.-]+\.(jpg|jpeg|png|webp)$#i',$file))chat_out(false,'Foto tidak valid.',[],404);
$order=null;
if(preg_match('#^(preview|hd)/(customer|driver)_(ROOM-[A-Z0-9-]+)_\d{8}_\d{6}_[a-f0-9]+\.(jpg|png|webp)$#i',$file,$m))$order=chat_find_order($conn,'',chat_room($m[3]),'','');
if(!$order){
    $st=$conn->prepare("SELECT room_id FROM chat_messages WHERE LOCATE(?,message)>0 OR LOCATE(?,message)>0 ORDER BY id DESC LIMIT 1");
    $old='server/uploads/chat/'.$file;$current='view_chat_image.php?file='.rawurlencode($file);$st->bind_param('ss',$old,$current);$st->execute();$row=$st->get_result()->fetch_assoc();
    if($row)$order=chat_find_order($conn,'',chat_room($row['room_id']),'','');
}
if(!$order)chat_out(false,'Percakapan tidak ditemukan.',[],404);chat_authorize($conn,$auth,$order);
$root=realpath(__DIR__.'/uploads/chat');$path=realpath(__DIR__.'/uploads/chat/'.$file);
if(!$root || !$path || strpos($path,$root.DIRECTORY_SEPARATOR)!==0 || !is_file($path))chat_out(false,'Foto tidak ditemukan.',[],404);
$mime=(new finfo(FILEINFO_MIME_TYPE))->file($path);if(!in_array($mime,['image/jpeg','image/png','image/webp'],true))chat_out(false,'Format foto tidak valid.',[],404);
header('Content-Type: '.$mime);header('Content-Length: '.filesize($path));header('Cache-Control: private, no-store');header('X-Content-Type-Options: nosniff');readfile($path);
