<?php
declare(strict_types=1);
require_once __DIR__.'/config.php';require_once __DIR__.'/customer_registration_helper.php';
header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try{
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){http_response_code(405);throw new InvalidArgumentException('Metode tidak didukung.');}
    $in=json_decode(file_get_contents('php://input'),true);if(!is_array($in))$in=$_POST;
    // Compatibility for old clients: stable retry ID derived from full request, never trust email_verified.
    if(empty($in['operation_id']))$in['operation_id']='legacy-'.hash('sha256',json_encode([$in['username']??'', $in['email']??'', $in['phone']??$in['no_hp']??'']));
    echo json_encode(cr_register($conn,$in),JSON_UNESCAPED_UNICODE);
}catch(InvalidArgumentException $ex){if(http_response_code()===200)http_response_code(422);echo json_encode(['success'=>false,'message'=>$ex->getMessage(),'code'=>$ex->getCode()===409?'REGISTRATION_ID_CONFLICT':'INVALID_REGISTRATION']);}
catch(Throwable $ex){error_log('REGISTRATION_UNCONFIRMED '.$ex->getMessage());http_response_code(503);echo json_encode(['success'=>false,'code'=>'REGISTRATION_UNCONFIRMED','message'=>'Hasil belum terkonfirmasi. Coba kembali dengan data yang sama.']);}
