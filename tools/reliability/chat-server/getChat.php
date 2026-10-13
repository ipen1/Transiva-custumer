<?php
declare(strict_types=1);
error_reporting(0);ini_set('display_errors','0');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Device-UUID');header('Access-Control-Allow-Methods: GET, OPTIONS');
if(($_SERVER['REQUEST_METHOD']??'')==='OPTIONS')exit;
require_once __DIR__.'/config.php';require_once __DIR__.'/chat_security_helper.php';require_once __DIR__.'/chat_page_helper.php';
$auth=native_require_user($conn);chat_bootstrap($conn);
$room=chat_room((string)($_GET['room_id']??''));$public=trim((string)($_GET['order_id']??''));$db=trim((string)($_GET['order_db_id']??''));$source=trim((string)($_GET['source']??$_GET['order_source']??'orders'));$last=max(0,(int)($_GET['last_id']??0));$mark=(int)($_GET['mark_read']??0)===1;$through=max(0,(int)($_GET['read_through_id']??0));$readSource=trim((string)($_GET['read_source']??''));$visibleMs=max(0,(int)($_GET['visible_ms']??0));
if($room===''&&$public!=='')$room=chat_room($public);if($public===''&&$room!=='')$public=preg_replace('/^ROOM-/','',$room);
$order=chat_find_order($conn,$source,$room,$public,$db);if(!$order)chat_out(false,'Order chat tidak ditemukan.',[],404,'ORDER_NOT_FOUND');$viewer=chat_authorize($conn,$auth,$order);
$dbRoom=chat_room((string)($order['room_id']??''));if($dbRoom!=='')$room=$dbRoom;if($room===''){$canonical=(string)($order['order_id']??$order['id']);$room=chat_room($canonical);}
if($mark){if($readSource!=='chat_room_foreground_v2'||$visibleMs<1200){chat_out(false,'Read receipt ditolak: chat belum terbukti terbuka di foreground.',['room_id'=>$room],409,'READ_NOT_VISIBLE');}$other=$viewer==='customer'?'driver':'customer';$limit=$through>0?$through:PHP_INT_MAX;$m=mysqli_prepare($conn,'UPDATE chat_messages SET is_read=1,read_at=COALESCE(read_at,NOW()) WHERE UPPER(REPLACE(room_id,\'_\',\'-\'))=? AND LOWER(sender_type)=? AND id<=? AND is_read=0');if($m){mysqli_stmt_bind_param($m,'ssi',$room,$other,$limit);mysqli_stmt_execute($m);mysqli_stmt_close($m);}}
try {$page=chat_page_read($conn,$room,$_GET);}
catch(InvalidArgumentException $error){chat_out(false,$error->getMessage(),[],422,'CHAT_PAGE_INVALID');}
catch(Throwable $error){error_log('CHAT_READ_FAILED');chat_out(false,'Chat belum dapat dibaca. Coba kembali.',[],503,'CHAT_READ_FAILED');}
$status=(string)($order['status']??'');chat_out(true,'Chat berhasil dimuat.',array_merge($page,['room_id'=>$room,'status'=>$status,'ended'=>chat_ended($status),'viewer_type'=>$viewer]));
