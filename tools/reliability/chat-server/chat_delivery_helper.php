<?php
declare(strict_types=1);
require_once __DIR__ . "/chat_notification_helper.php";

function chat_delivery_hash(string $room, string $sender, string $message): string {
    return hash('sha256',json_encode([$room,$sender,$message],JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR));
}
function chat_delivery_validate_id(string $id): void {
    if($id!==''&&!preg_match('/^[A-Za-z0-9_-]{16,80}$/D',$id))
        throw new InvalidArgumentException('ID pesan tidak valid.');
}
function chat_delivery_prepare(mysqli $conn,string $sql): mysqli_stmt {
    $stmt=mysqli_prepare($conn,$sql);if(!$stmt)throw new RuntimeException('Penyimpanan pesan belum siap.');return $stmt;
}
function chat_delivery_execute(mysqli_stmt $stmt): void {
    if(!mysqli_stmt_execute($stmt))throw new RuntimeException('Pesan belum dapat disimpan.');
}
function chat_delivery_receipt(mysqli $conn,int $user,string $client,string $hash,bool $locked=false): ?int {
    $stmt=chat_delivery_prepare($conn,'SELECT request_hash,message_id FROM chat_message_receipts WHERE user_id=? AND client_message_id=?'.($locked?' FOR UPDATE':''));
    mysqli_stmt_bind_param($stmt,'is',$user,$client);chat_delivery_execute($stmt);
    mysqli_stmt_bind_result($stmt,$stored,$id);$found=mysqli_stmt_fetch($stmt);mysqli_stmt_close($stmt);
    if($found===false)throw new RuntimeException('Receipt pesan belum dapat dibaca.');
    if(!$found)return null;
    if(!hash_equals((string)$stored,$hash))throw new InvalidArgumentException('ID pesan sudah dipakai untuk isi berbeda.');
    return (int)$id>0?(int)$id:null;
}
function chat_delivery_require_storage(mysqli $conn): void {
    $result=mysqli_query($conn,"SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('chat_messages','chat_message_receipts')");
    if(!$result)throw new RuntimeException('Penyimpanan chat belum siap.');
    $count=0;while($row=mysqli_fetch_assoc($result)){if(strcasecmp((string)$row['ENGINE'],'InnoDB')!==0)throw new RuntimeException('Chat memerlukan penyimpanan InnoDB.');$count++;}
    mysqli_free_result($result);if($count!==2)throw new RuntimeException('Migrasi chat belum dipasang.');
    $result=mysqli_query($conn,"SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_list FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_message_receipts' AND NON_UNIQUE=0 GROUP BY INDEX_NAME");
    if(!$result)throw new RuntimeException('Pengaman ID chat belum siap.');
    $unique=false;while($row=mysqli_fetch_assoc($result)){$parts=explode(',',(string)$row['columns_list']);sort($parts);if($parts===['client_message_id','user_id'])$unique=true;}
    mysqli_free_result($result);if(!$unique)throw new RuntimeException('Constraint unik ID chat belum dipasang.');
}
/** Reserve a unique client ID and insert its message in the same transaction. */
function chat_delivery_send(mysqli $conn,int $user,string $client,string $room,string $sender,string $message,?array $order=null): array {
    chat_delivery_validate_id($client);
    if($order!==null)chat_notification_require_storage($conn);
    if($client==='') { // Legacy sends still receive atomic notification intents.
        if(!mysqli_begin_transaction($conn))throw new RuntimeException('CHAT_TRANSACTION');
        try{
            $stmt=chat_delivery_prepare($conn,'INSERT INTO chat_messages(room_id,sender_type,message,created_at,is_read,read_at) VALUES(?,?,?,NOW(),0,NULL)');
            mysqli_stmt_bind_param($stmt,'sss',$room,$sender,$message);chat_delivery_execute($stmt);
            $id=(int)mysqli_insert_id($conn);mysqli_stmt_close($stmt);
            if($order!==null)chat_notification_enqueue($conn,$order,$room,$sender,$id,$message);
            if(!mysqli_commit($conn))throw new RuntimeException('CHAT_COMMIT');return ['id'=>$id,'replayed'=>false];
        }catch(Throwable $e){mysqli_rollback($conn);throw $e;}
    }
    chat_delivery_require_storage($conn);$hash=chat_delivery_hash($room,$sender,$message);
    if(!mysqli_begin_transaction($conn))throw new RuntimeException('Transaksi chat belum dimulai.');
    try {
        $stmt=chat_delivery_prepare($conn,'INSERT INTO chat_message_receipts(user_id,client_message_id,request_hash) VALUES(?,?,?) ON DUPLICATE KEY UPDATE client_message_id=client_message_id');
        mysqli_stmt_bind_param($stmt,'iss',$user,$client,$hash);chat_delivery_execute($stmt);mysqli_stmt_close($stmt);
        $id=chat_delivery_receipt($conn,$user,$client,$hash,true);
        $replayed=$id!==null;
        if(!$replayed) {
            $stmt=chat_delivery_prepare($conn,'INSERT INTO chat_messages(room_id,sender_type,message,created_at,is_read,read_at) VALUES(?,?,?,NOW(),0,NULL)');
            mysqli_stmt_bind_param($stmt,'sss',$room,$sender,$message);chat_delivery_execute($stmt);$id=(int)mysqli_insert_id($conn);mysqli_stmt_close($stmt);
            $stmt=chat_delivery_prepare($conn,'UPDATE chat_message_receipts SET message_id=? WHERE user_id=? AND client_message_id=?');
            mysqli_stmt_bind_param($stmt,'iis',$id,$user,$client);chat_delivery_execute($stmt);
            if(mysqli_stmt_affected_rows($stmt)!==1)throw new RuntimeException('Receipt pesan gagal disimpan.');mysqli_stmt_close($stmt);
        }
        if(!$replayed&&$order!==null)chat_notification_enqueue($conn,$order,$room,$sender,$id,$message);
        if(!mysqli_commit($conn))throw new RuntimeException('Hasil pesan belum terkonfirmasi.');
        return ['id'=>$id,'replayed'=>$replayed];
    }catch(Throwable $error){try{mysqli_rollback($conn);}catch(Throwable $ignored){}throw $error;}
}
