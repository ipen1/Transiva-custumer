<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');

header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/p0_security_helper.php';
$__p0auth=p0_require_customer($conn);

function outc(bool $ok,string $message,array $extra=[]):void{
  echo json_encode(array_merge(['success'=>$ok,'message'=>$message,'server_time'=>date('Y-m-d H:i:s')],$extra),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);exit;
}
function tablec(mysqli $c,string $t):bool{$t=mysqli_real_escape_string($c,$t);$q=mysqli_query($c,"SHOW TABLES LIKE '$t'");return $q&&mysqli_num_rows($q)>0;}
function colc(mysqli $c,string $t,string $n):bool{$t=mysqli_real_escape_string($c,$t);$n=mysqli_real_escape_string($c,$n);$q=mysqli_query($c,"SHOW COLUMNS FROM `$t` LIKE '$n'");return $q&&mysqli_num_rows($q)>0;}
function roomc($v):string{$v=strtoupper(trim((string)$v));$v=str_replace('_','-',$v);$v=preg_replace('/[^A-Z0-9\-]/','',$v)??'';if($v!==''&&strpos($v,'ROOM-')!==0)$v='ROOM-'.$v;return $v;}
function endedc($s):bool{return in_array(strtolower(trim((string)$s)),['finished','finish','completed','complete','cancelled','canceled','customer_cancelled','merchant_rejected'],true);}
function servicec(array $r,string $t):string{if($t==='pickup_orders')return 'TransPickup';$v=strtolower(trim((string)($r['order_type']??$r['service_type']??$r['type']??'')));if(strpos($v,'food')!==false||!empty($r['restaurant_id'])||!empty($r['merchant_id']))return 'TransFood';if(strpos($v,'car')!==false||strpos($v,'mobil')!==false)return 'TransCar';return 'TransRide';}

$userId=(int)$__p0auth["id"];if($userId<=0)outc(false,'User ID tidak valid',['conversations'=>[],'total'=>0]);
$list=[];
$hasChat=tablec($conn,'chat_messages');
$roomColumn=$hasChat&&colc($conn,'chat_messages','room_key')?'room_key':"UPPER(REPLACE(room_id,'_','-'))";
foreach(['orders','pickup_orders'] as $table){
 if(!tablec($conn,$table)||!colc($conn,$table,'user_id'))continue;
 $st=mysqli_prepare($conn,"SELECT * FROM `$table` WHERE user_id=? ORDER BY id DESC LIMIT 100");if(!$st)continue;mysqli_stmt_bind_param($st,'i',$userId);mysqli_stmt_execute($st);$rs=mysqli_stmt_get_result($st);
 while($rs&&($r=mysqli_fetch_assoc($rs))){
  $id=(int)($r['id']??0);if($id<=0)continue;$public=trim((string)($r['order_id']??''));if($public==='')$public=(string)$id;
  $rawRoom=trim((string)($r['room_id']??$r['chat_room_id']??''));$room=roomc($rawRoom!==''?$rawRoom:$public);
  $status=trim((string)($r['status']??''));$driver=trim((string)($r['driver']??$r['driver_username']??$r['assigned_driver']??''));
  $last='';$sender='';$at=trim((string)($r['updated_at']??$r['created_at']??''));

  $list[]=['id'=>$id,'order_db_id'=>$id,'order_id'=>$public,'source'=>$table,'room_id'=>$room,'participant_name'=>$driver!==''?$driver:'Driver','driver'=>$driver,'order_type'=>servicec($r,$table),'status'=>$status,'is_history'=>endedc($status),'chat_available'=>!endedc($status)&&$driver!=='','last_message'=>$last,'last_sender_type'=>$sender,'last_message_is_mine'=>strtolower(trim($sender))==='customer','last_message_at'=>$at,'created_at'=>(string)($r['created_at']??'')];
 }
 mysqli_stmt_close($st);
}
// One batched last-message lookup for all rooms, rather than one query per order.
if($hasChat&&$list){
 $rooms=array_values(array_unique(array_column($list,'room_id')));
 $placeholders=implode(',',array_fill(0,count($rooms),'?'));
 $sql="SELECT cm.room_id,cm.sender_type,cm.message,cm.created_at FROM chat_messages cm JOIN (SELECT MAX(id) AS id FROM chat_messages WHERE $roomColumn IN ($placeholders) GROUP BY $roomColumn) latest ON latest.id=cm.id";
 $stmt=mysqli_prepare($conn,$sql);
 if(!$stmt)outc(false,'Daftar pesan belum dapat diperbarui.');
 mysqli_stmt_bind_param($stmt,str_repeat('s',count($rooms)),...$rooms);
 if(!mysqli_stmt_execute($stmt)){mysqli_stmt_close($stmt);outc(false,'Daftar pesan belum dapat diperbarui.');}
 $result=mysqli_stmt_get_result($stmt);$latest=[];
 while($row=mysqli_fetch_assoc($result))$latest[roomc($row['room_id'])]=$row;
 mysqli_stmt_close($stmt);
 foreach($list as &$item){$message=$latest[$item['room_id']]??null;if($message){$item['last_message']=(string)$message['message'];$item['last_sender_type']=(string)$message['sender_type'];$item['last_message_is_mine']=strtolower((string)$message['sender_type'])==='customer';$item['last_message_at']=(string)$message['created_at'];}}unset($item);
}
usort($list,static fn($a,$b)=>strcmp((string)($b['last_message_at']??''),(string)($a['last_message_at']??'')));
outc(true,'Daftar percakapan customer berhasil dimuat.',['conversations'=>$list,'total'=>count($list)]);
