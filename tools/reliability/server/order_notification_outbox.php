<?php
declare(strict_types=1);
function order_queue_notification(mysqli $conn,string $event,string $kind,array $payload): void {
    $json=json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    $st=mysqli_prepare($conn,'INSERT INTO order_notification_outbox(event_key,kind,payload_json) VALUES(?,?,?)');
    mysqli_stmt_bind_param($st,'sss',$event,$kind,$json);if(!mysqli_stmt_execute($st)){mysqli_stmt_close($st);throw new RuntimeException('OUTBOX_WRITE_FAILED');}mysqli_stmt_close($st);
}
