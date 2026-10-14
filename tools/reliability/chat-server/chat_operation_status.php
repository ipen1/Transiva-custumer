<?php
declare(strict_types=1);
require_once __DIR__.'/config.php';require_once __DIR__.'/chat_security_helper.php';require_once __DIR__.'/chat_delivery_helper.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='GET')chat_out(false,'Metode tidak didukung.',[],405);
$auth=native_require_user($conn);$uid=(int)$auth['id'];$id=trim((string)($_GET['operation_id']??''));
try{chat_delivery_validate_id($id);if($id==='')throw new InvalidArgumentException('ID kosong');
 $st=chat_delivery_prepare($conn,'SELECT response_json FROM chat_media_receipts WHERE user_id=? AND operation_id=?');mysqli_stmt_bind_param($st,'is',$uid,$id);chat_delivery_execute($st);$row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
 if($row&&$row['response_json']!==null){$response=json_decode($row['response_json'],true,512,JSON_THROW_ON_ERROR);$response['replayed']=true;chat_out(true,'Lampiran sudah terkirim.',$response);}
 $st=chat_delivery_prepare($conn,'SELECT message_id FROM chat_message_receipts WHERE user_id=? AND client_message_id=?');mysqli_stmt_bind_param($st,'is',$uid,$id);chat_delivery_execute($st);$row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
 if($row&&(int)$row['message_id']>0)chat_out(true,'Pesan sudah terkirim.',['client_message_id'=>$id,'operation_id'=>$id,'id'=>(int)$row['message_id'],'replayed'=>true]);
 chat_out(false,'Operasi belum ditemukan.',['operation_state'=>'not_found','operation_id'=>$id]);
}catch(InvalidArgumentException $e){chat_out(false,'ID tidak valid.',[],422);}
catch(Throwable $e){chat_out(false,'Status belum dapat dipastikan. Coba kembali.',[],503,'CHAT_STORAGE_UNAVAILABLE');}
