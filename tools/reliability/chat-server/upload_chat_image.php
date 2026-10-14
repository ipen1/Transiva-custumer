<?php
declare(strict_types=1);
require_once __DIR__.'/config.php';require_once __DIR__.'/chat_media_helper.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='POST')chat_out(false,'Metode tidak didukung.',[],405);
$auth=native_require_user($conn);[$order,$room,$sender]=chat_media_context($conn,$auth);
$files=[chat_media_file('preview_image',2*1024*1024,true),chat_media_file('hd_image',8*1024*1024,true)];
try{$response=chat_media_send($conn,$auth,$order,$room,$sender,'image',$files);chat_out(true,'Foto terkirim.',$response);}
catch(InvalidArgumentException $e){chat_out(false,$e->getMessage(),[],409,'CHAT_ID_CONFLICT');}
catch(DomainException $e){chat_out(false,$e->getMessage(),[],409,'CHAT_ENDED');}
catch(Throwable $e){error_log('CHAT_IMAGE_UNCONFIRMED');chat_out(false,'Hasil foto belum terkonfirmasi. Kirim ulang dengan ID yang sama.',[],503,'CHAT_SEND_UNCONFIRMED');}
