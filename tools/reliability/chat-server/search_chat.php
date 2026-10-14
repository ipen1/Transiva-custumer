<?php
declare(strict_types=1);
require_once __DIR__.'/config.php';require_once __DIR__.'/chat_security_helper.php';
$auth=native_require_user($conn);
$order=chat_find_order($conn,(string)($_GET['source']??'orders'),chat_room((string)($_GET['room_id']??'')),(string)($_GET['order_id']??''),(string)($_GET['order_db_id']??''));
if(!$order)chat_out(false,'Percakapan tidak ditemukan.',[],404,'ORDER_NOT_FOUND');chat_authorize($conn,$auth,$order);
$room=chat_room((string)($order['room_id']?:($order['order_id']?:$order['id'])));
$roomColumn=native_column_exists($conn,'chat_messages','room_key')?'room_key':"UPPER(REPLACE(room_id,'_','-'))";
$target=max(0,(int)($_GET['target_id']??0));
if($target){
 $st=mysqli_prepare($conn,"SELECT id FROM chat_messages WHERE {$roomColumn}=? AND id=?");mysqli_stmt_bind_param($st,'si',$room,$target);mysqli_stmt_execute($st);$found=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
 if(!$found)chat_out(false,'Pesan tidak ditemukan dalam percakapan ini.',[],404);
 $st=mysqli_prepare($conn,"SELECT id,room_id,sender_type,message,created_at,is_read,read_at FROM chat_messages WHERE {$roomColumn}=? AND id<=? ORDER BY id DESC LIMIT 31");mysqli_stmt_bind_param($st,'si',$room,$target);mysqli_stmt_execute($st);$rows=array_reverse(mysqli_fetch_all(mysqli_stmt_get_result($st),MYSQLI_ASSOC));mysqli_stmt_close($st);
 $oldest=(int)$rows[0]['id'];$st=mysqli_prepare($conn,"SELECT id FROM chat_messages WHERE {$roomColumn}=? AND id<? LIMIT 1");mysqli_stmt_bind_param($st,'si',$room,$oldest);mysqli_stmt_execute($st);$older=(bool)mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
 chat_out(true,'Pesan ditemukan.',['messages'=>$rows,'status'=>$order['status'],'page_mode'=>'latest','has_more_older'=>$older,'target_id'=>$target]);
}
$query=trim((string)($_GET['q']??''));if(mb_strlen($query)<2||mb_strlen($query)>100)chat_out(false,'Ketik 2–100 karakter.',[],422);
$before=max(0,(int)($_GET['before_id']??0));$pattern='%'.str_replace(['!','%','_'],['!!','!%','!_'],$query).'%';
$st=mysqli_prepare($conn,"SELECT id,sender_type,message,created_at FROM chat_messages WHERE {$roomColumn}=? AND message LIKE ? ESCAPE '!' AND (?=0 OR id<?) ORDER BY id DESC LIMIT 51");
mysqli_stmt_bind_param($st,'ssii',$room,$pattern,$before,$before);mysqli_stmt_execute($st);$rows=mysqli_fetch_all(mysqli_stmt_get_result($st),MYSQLI_ASSOC);mysqli_stmt_close($st);$more=count($rows)>50;if($more)array_pop($rows);
chat_out(true,'Hasil pencarian.',['results'=>$rows,'has_more'=>$more,'next_before_id'=>$rows?(int)$rows[count($rows)-1]['id']:0]);
