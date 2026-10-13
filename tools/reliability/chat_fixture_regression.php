<?php
// Controlled database collaborator: runs actual production helpers without a live DB.
if(PHP_SAPI!=='cli')exit(1);
class mysqli {public array $messages=[],$receipts=[];public int $last=0;public bool $failInsert=false,$ambiguousCommit=false;public ?array $snapshot=null;}
class mysqli_stmt {public mysqli $conn;public string $sql;public array $params=[],$rows=[],$refs=[];public int $at=0,$affected=0;function __construct($c,$s){$this->conn=$c;$this->sql=$s;}}
class ChatFixtureResult {public array $rows;function __construct($r){$this->rows=$r;}}
function mysqli_prepare($c,$s){return new mysqli_stmt($c,$s);}
function mysqli_stmt_bind_param($s,$types,&...$values){foreach($values as &$v)$s->params[]=&$v;return true;}
function mysqli_stmt_bind_result($s,&...$values){foreach($values as &$v)$s->refs[]=&$v;return true;}
function mysqli_stmt_execute($s){
 $c=$s->conn;$q=$s->sql;$p=$s->params;$s->rows=[];$s->affected=0;
 if(str_starts_with($q,'INSERT INTO chat_message_receipts')){$key=$p[0].':'.$p[1];if(!isset($c->receipts[$key])){$c->receipts[$key]=['request_hash'=>$p[2],'message_id'=>null];$s->affected=1;}}
 elseif(str_starts_with($q,'SELECT request_hash,message_id')){if(isset($c->receipts[$p[0].':'.$p[1]]))$s->rows[]=array_values($c->receipts[$p[0].':'.$p[1]]);}
 elseif(str_starts_with($q,'INSERT INTO chat_messages')){if($c->failInsert)throw new RuntimeException('injected insert failure');$id=++$c->last;$c->messages[$id]=['id'=>$id,'room_id'=>$p[0],'sender_type'=>$p[1],'message'=>$p[2],'created_at'=>'2026-10-13 00:00:00','is_read'=>0,'read_at'=>null];$s->affected=1;}
 elseif(str_starts_with($q,'UPDATE chat_message_receipts')){$c->receipts[$p[1].':'.$p[2]]['message_id']=$p[0];$s->affected=1;}
 elseif(str_starts_with($q,'SELECT id,room_id')){
  $rows=array_values(array_filter($c->messages,static function($r)use($p,$q){return $r['room_id']===$p[0]&&(!isset($p[1])||(str_contains($q,'id<?')?$r['id']<$p[1]:$r['id']>$p[1]));}));
  usort($rows,static fn($a,$b)=>str_contains($q,'DESC')?$b['id']<=>$a['id']:$a['id']<=>$b['id']);preg_match('/LIMIT (\d+)/',$q,$m);$s->rows=array_slice($rows,0,(int)$m[1]);
 }
 elseif(str_starts_with($q,'SELECT id,sender_type')){preg_match('/id IN \(([^)]+)\)/',$q,$m);$ids=array_map('intval',explode(',',$m[1]));foreach($ids as $id){$row=$c->messages[$id]??null;if($row&&$row['room_id']===$p[0])$s->rows[]=array_intersect_key($row,array_flip(['id','sender_type','created_at','is_read','read_at']));}}
 else throw new RuntimeException('Unexpected fixture query '.$q);
 return true;
}
function mysqli_stmt_fetch($s){if(!isset($s->rows[$s->at]))return null;$row=array_values($s->rows[$s->at++]);foreach($s->refs as $i=>&$value)$value=$row[$i];return true;}
function mysqli_stmt_get_result($s){return new ChatFixtureResult($s->rows);}
function mysqli_fetch_assoc($r){return array_shift($r->rows);}
function mysqli_stmt_close($s){}
function mysqli_stmt_affected_rows($s){return $s->affected;}
function mysqli_insert_id($c){return $c->last;}
function mysqli_query($c,$q){if(str_contains($q,'information_schema.STATISTICS'))return new ChatFixtureResult([['columns_list'=>'user_id,client_message_id']]);return new ChatFixtureResult([['TABLE_NAME'=>'chat_messages','ENGINE'=>'InnoDB'],['TABLE_NAME'=>'chat_message_receipts','ENGINE'=>'InnoDB']]);}
function mysqli_free_result($r){}
function mysqli_begin_transaction($c){$c->snapshot=[$c->messages,$c->receipts,$c->last];return true;}
function mysqli_commit($c){$c->snapshot=null;if($c->ambiguousCommit){$c->ambiguousCommit=false;throw new RuntimeException('response lost after commit');}return true;}
function mysqli_rollback($c){if($c->snapshot)[$c->messages,$c->receipts,$c->last]=$c->snapshot;$c->snapshot=null;return true;}
function native_column_exists($c,$table,$name){return true;}
require ($argv[1]??__DIR__.'/chat-server').'/chat_delivery_helper.php';
require ($argv[1]??__DIR__.'/chat-server').'/chat_page_helper.php';
$checks=0;function check_chat($ok,$name){global $checks;if(!$ok)throw new RuntimeException($name);$checks++;echo "PASS $name\n";}
$c=new mysqli();for($i=1;$i<=425;$i++)chat_delivery_send($c,1,'','ROOM-A','driver','message-'.$i);
$page=chat_page_read($c,'ROOM-A',['page_mode'=>'latest','limit'=>60]);
check_chat(array_column($page['messages'],'id')===range(366,425)&&$page['has_more_older'],'latest page opens beyond 200 messages');
$seen=array_column($page['messages'],'id');$oldest=366;
while($page['has_more_older']){$page=chat_page_read($c,'ROOM-A',['page_mode'=>'older','before_id'=>$oldest,'limit'=>60]);$ids=array_column($page['messages'],'id');check_chat(!array_intersect($seen,$ids),'history page never overlaps');$seen=array_merge($seen,$ids);$oldest=min($ids);}
sort($seen);check_chat($seen===range(1,425),'all 425 messages reachable through older pages');
for($i=0;$i<130;$i++)chat_delivery_send($c,1,'','ROOM-A','driver','new');
$page=chat_page_read($c,'ROOM-A',['page_mode'=>'newer','last_id'=>425,'limit'=>60]);check_chat(array_column($page['messages'],'id')===range(426,485)&&$page['has_more'],'newer backlog uses forward cursor');
check_chat(array_column(chat_page_read($c,'ROOM-A',['last_id'=>200])['messages'],'id')===range(201,400),'legacy driver contract preserved');
$c->messages[10]['is_read']=1;$page=chat_page_read($c,'ROOM-A',['page_mode'=>'newer','last_id'=>555,'receipt_ids'=>'10,9999']);check_chat(!$page['messages']&&$page['receipt_updates'][0]['is_read']===1,'receipt updates independent of message cursor');
$id='ccm-fixture-stable-001';$first=chat_delivery_send($c,1,$id,'ROOM-A','customer','hello');$second=chat_delivery_send($c,1,$id,'ROOM-A','customer','hello');check_chat($first['id']===$second['id']&&$second['replayed'],'retry reuses one persisted message');
$blocked=false;try{chat_delivery_send($c,1,$id,'ROOM-A','customer','edited');}catch(InvalidArgumentException $expected){$blocked=true;}check_chat($blocked,'same ID rejects changed payload');
$blocked=false;try{chat_delivery_send($c,1,$id,'ROOM-B','customer','hello');}catch(InvalidArgumentException $expected){$blocked=true;}check_chat($blocked,'same ID cannot move message to another room');
$other=chat_delivery_send($c,2,$id,'ROOM-A','customer','hello');check_chat($other['id']!==$first['id'],'client IDs are isolated per authenticated account');
$c->failInsert=true;$before=count($c->messages);try{chat_delivery_send($c,1,'ccm-fixture-rollback-001','ROOM-A','customer','retry');}catch(RuntimeException $expected){}$c->failInsert=false;check_chat(count($c->messages)===$before&&!isset($c->receipts['1:ccm-fixture-rollback-001']),'insert failure rolls back reservation and message');
$c->ambiguousCommit=true;try{chat_delivery_send($c,1,'ccm-fixture-ambiguous-001','ROOM-A','customer','ambiguous');}catch(RuntimeException $expected){}
$recovered=chat_delivery_send($c,1,'ccm-fixture-ambiguous-001','ROOM-A','customer','ambiguous');check_chat($recovered['replayed'],'lost response after commit recovered with same ID');
check_chat(chat_page_read($c,'ROOM-B',['page_mode'=>'latest'])['messages']===[],'message page stays scoped to its room');
echo "PASS $checks chat helper fixture scenarios\n";
