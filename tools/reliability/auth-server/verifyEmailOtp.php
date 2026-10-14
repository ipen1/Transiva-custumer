<?php
declare(strict_types=1);
require_once __DIR__.'/customer_email_helper.php';mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$auth=ce_user($conn);$id=(int)$auth['id'];$in=native_input();$otp=trim((string)($in['otp']??''));
if(!preg_match('/^[0-9]{6}$/',$otp))native_out(false,'OTP harus 6 angka.',[],422);
try{
    $conn->begin_transaction();$u=ce_lock($conn,$id);
    $st=$conn->prepare('SELECT * FROM customer_email_challenges WHERE user_id=? FOR UPDATE');$st->bind_param('i',$id);$st->execute();$r=$st->get_result()->fetch_assoc();
    if(!$r || !$r['otp_hash'] || strtolower(trim($u['email']))!==$r['email'] || strtotime($r['expires_at'])<=time() || (int)$r['attempts']>=5){$conn->rollback();native_out(false,'Kode tidak berlaku. Kirim ulang OTP.',[],422);}
    if(!password_verify($otp,$r['otp_hash'])){$conn->query("UPDATE customer_email_challenges SET attempts=attempts+1 WHERE user_id=$id");$conn->commit();native_out(false,'Kode salah.',[],422);}
    $proof=bin2hex(random_bytes(32));$hash=hash('sha256',$proof);
    $st=$conn->prepare('UPDATE customer_email_challenges SET otp_hash=NULL,proof_hash=?,proof_expires_at=DATE_ADD(NOW(),INTERVAL 5 MINUTE),consumed_at=NULL WHERE user_id=?');$st->bind_param('si',$hash,$id);$st->execute();$conn->commit();
    native_out(true,'Kode valid.', ['verification_proof'=>$proof]);
}catch(Throwable $ex){$conn->rollback();error_log('CUSTOMER_OTP_VERIFY '.$ex->getMessage());native_out(false,'Verifikasi belum dapat diproses.',[],503);}
