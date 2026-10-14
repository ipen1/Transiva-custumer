<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');
require_once __DIR__.'/config.php';require_once __DIR__.'/customer_auth_lookup_helper.php';mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try{
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){http_response_code(405);echo json_encode(['success'=>false,'message'=>'Metode tidak didukung.']);exit;}
    $raw=file_get_contents('php://input',false,null,0,2049);$in=strlen($raw)<=2048?json_decode($raw,true):[];
    $value=$in['username']??'';if(!is_string($value)||strlen($value)>100)throw new InvalidArgumentException('Nama pengguna tidak valid.');
    $value=trim($value);if($value==='')throw new InvalidArgumentException('Masukkan nama pengguna.');
    $cols=cal_columns($conn,'users');$photo=isset($cols['profile_photo'])?'profile_photo':"'' AS profile_photo";
    $st=$conn->prepare("SELECT username,$photo FROM users WHERE username=? AND LOWER(TRIM(role)) IN ('','customer','pelanggan','user') LIMIT 1");$st->bind_param('s',$value);$st->execute();$row=$st->get_result()->fetch_assoc();$st->close();
    if(!$row){echo json_encode(['success'=>true,'found'=>false,'message'=>'Nama pengguna belum terdaftar.']);exit;}
    // Only the avatar and login name are public here: never phone, email, IDs, or tokens.
    $photo=trim((string)$row['profile_photo']);
    if(stripos($photo,'view_chat_image.php')!==false || stripos($photo,'play_chat_voice.php')!==false || preg_match('#^(?:https://[^/]+/)?(?:server/)?(?:uploads/)?(?:chat/|chat_voice/)#i',$photo))$photo='';
    echo json_encode(['success'=>true,'found'=>true,'profile'=>['username'=>$row['username'],'photo'=>$photo]],JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
}catch(InvalidArgumentException $e){http_response_code(422);echo json_encode(['success'=>false,'message'=>$e->getMessage()]);}
catch(Throwable $e){error_log('LOGIN_PREVIEW_FAILED errno='.$e->getCode());http_response_code(503);echo json_encode(['success'=>false,'code'=>'LOGIN_PREVIEW_UNAVAILABLE','message'=>'Akun belum dapat diperiksa. Coba kembali sebentar lagi.']);}
