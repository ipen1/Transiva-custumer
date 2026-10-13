<?php
/** Resolve mutable FCM tokens in the worker, outside the money transaction. */
function customer_wallet_deliver_notification($conn, array $payload, ?callable $sender=null): string {
    $userId=(int)($payload['user_id']??0);
    if($userId<=0)throw new RuntimeException('WALLET_NOTIFICATION_INVALID');
    $stmt=mysqli_prepare($conn,'SELECT fcm_token FROM users WHERE id=? LIMIT 1');
    if(!$stmt)throw new RuntimeException('WALLET_TOKEN_LOOKUP_FAILED');
    mysqli_stmt_bind_param($stmt,'i',$userId);
    if(!mysqli_stmt_execute($stmt)){mysqli_stmt_close($stmt);throw new RuntimeException('WALLET_TOKEN_LOOKUP_FAILED');}
    mysqli_stmt_bind_result($stmt,$token);$found=mysqli_stmt_fetch($stmt);mysqli_stmt_close($stmt);
    if($found===false)throw new RuntimeException('WALLET_TOKEN_LOOKUP_FAILED');
    if($found===null||trim((string)$token)==='')return 'unavailable';
    $send=$sender??'send_fcm_notification';
    $result=$send((string)$token,(string)($payload['title']??''),(string)($payload['body']??''),(array)($payload['data']??[]));
    if(!is_array($result)||empty($result['success']))throw new RuntimeException('FCM_DELIVERY_FAILED');
    return 'sent';
}
