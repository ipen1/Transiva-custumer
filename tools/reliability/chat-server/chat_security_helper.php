<?php
declare(strict_types=1);
require_once __DIR__.'/native_auth.php';

function chat_out(bool $success,string $message,array $extra=[],int $status=200,string $code=''):void{
    http_response_code($status);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');
    echo json_encode(array_merge(['success'=>$success,'message'=>$message,'code'=>$code],$extra),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    exit;
}
function chat_room(string $v):string{
    $v=strtoupper(trim($v)); $v=str_replace('_','-',$v); $v=preg_replace('/[^A-Z0-9\-]/','',$v)??'';
    return $v!=='' && strpos($v,'ROOM-')!==0 ? 'ROOM-'.$v : $v;
}
function chat_bootstrap(mysqli $c): void {
    // Schema chat sudah dipasang melalui migrasi/deployment. Tidak ada DDL saat request runtime.
}

function chat_find_order(mysqli $c,string $source,string $room,string $public,string $db):?array{
    $tables=$source==='pickup_orders'?['pickup_orders','orders']:['orders','pickup_orders'];
    foreach($tables as $t){
      if(!native_table_exists($c,$t)) continue;
      $driver=native_column_exists($c,$t,'driver')?'driver':(native_column_exists($c,$t,'driver_username')?'driver_username':'');
      if($driver==='') continue;
      $roomCol=native_column_exists($c,$t,'room_id')?'room_id':'';
      $where=[];$types='';$vals=[];
      if($public!==''){ $where[]='order_id=?';$types.='s';$vals[]=$public; }
      if($db!=='' && ctype_digit($db)){ $where[]='id=?';$types.='i';$vals[]=(int)$db; }
      if($room!=='' && $roomCol!==''){ $where[]="UPPER(REPLACE(`{$roomCol}`,'_','-'))=?";$types.='s';$vals[]=$room; }
      $plain=preg_replace('/^ROOM-/','',$room);
      if($plain!=='' && $plain!==$public){ $where[]='order_id=?';$types.='s';$vals[]=$plain; }
      if(!$where) continue;
      $roomSelect=$roomCol!==''?"`{$roomCol}`":"''";
      $sql="SELECT id,user_id,order_id,status,`{$driver}` AS driver,{$roomSelect} AS room_id FROM `{$t}` WHERE ".implode(' OR ',$where).' ORDER BY id DESC LIMIT 1';
      $st=mysqli_prepare($c,$sql); if(!$st) continue;
      $refs=[];$refs[]=$types;foreach($vals as $k=>$v)$refs[]=&$vals[$k];mysqli_stmt_bind_param($st,...$refs);
      mysqli_stmt_execute($st);$rs=mysqli_stmt_get_result($st);$row=$rs?mysqli_fetch_assoc($rs):null;mysqli_stmt_close($st);
      if($row){$row['_source']=$t;return $row;}
    }
    return null;
}
function chat_authorize(mysqli $c,array $auth,array $order):string{
    $role=strtolower(trim((string)($auth['role']??'')));
    if($role==='customer'){
      if((int)($order['user_id']??0)!==(int)($auth['id']??0)) chat_out(false,'Order ini bukan milik akun Anda.',[],403,'CHAT_FORBIDDEN');
      return 'customer';
    }
    if($role==='driver'){
      $assigned=trim((string)($order['driver']??''));$username=trim((string)($auth['username']??''));
      if($assigned==='' || strcasecmp($assigned,$username)!==0) chat_out(false,'Order ini tidak ditugaskan kepada akun driver Anda.',[],403,'CHAT_FORBIDDEN');
      return 'driver';
    }
    chat_out(false,'Role tidak diizinkan menggunakan chat.',[],403,'CHAT_ROLE');
}
function chat_ended(string $s):bool{return in_array(strtolower(trim($s)),['finished','finish','completed','complete','done','delivered','canceled','cancelled','rejected','merchant_rejected','expired'],true);}
