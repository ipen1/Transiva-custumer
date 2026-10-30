<?php

require_once __DIR__.'/schema_runtime_guard.php';
/** Transiva customer benefit engine: verified vouchers + monthly Hemat quotas. */
require_once __DIR__ . '/loyalty_season_helper.php';
function cb_column_exists(mysqli $c,string $table,string $column):bool{
  $s=mysqli_prepare($c,"SELECT COUNT(*) n FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
  if(!$s)return false; mysqli_stmt_bind_param($s,'ss',$table,$column); mysqli_stmt_execute($s); $r=mysqli_fetch_assoc(mysqli_stmt_get_result($s)); mysqli_stmt_close($s); return (int)($r['n']??0)>0;
}
function cb_is_verified(mysqli $c,int $uid):bool{
  $hasAdmin=cb_column_exists($c,'users','verified_by_admin'); $hasEmail=cb_column_exists($c,'users','email_verified');
  if(!$hasAdmin && !$hasEmail) return false;
  $cols=[]; if($hasAdmin)$cols[]='COALESCE(verified_by_admin,0) verified_by_admin'; if($hasEmail)$cols[]='COALESCE(email_verified,0) email_verified';
  $s=mysqli_prepare($c,'SELECT '.implode(',',$cols).' FROM users WHERE id=? LIMIT 1'); if(!$s)return false; mysqli_stmt_bind_param($s,'i',$uid); mysqli_stmt_execute($s); $u=mysqli_fetch_assoc(mysqli_stmt_get_result($s)); mysqli_stmt_close($s); if(!$u)return false;
  // Same trusted DB policy as the customer dashboard: email OR admin verification.
  // Never infer verification from request fields or update either flag here.
  return (int)($u['verified_by_admin']??0)===1 || (int)($u['email_verified']??0)===1;
}
function cb_assert_voucher_verified(mysqli $c,int $uid,string $code):void{
  if(trim($code)==='')return;
  if(!cb_is_verified($c,$uid)) throw new Exception('Voucher hanya dapat digunakan oleh akun yang sudah terverifikasi. Silakan verifikasi akun terlebih dahulu.');
}
function cb_ensure_hemat_table(mysqli $c):void{
  transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS customer_hemat_usages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    benefit_bucket VARCHAR(20) NOT NULL,
    service_type VARCHAR(30) NOT NULL,
    order_id VARCHAR(120) NOT NULL,
    discount_amount INT NOT NULL DEFAULT 0,
    usage_month CHAR(7) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uq_hemat_order(order_id), KEY idx_hemat_month(user_id,benefit_bucket,usage_month)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
}
function cb_tier(mysqli $c,int $uid):string{
  // Benefit bulanan mengikuti tier aktif season, bukan achievement lifetime.
  $s=ls_user_stats($c,$uid);
  return strtoupper((string)($s['season_tier']??'BRONZE'));
}
function cb_ensure_hemat_settings_table(mysqli $c):void{
  transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS customer_hemat_tier_limits (
    tier VARCHAR(20) NOT NULL PRIMARY KEY,
    monthly_limit INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
  $defaults=['BRONZE'=>2,'SILVER'=>5,'GOLD'=>8,'DIAMOND'=>11,'PLATINUM'=>15];
  $st=mysqli_prepare($c,'INSERT IGNORE INTO customer_hemat_tier_limits(tier,monthly_limit) VALUES(?,?)');
  if($st){foreach($defaults as $tier=>$limit){mysqli_stmt_bind_param($st,'si',$tier,$limit);mysqli_stmt_execute($st);}mysqli_stmt_close($st);}
}
function cb_hemat_limit_for_tier(string $tier, ?mysqli $c=null):int{
  $t=strtoupper(trim($tier));
  if($c instanceof mysqli){
    cb_ensure_hemat_settings_table($c);
    $s=mysqli_prepare($c,'SELECT monthly_limit FROM customer_hemat_tier_limits WHERE tier=? LIMIT 1');
    if($s){mysqli_stmt_bind_param($s,'s',$t);mysqli_stmt_execute($s);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($s));mysqli_stmt_close($s);if($r)return max(0,(int)($r['monthly_limit']??0));}
  }
  if($t==='PLATINUM')return 15; if($t==='DIAMOND')return 11; if($t==='GOLD')return 8; if($t==='SILVER')return 5; return 2;
}

function cb_bucket(string $service):string{ return strtolower($service)==='transfood' ? 'food' : 'ride'; }
function cb_hemat_stats(mysqli $c,int $uid,string $service):array{
  cb_ensure_hemat_table($c); $tier=cb_tier($c,$uid); $limit=cb_hemat_limit_for_tier($tier,$c); $bucket=cb_bucket($service); $month=date('Y-m'); $used=0;
  $s=mysqli_prepare($c,'SELECT COUNT(*) used FROM customer_hemat_usages WHERE user_id=? AND benefit_bucket=? AND usage_month=?');
  if($s){mysqli_stmt_bind_param($s,'iss',$uid,$bucket,$month);mysqli_stmt_execute($s);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($s));mysqli_stmt_close($s);$used=(int)($r['used']??0);}
  return ['tier'=>$tier,'limit'=>$limit,'used'=>$used,'remaining'=>max(0,$limit-$used),'month'=>$month,'bucket'=>$bucket];
}
function cb_assert_hemat_available(mysqli $c,int $uid,string $service):array{
  if(!cb_is_verified($c,$uid)) throw new Exception('Mode Hemat khusus akun terverifikasi. Verifikasi email dan akun Anda terlebih dahulu.');
  $s=cb_hemat_stats($c,$uid,$service); if($s['remaining']<=0) throw new Exception('Kuota Hemat '.$s['tier'].' bulan ini sudah habis ('.$s['limit'].'x). Mode Standar tetap dapat digunakan.'); return $s;
}
function cb_hemat_discount(int $price):int{ return max(0,min(2000,(int)round(max(0,$price)*0.20))); }
function cb_record_hemat(mysqli $c,int $uid,string $service,string $orderId,int $discount):void{
  $stats=cb_assert_hemat_available($c,$uid,$service); $bucket=$stats['bucket'];$month=$stats['month'];
  $s=mysqli_prepare($c,'INSERT INTO customer_hemat_usages(user_id,benefit_bucket,service_type,order_id,discount_amount,usage_month) VALUES(?,?,?,?,?,?)');
  if(!$s)throw new Exception('Gagal menyiapkan pencatatan kuota Hemat'); mysqli_stmt_bind_param($s,'isssis',$uid,$bucket,$service,$orderId,$discount,$month); if(!mysqli_stmt_execute($s)){ $e=mysqli_stmt_error($s);mysqli_stmt_close($s);throw new Exception('Gagal mencatat kuota Hemat: '.$e);} mysqli_stmt_close($s);
}
?>
