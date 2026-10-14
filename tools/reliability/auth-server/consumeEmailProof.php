<?php
declare(strict_types=1);
require_once __DIR__.'/customer_email_helper.php';mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$auth=ce_user($conn);$id=(int)$auth['id'];$in=native_input();$proof=(string)($in['verification_proof']??'');
if(!preg_match('/^[a-f0-9]{64}$/',$proof))native_out(false,'Bukti verifikasi tidak valid.',[],422);
try{
    $conn->begin_transaction();$u=ce_lock($conn,$id);
    $st=$conn->prepare('SELECT * FROM customer_email_challenges WHERE user_id=? FOR UPDATE');$st->bind_param('i',$id);$st->execute();$r=$st->get_result()->fetch_assoc();
    if(!$r || !$r['proof_hash'] || !hash_equals($r['proof_hash'],hash('sha256',$proof)) || strtolower(trim($u['email']))!==$r['email']){$conn->rollback();native_out(false,'Bukti verifikasi tidak cocok dengan akun.',[],403);}
    // Replay is read-only: consumption and account update occurred in one commit.
    if($r['consumed_at'] && (int)$u['email_verified']===1){$conn->commit();native_out(true,'Email sudah terverifikasi.',['email_verified'=>1,'replayed'=>true]);}
    if($r['consumed_at'] || strtotime($r['proof_expires_at'])<=time()){$conn->rollback();native_out(false,'Bukti sudah digunakan atau kedaluwarsa. Kirim ulang OTP.',[],422,'EMAIL_PROOF_EXPIRED');}
    $conn->query("UPDATE users SET email_verified=1,email_verified_at=NOW() WHERE id=$id");$conn->query("UPDATE customer_email_challenges SET consumed_at=NOW() WHERE user_id=$id");$conn->commit();
    native_out(true,'Email berhasil diverifikasi.', ['email_verified'=>1]);
}catch(Throwable $ex){$conn->rollback();error_log('CUSTOMER_EMAIL_PROOF '.$ex->getMessage());native_out(false,'Hasil belum terkonfirmasi. Coba verifikasi kembali.',[],503);}
