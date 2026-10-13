<?php
declare(strict_types=1);
/** SQL page reader. New clients opt in; legacy last_id clients keep their old contract. */
function chat_page_read(mysqli $conn,string $room,array $input): array {
    $mode=(string)($input['page_mode']??'legacy');
    if(!in_array($mode,['latest','older','newer','legacy'],true))throw new InvalidArgumentException('Mode halaman tidak valid.');
    $limit=$mode==='legacy'?200:max(1,min(100,(int)($input['limit']??60)));
    $cursor=$mode==='older'?max(0,(int)($input['before_id']??0)):max(0,(int)($input['last_id']??0));
    if($mode==='older'&&$cursor<=0)throw new InvalidArgumentException('Cursor riwayat tidak valid.');
    $roomColumn=native_column_exists($conn,'chat_messages','room_key')?'room_key':"UPPER(REPLACE(room_id,'_','-'))";
    $where="$roomColumn=?";$types='s';$values=[$room];
    if($mode!=='latest'){$where.=$mode==='older'?' AND id<?':' AND id>?';$types.='i';$values[]=$cursor;}
    $descending=$mode==='latest'||$mode==='older';$take=$limit+1;
    $sql='SELECT id,room_id,sender_type,message,created_at,is_read,read_at FROM chat_messages WHERE '.$where.' ORDER BY id '.($descending?'DESC':'ASC').' LIMIT '.$take;
    $stmt=mysqli_prepare($conn,$sql);if(!$stmt)throw new RuntimeException('Pembacaan chat gagal.');
    mysqli_stmt_bind_param($stmt,$types,...$values);
    if(!mysqli_stmt_execute($stmt))throw new RuntimeException('Pembacaan chat gagal.');
    $result=mysqli_stmt_get_result($stmt);$rows=[];
    while($row=mysqli_fetch_assoc($result)){$row['id']=(int)$row['id'];$row['is_read']=(int)$row['is_read'];$rows[]=$row;}
    mysqli_stmt_close($stmt);$more=count($rows)>$limit;if($more)array_pop($rows);if($descending)$rows=array_reverse($rows);
    $newest=$cursor;$oldest=0;
    foreach($rows as $row){$newest=max($newest,$row['id']);$oldest=$oldest===0?$row['id']:min($oldest,$row['id']);}
    if($mode==='latest')$newest=$rows?max(array_column($rows,'id')):0;
    $receipts=[];
    $ids=array_values(array_unique(array_filter(array_map('intval',explode(',',substr((string)($input['receipt_ids']??''),0,2000))),static fn($id)=>$id>0)));
    $ids=array_slice($ids,0,100);
    if($ids) {
        // IDs are strictly integers; room authorization still scopes every receipt read.
        $stmt=mysqli_prepare($conn,'SELECT id,sender_type,created_at,is_read,read_at FROM chat_messages WHERE '.$roomColumn.'=? AND id IN ('.implode(',',$ids).')');
        if(!$stmt)throw new RuntimeException('Pembacaan receipt gagal.');mysqli_stmt_bind_param($stmt,'s',$room);
        if(!mysqli_stmt_execute($stmt))throw new RuntimeException('Pembacaan receipt gagal.');
        $result=mysqli_stmt_get_result($stmt);while($row=mysqli_fetch_assoc($result)){$row['id']=(int)$row['id'];$row['is_read']=(int)$row['is_read'];$receipts[]=$row;}mysqli_stmt_close($stmt);
    }
    return ['messages'=>$rows,'last_id'=>$newest,'oldest_id'=>$oldest,'page_mode'=>$mode,
        'has_more'=>$more,'has_more_older'=>$descending&&$more,'receipt_updates'=>$receipts];
}
