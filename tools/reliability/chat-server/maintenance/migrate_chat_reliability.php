<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require dirname(__DIR__).'/config.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try {
    $rows=$conn->query("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_messages'");
    $row=$rows->fetch_assoc();
    if(!$row||strcasecmp((string)$row['ENGINE'],'InnoDB')!==0)throw new RuntimeException('chat_messages harus tersedia dengan ENGINE=InnoDB.');
    $sql=file_get_contents(__DIR__.'/chat_reliability_schema.sql');$conn->query($sql);
    // Optional read index: migration is repeatable; no ALTER runs in public requests.
    $column=$conn->query("SHOW COLUMNS FROM chat_messages LIKE 'room_key'");
    if($column->num_rows===0)$conn->query("ALTER TABLE chat_messages ADD COLUMN room_key VARCHAR(191) GENERATED ALWAYS AS (UPPER(REPLACE(room_id,'_','-'))) STORED");
    $index=$conn->query("SHOW INDEX FROM chat_messages WHERE Key_name='idx_chat_room_key_id'");
    if($index->num_rows===0)$conn->query('ALTER TABLE chat_messages ADD INDEX idx_chat_room_key_id(room_key,id)');
    require dirname(__DIR__).'/chat_delivery_helper.php';chat_delivery_require_storage($conn);
    echo "PASS chat receipt storage and room pagination index ready\n";
}catch(Throwable $error){fwrite(STDERR,"Chat migration failed: ".$error->getMessage()."\n");exit(1);}
