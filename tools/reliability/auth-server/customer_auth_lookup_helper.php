<?php
declare(strict_types=1);
require_once __DIR__.'/customer_registration_helper.php';
function cal_columns(mysqli $c,string $table):array {
    static $cache=[];$key=spl_object_id($c).':'.$table;if(isset($cache[$key]))return $cache[$key];
    $rows=$c->query("SHOW COLUMNS FROM `$table`");$cols=[];while($row=$rows->fetch_assoc())$cols[$row['Field']]=$row;return $cache[$key]=$cols;
}
/** Read-only schema check. Runtime requests never ALTER/CREATE tables. */
function cal_signup_ready(mysqli $c):bool {
    $rows=$c->query("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('users','customer_registration_receipts','customer_email_challenges')");$tables=[];
    while($r=$rows->fetch_assoc())$tables[$r['TABLE_NAME']]=strtolower($r['ENGINE']);
    foreach(['users','customer_registration_receipts','customer_email_challenges'] as $table)if(($tables[$table]??'')!=='innodb')return false;
    foreach(['users'=>['id','username','email','phone','password','role','email_verified','email_verified_at','auth_username_key','auth_customer_email_key','auth_customer_phone_key'],'customer_registration_receipts'=>['operation_id','request_hash','password_proof','response_json'],'customer_email_challenges'=>['user_id','email','otp_hash','expires_at','resend_after','attempts','proof_hash','proof_expires_at','consumed_at']] as $table=>$needed){$cols=cal_columns($c,$table);foreach($needed as $column)if(!isset($cols[$column]))return false;}
    $indexes=$c->query('SHOW INDEX FROM users');$unique=[];while($r=$indexes->fetch_assoc())if((int)$r['Non_unique']===0)$unique[$r['Key_name']][]=$r['Column_name'];
    foreach(['auth_username_key','auth_customer_email_key','auth_customer_phone_key'] as $column){$found=false;foreach($unique as $cols)if($cols===[$column])$found=true;if(!$found)return false;}
    return true;
}
function cal_field(string $field,string $value):array {
    $v=$field==='phone'?cr_phone($value):strtolower(trim($value));$valid=false;
    if($field==='username')$valid=preg_match('/^[a-z0-9_.-]{3,30}$/',$v)===1;
    if($field==='email')$valid=strlen($v)<=254 && filter_var($v,FILTER_VALIDATE_EMAIL)!==false;
    if($field==='phone')$valid=preg_match('/^62[0-9]{9,13}$/',$v)===1;
    return [$v,$valid];
}
function cal_availability(mysqli $c,array $input):array {
    $out=[];$columns=['username'=>'auth_username_key','email'=>'auth_customer_email_key','phone'=>'auth_customer_phone_key'];
    $labels=['username'=>'Nama pengguna','email'=>'Email','phone'=>'Nomor HP'];
    foreach($columns as $field=>$column){
        if(!array_key_exists($field,$input))continue;
        [$value,$valid]=cal_field($field,(string)$input[$field]);
        if(!$valid){$out[$field]=['valid'=>false,'available'=>false,'message'=>$labels[$field].' tidak valid.'];continue;}
        $st=$c->prepare("SELECT id FROM users WHERE `$column`=? LIMIT 1");$st->bind_param('s',$value);$st->execute();$taken=(bool)$st->get_result()->fetch_assoc();$st->close();
        $out[$field]=['valid'=>true,'available'=>!$taken,'message'=>$labels[$field].($taken?' sudah digunakan.':' tersedia.')];
    }
    return $out;
}
function cal_conflicts(mysqli $c,array $in):array {
    $conflicts=[];foreach(cal_availability($c,$in) as $field=>$r)if(!$r['available'])$conflicts[$field]=$r['message'];return $conflicts;
}
