<?php

require_once __DIR__.'/schema_runtime_guard.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
require __DIR__.'/config.php';
require_once __DIR__.'/p0_security_helper.php';
require_once __DIR__.'/customer_support_order_helper.php';
function reply_support($data,$code=200){http_response_code($code);echo json_encode($data,JSON_UNESCAPED_UNICODE);exit;}
try {
 $auth=p0_require_customer($conn); $uid=(int)$auth['id'];
 transiva_schema_query($conn,"CREATE TABLE IF NOT EXISTS customer_support_tickets (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL, category VARCHAR(80) NOT NULL, subject VARCHAR(150) NOT NULL, detail TEXT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'open', admin_reply TEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX idx_customer_created (customer_id,created_at), INDEX idx_status (status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
 if($_SERVER['REQUEST_METHOD']==='GET'){$st=$conn->prepare('SELECT id,category,subject,detail,status,admin_reply,created_at,updated_at FROM customer_support_tickets WHERE customer_id=? ORDER BY id DESC LIMIT 30');$st->bind_param('i',$uid);$st->execute();$rows=$st->get_result()->fetch_all(MYSQLI_ASSOC);reply_support(['success'=>true,'tickets'=>$rows]);}
 if($_SERVER['REQUEST_METHOD']!=='POST')reply_support(['success'=>false,'message'=>'Metode tidak didukung'],405);
 p0_rate_limit($conn,'customer_support',(string)$uid,5,3600);
 $data=json_decode(file_get_contents('php://input'),true);if(!is_array($data))reply_support(['success'=>false,'message'=>'Format tidak valid'],400);
 $category=trim((string)($data['category']??''));$subject=trim((string)($data['subject']??''));$detail=trim((string)($data['detail']??''));
 $allowed=['Pesanan / perjalanan','Pembayaran / saldo','Akun / login','Driver / merchant','Aplikasi / teknis','Lainnya'];
 if(!in_array($category,$allowed,true)||mb_strlen($subject)<5||mb_strlen($subject)>150||mb_strlen($detail)<15||mb_strlen($detail)>4000)reply_support(['success'=>false,'message'=>'Periksa kembali isi keluhan'],422);
 $detail.=support_order_evidence($conn,$auth,$data);
 $photo=support_save_photo($data);if($photo!==null)$detail.='\nBukti foto: https://transiva.my.id/'.$photo;
 $st=$conn->prepare('INSERT INTO customer_support_tickets(customer_id,category,subject,detail) VALUES(?,?,?,?)');$st->bind_param('isss',$uid,$category,$subject,$detail);if(!$st->execute())throw new Exception('Insert failed');reply_support(['success'=>true,'id'=>$conn->insert_id,'message'=>'Keluhan tersimpan']);
}catch(InvalidArgumentException $e){reply_support(['success'=>false,'message'=>$e->getMessage()],422);}
catch(Throwable $e){error_log('support: '.$e->getMessage());reply_support(['success'=>false,'message'=>'Layanan bantuan sementara tidak tersedia'],500);}
