<?php
// Status lookup only: no slot reservation, balance writes, quote creation or notification enqueue.
require 'config.php';
require_once __DIR__.'/wallet_helper.php';
require_once __DIR__.'/p0_security_helper.php';
require_once __DIR__.'/customer_wallet_operation_helper.php';
try {
    $auth=p0_require_customer($conn); $userId=(int)$auth['id']; $data=wallet_input();
    if(isset($data['operation_owner_id'])&&(string)$data['operation_owner_id']!==(string)$userId)throw new RuntimeException('Sesi operasi telah berubah.');
    $action=(string)($data['action']??'');$key=trim((string)($data['operation_id']??''));
    if(!in_array($action,['withdraw','transfer'],true)||$key===''||strlen($key)>120)throw new RuntimeException('ID operasi tidak valid.');
    $result=customer_wallet_lookup_status($conn,$userId,'wallet_'.$action,$key);
    wallet_out(true,'Status operasi diperiksa.',$result);
}catch(Throwable $error){wallet_out(false,'Status belum dapat diperiksa. Coba kembali nanti.',['operation_state'=>'unknown'],400);}
