<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');
require_once __DIR__.'/config.php';require_once __DIR__.'/customer_auth_lookup_helper.php';mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try{
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){http_response_code(405);echo json_encode(['success'=>false,'message'=>'Metode tidak didukung.']);exit;}
    $raw=file_get_contents('php://input',false,null,0,8193);$in=strlen($raw)<=8192?json_decode($raw,true):null;
    if(!is_array($in)||!isset($in['fields'])||!is_array($in['fields']))throw new InvalidArgumentException('Data pemeriksaan tidak valid.');
    $fields=array_intersect_key($in['fields'],array_flip(['username','email','phone']));if(!$fields)throw new InvalidArgumentException('Pilih data yang akan diperiksa.');
    foreach($fields as $value)if(!is_string($value)||strlen($value)>254)throw new InvalidArgumentException('Data pemeriksaan terlalu panjang.');
    if(!cal_signup_ready($conn)){http_response_code(503);echo json_encode(['success'=>false,'code'=>'AUTH_SCHEMA_REQUIRED','message'=>'Pendaftaran belum diaktifkan oleh admin. Silakan hubungi admin Transiva.']);exit;}
    echo json_encode(['success'=>true,'fields'=>cal_availability($conn,$fields)],JSON_UNESCAPED_UNICODE);
}catch(InvalidArgumentException $e){http_response_code(422);echo json_encode(['success'=>false,'message'=>$e->getMessage()]);}
catch(Throwable $e){error_log('AUTH_CHECK_FAILED errno='.$e->getCode());http_response_code(503);echo json_encode(['success'=>false,'code'=>'AUTH_CHECK_UNAVAILABLE','message'=>'Data belum dapat diperiksa. Coba kembali sebentar lagi.']);}
