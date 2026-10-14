<?php
declare(strict_types=1);
function cr_phone(string $value): string {
    $p=preg_replace('/\D+/', '', $value);
    if(substr($p,0,1)==='0')$p=substr($p,1);
    return $p!=='' && substr($p,0,2)!=='62'?'62'.$p:$p;
}
function cr_validate(array $in): array {
    $u=strtolower(trim((string)($in['username']??'')));
    $e=strtolower(trim((string)($in['email']??'')));
    $p=cr_phone((string)($in['phone']??$in['no_hp']??''));
    $pass=(string)($in['password']??'');
    if(!preg_match('/^[a-z0-9_.-]{3,30}$/',$u))throw new InvalidArgumentException('Nama pengguna harus 3–30 karakter: huruf, angka, titik, strip, underscore.');
    if(strlen($e)>254 || !filter_var($e,FILTER_VALIDATE_EMAIL))throw new InvalidArgumentException('Email tidak valid.');
    if(!preg_match('/^62[0-9]{9,13}$/',$p))throw new InvalidArgumentException('Nomor HP tidak valid.');
    if(mb_strlen($pass,'UTF-8')<8 || strlen($pass)>72)throw new InvalidArgumentException('Kata sandi minimal 8 karakter, maksimal 72 byte.');
    return [$u,$e,$p,$pass];
}
/** A receipt lock and unique database indexes serialize retries and competing signups. */
function cr_register(mysqli $c,array $in,bool $google=false): array {
    [$u,$e,$p,$pass]=cr_validate($in);
    $id=trim((string)($in['operation_id']??''));
    if(!preg_match('/^[a-zA-Z0-9_-]{16,100}$/',$id))throw new InvalidArgumentException('ID pendaftaran tidak valid.');
    // Identity fingerprint excludes passwords. A salted slow hash binds password retries.
    $fingerprint=hash('sha256',json_encode([$u,$e,$p,$google],JSON_THROW_ON_ERROR));
    $passwordProof=password_hash($pass,PASSWORD_DEFAULT);
    $c->begin_transaction();
    try{
        $st=$c->prepare('INSERT INTO customer_registration_receipts(operation_id,request_hash,password_proof) VALUES(?,?,?) ON DUPLICATE KEY UPDATE operation_id=operation_id');
        $st->bind_param('sss',$id,$fingerprint,$passwordProof);$st->execute();
        $st=$c->prepare('SELECT request_hash,password_proof,response_json FROM customer_registration_receipts WHERE operation_id=? FOR UPDATE');$st->bind_param('s',$id);$st->execute();$row=$st->get_result()->fetch_assoc();
        if(!$row || !hash_equals($row['request_hash'],$fingerprint) || (!$google && !password_verify($pass,$row['password_proof'])))throw new InvalidArgumentException('Data berubah dari percobaan sebelumnya. Jika akun sudah dibuat, masuk dengan kata sandi sebelumnya.',409);
        if($row['response_json']!==null){$out=json_decode($row['response_json'],true,512,JSON_THROW_ON_ERROR);$c->commit();$out['replayed']=true;return $out;}
        $hash=$row['password_proof'];$verified=$google?1:0;
        $st=$c->prepare("INSERT INTO users(username,email,phone,password,role,email_verified,email_verified_at) VALUES(?,?,?,?,'customer',?,IF(?=1,NOW(),NULL))");
        $st->bind_param('ssssii',$u,$e,$p,$hash,$verified,$verified);
        try{$st->execute();$out=['success'=>true,'message'=>'Akun berhasil dibuat.','user_id'=>$c->insert_id,'phone'=>$p,'email_verified'=>$verified];}
        catch(mysqli_sql_exception $ex){if($ex->getCode()!==1062)throw $ex;$out=['success'=>false,'message'=>'Nama pengguna, email, atau nomor HP sudah digunakan. Silakan masuk jika akun sudah pernah dibuat.','code'=>'ACCOUNT_EXISTS'];}
        $json=json_encode($out,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
        $st=$c->prepare('UPDATE customer_registration_receipts SET response_json=? WHERE operation_id=?');$st->bind_param('ss',$json,$id);$st->execute();
        $c->commit();return $out;
    }catch(Throwable $ex){$c->rollback();throw $ex;}
}
