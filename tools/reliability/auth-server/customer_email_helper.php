<?php
declare(strict_types=1);
require_once __DIR__.'/native_auth.php';
function ce_user(mysqli $c): array {
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST')native_out(false,'Metode tidak didukung.',[],405);
    $u=native_require_user($c);if(strtolower($u['role'])!=='customer')native_out(false,'Akses ditolak.',[],403);
    return $u;
}
function ce_lock(mysqli $c,int $id):array {
    $st=$c->prepare('SELECT id,email,email_verified FROM users WHERE id=? FOR UPDATE');$st->bind_param('i',$id);$st->execute();return $st->get_result()->fetch_assoc();
}
function ce_mail(string $email,string $otp):void {
    $base=__DIR__.'/../PHPMailer/src/';foreach(['Exception','PHPMailer','SMTP'] as $f)require_once $base.$f.'.php';
    $cfg=require __DIR__.'/mail_config.php';$mail=new \PHPMailer\PHPMailer\PHPMailer(true);
    $mail->isSMTP();$mail->Host=$cfg['host'];$mail->SMTPAuth=true;$mail->Username=$cfg['username'];$mail->Password=$cfg['password'];$mail->Port=(int)$cfg['port'];
    $mail->SMTPSecure=$cfg['secure']==='ssl'?'ssl':'tls';$mail->CharSet='UTF-8';$mail->Timeout=15;
    $mail->setFrom($cfg['from_email'],$cfg['from_name']);$mail->addAddress($email);$mail->Subject='Verifikasi email Transiva';$mail->Body="Kode verifikasi email Anda: $otp. Berlaku 5 menit. Jangan bagikan kode ini.";$mail->send();
}
