<?php
declare(strict_types=1);
require_once __DIR__.'/customer_email_helper.php';mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$auth=ce_user($conn);$id=(int)$auth['id'];$hash='';
try{
    $conn->begin_transaction();$u=ce_lock($conn,$id);$email=strtolower(trim($u['email']));
    if(!filter_var($email,FILTER_VALIDATE_EMAIL)){$conn->rollback();native_out(false,'Email akun tidak valid.',[],422);}
    if((int)$u['email_verified']===1){$conn->commit();native_out(true,'Email sudah terverifikasi.',['email_verified'=>1]);}
    $st=$conn->prepare('SELECT resend_after FROM customer_email_challenges WHERE user_id=? FOR UPDATE');$st->bind_param('i',$id);$st->execute();$row=$st->get_result()->fetch_assoc();
    if($row && strtotime((string)$row['resend_after'])>time()){$conn->rollback();native_out(false,'Tunggu sebelum mengirim ulang.', ['retry_after'=>max(1,strtotime($row['resend_after'])-time())],429);}
    $otp=(string)random_int(100000,999999);$hash=password_hash($otp,PASSWORD_DEFAULT);
    $st=$conn->prepare('INSERT INTO customer_email_challenges(user_id,email,otp_hash,expires_at,resend_after) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL 5 MINUTE),DATE_ADD(NOW(),INTERVAL 60 SECOND)) ON DUPLICATE KEY UPDATE email=VALUES(email),otp_hash=VALUES(otp_hash),expires_at=VALUES(expires_at),resend_after=VALUES(resend_after),attempts=0,proof_hash=NULL,proof_expires_at=NULL,consumed_at=NULL');
    $st->bind_param('iss',$id,$email,$hash);$st->execute();$conn->commit();
    ce_mail($email,$otp);native_out(true,'Kode dikirim ke email akun.', ['retry_after'=>60]);
}catch(Throwable $ex){try{$conn->rollback();if($hash!==''){$st=$conn->prepare('UPDATE customer_email_challenges SET otp_hash=NULL WHERE user_id=? AND otp_hash=?');$st->bind_param('is',$id,$hash);$st->execute();}}catch(Throwable $ignored){}error_log('CUSTOMER_EMAIL_SEND '.$ex->getMessage());native_out(false,'Kode belum dapat dikirim. Coba kembali sebentar lagi.',[],503);}
