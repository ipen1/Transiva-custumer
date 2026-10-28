<?php
declare(strict_types=1);
const RF_REWARD=1000;
const RF_MIN_TOPUP=10000;
const RF_SLOGAN='Undang teman ke Transiva, dapatkan saldo promo Rp1.000 setelah temanmu top up minimal Rp10.000 dan menyelesaikan pesanan pertamanya.';
function rf_table(mysqli $c,string $name):bool {
 static $cache=[];$key=spl_object_id($c).':'.$name;if(isset($cache[$key]))return $cache[$key];
 $s=mysqli_prepare($c,'SELECT COUNT(*) n FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?');if(!$s)return false;mysqli_stmt_bind_param($s,'s',$name);mysqli_stmt_execute($s);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($s));mysqli_stmt_close($s);return $cache[$key]=(int)($r['n']??0)>0;
}
function rf_query(mysqli $c,string $sql,string $types='',array $args=[]):mysqli_stmt {
 $s=mysqli_prepare($c,$sql);if(!$s)throw new RuntimeException('Referral belum siap.');if($types!=='')mysqli_stmt_bind_param($s,$types,...$args);
 if(!mysqli_stmt_execute($s)){mysqli_stmt_close($s);throw new RuntimeException('Pencatatan referral gagal.');}return $s;
}
function rf_rows(mysqli_stmt $s):array{$q=mysqli_stmt_get_result($s);$rows=[];while($r=mysqli_fetch_assoc($q))$rows[]=$r;mysqli_stmt_close($s);return $rows;}
function rf_ready(mysqli $c):bool{return rf_table($c,'customer_referral_invites_v2');}
function rf_require_storage(mysqli $c):void {
 static $checked=[];$key=spl_object_id($c);if(isset($checked[$key]))return;
 foreach(['users','wallet_transactions','customer_referral_invites_v2'] as $name){
  $r=rf_rows(rf_query($c,'SELECT ENGINE engine FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?','s',[$name]));
  if(strcasecmp((string)($r[0]['engine']??''),'InnoDB')!==0)throw new RuntimeException('Tabel referral/wallet harus InnoDB.');
 }
 $indexes=rf_rows(rf_query($c,"SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) columns_list FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_referral_invites_v2' AND NON_UNIQUE=0 GROUP BY INDEX_NAME"));
 $valid=false;foreach($indexes as $index)if(($index['columns_list']??'')==='referred_user_id')$valid=true;
 if(!$valid)throw new RuntimeException('Unique customer referral belum terpasang.');$checked[$key]=true;
}
function rf_has_previous_activity(mysqli $c,int $uid):bool {
 foreach(['orders','pickup_orders'] as $table)if(rf_table($c,$table)){
  $r=rf_rows(rf_query($c,"SELECT id FROM $table WHERE user_id=? LIMIT 1",'i',[$uid]));if($r)return true;
 }
 // Permanent earning ledgers survive history archiving and prevent reusing an old active account.
 foreach(['customer_loyalty_point_ledger','customer_coin_ledger'] as $table)if(rf_table($c,$table)){
  if(rf_rows(rf_query($c,"SELECT user_id FROM $table WHERE user_id=? AND entry_type='earn' LIMIT 1",'i',[$uid])))return true;
 }
 // Transfers, rewards, refunds and unapproved proof uploads are never deposits.
 return (bool)rf_rows(rf_query($c,"SELECT id FROM wallet_transactions WHERE user_id=? AND LOWER(type) IN ('deposit','topup') AND LOWER(status)='success' LIMIT 1",'i',[$uid]));
}
function rf_attach(mysqli $c,int $uid,string $code):array {
 if(!rf_ready($c))throw new RuntimeException('Jalankan maintenance/referral_cash_v2.sql terlebih dahulu.');
 rf_require_storage($c);$code=strtoupper(trim($code));if(!preg_match('/^[A-Z0-9]{3,40}$/',$code))throw new DomainException('Kode referral tidak valid.');
 if(!mysqli_begin_transaction($c))throw new RuntimeException('Transaksi referral tidak dapat dimulai.');
 try{
  // Same customer row lock used by order creation: binding and first order cannot race.
  $u=rf_rows(rf_query($c,"SELECT id FROM users WHERE id=? AND LOWER(role)='customer' FOR UPDATE",'i',[$uid]));if(!$u)throw new DomainException('Akun customer tidak ditemukan.');
  $bound=rf_rows(rf_query($c,'SELECT referral_code FROM customer_referral_invites_v2 WHERE referred_user_id=? FOR UPDATE','i',[$uid]));
  if($bound){if($bound[0]['referral_code']!==$code)throw new DomainException('Kode referral akun ini sudah ditetapkan.');if(!mysqli_commit($c))throw new RuntimeException('Komit referral belum terkonfirmasi.');return ['already_attached'=>true];}
  $owner=rf_rows(rf_query($c,"SELECT r.user_id FROM customer_referral_codes r JOIN users u ON u.id=r.user_id WHERE r.referral_code=? AND LOWER(u.role)='customer'",'s',[$code]));$ref=(int)($owner[0]['user_id']??0);
  if($ref<=0)throw new DomainException('Kode referral tidak ditemukan.');if($ref===$uid)throw new DomainException('Kode sendiri tidak dapat digunakan.');
  if(rf_has_previous_activity($c,$uid))throw new DomainException('Kode hanya untuk customer baru, sebelum order atau top up berhasil pertama.');
  // One-time binding survives repeats and rejects attempts to switch inviter.
  $s=rf_query($c,'INSERT INTO customer_referral_invites_v2(referrer_user_id,referred_user_id,referral_code,reward_amount) VALUES(?,?,?,?)','iisi',[$ref,$uid,$code,RF_REWARD]);mysqli_stmt_close($s);
  if(!mysqli_commit($c))throw new RuntimeException('Komit referral belum terkonfirmasi.');return ['already_attached'=>false];
 }catch(Throwable $e){mysqli_rollback($c);throw $e;}
}
function rf_requirements(mysqli $c,array $invite):array {
 $uid=(int)$invite['referred_user_id'];$since=(string)$invite['created_at'];
 $deposit=rf_rows(rf_query($c,"SELECT id,amount,created_at FROM wallet_transactions WHERE user_id=? AND LOWER(type) IN ('deposit','topup') AND LOWER(status)='success' AND direction='in' AND amount>=? AND created_at>=? ORDER BY id LIMIT 1",'iis',[$uid,RF_MIN_TOPUP,$since]));
 $done=[];
 foreach(['orders','pickup_orders'] as $table)if(rf_table($c,$table)){
  // Only trusted terminal states qualify; arrived_delivery/delivered/pending/cancelled do not.
  $rows=rf_rows(rf_query($c,"SELECT id,order_id,updated_at FROM $table WHERE user_id=? AND created_at>=? AND LOWER(status) IN ('finished','completed','finish','done','selesai') ORDER BY updated_at ASC,id ASC LIMIT 1",'is',[$uid,$since]));
  if($rows){$r=$rows[0];$r['source']=$table;$done[]=$r;}
 }
 usort($done,fn($a,$b)=>strcmp((string)$a['updated_at'],(string)$b['updated_at'])?:((int)$a['id']<=>(int)$b['id']));
 return ['topup'=>$deposit[0]??null,'order'=>$done[0]??null,'topup_qualified'=>(bool)$deposit,'first_order_confirmed'=>(bool)$done];
}
function rf_award(mysqli $c,int $inviteId):bool {
 rf_require_storage($c);if(!mysqli_begin_transaction($c))throw new RuntimeException('Transaksi referral tidak dapat dimulai.');
 try{
  $r=rf_rows(rf_query($c,'SELECT * FROM customer_referral_invites_v2 WHERE id=? FOR UPDATE','i',[$inviteId]));$invite=$r[0]??null;
  if(!$invite||$invite['status']!=='pending'){mysqli_rollback($c);return false;}
  $requirements=rf_requirements($c,$invite);if(!$requirements['topup']||!$requirements['order']){mysqli_rollback($c);return false;}
  $ref=(int)$invite['referrer_user_id'];$new=(int)$invite['referred_user_id'];
  if($ref===$new)throw new RuntimeException('Referrer tidak valid.');
  $users=rf_rows(rf_query($c,"SELECT id,balance FROM users WHERE id=? AND LOWER(role)='customer' FOR UPDATE",'i',[$ref]));if(!$users){mysqli_rollback($c);return false;}
  $before=(int)$users[0]['balance'];$after=$before+RF_REWARD;$reference='REFERRAL-V2-'.$inviteId;
  $exists=rf_rows(rf_query($c,"SELECT id FROM wallet_transactions WHERE user_id=? AND type='referral_promo_credit' AND reference=? LIMIT 1",'is',[$ref,$reference]));
  if($exists)throw new RuntimeException('Ledger referral sudah ada tetapi status belum cocok; periksa rekonsiliasi.');
  $s=rf_query($c,'UPDATE users SET balance=? WHERE id=?','ii',[$after,$ref]);if(mysqli_stmt_affected_rows($s)!==1)throw new RuntimeException('Kredit saldo referral gagal.');mysqli_stmt_close($s);
  $description='Saldo promo referral: top up minimal Rp10.000 dan pesanan pertama terkonfirmasi';
  $s=rf_query($c,"INSERT INTO wallet_transactions(user_id,type,direction,amount,balance_before,balance_after,status,reference,description,related_user_id,created_at) VALUES(?,'referral_promo_credit','in',?,?,?,'success',?,?,?,NOW())",'iiiissi',[$ref,RF_REWARD,$before,$after,$reference,$description,$new]);$walletId=(int)mysqli_insert_id($c);mysqli_stmt_close($s);
  $s=rf_query($c,"UPDATE customer_referral_invites_v2 SET status='rewarded',reward_amount=?,qualifying_topup_id=?,qualifying_order_source=?,qualifying_order_id=?,wallet_transaction_id=?,rewarded_at=NOW() WHERE id=? AND status='pending'",'iisiii',[RF_REWARD,(int)$requirements['topup']['id'],$requirements['order']['source'],(int)$requirements['order']['id'],$walletId,$inviteId]);if(mysqli_stmt_affected_rows($s)!==1)throw new RuntimeException('Status referral gagal disimpan.');mysqli_stmt_close($s);
  if(!mysqli_commit($c))throw new RuntimeException('Komit hadiah belum terkonfirmasi.');return true;
 }catch(Throwable $e){mysqli_rollback($c);throw $e;}
}
function rf_candidates(mysqli $c,int $uid=0,int $after=0,int $limit=30):array {
 $where=$uid>0?' AND (r.referrer_user_id='.(int)$uid.' OR r.referred_user_id='.(int)$uid.')':'';
 $finished=[];foreach(['orders','pickup_orders'] as $table)if(rf_table($c,$table))$finished[]="EXISTS(SELECT 1 FROM $table o WHERE o.user_id=r.referred_user_id AND o.created_at>=r.created_at AND LOWER(o.status) IN ('finished','completed','finish','done','selesai'))";
 if(!$finished)return [];$limit=max(1,min(100,$limit));
 return rf_rows(rf_query($c,"SELECT r.id FROM customer_referral_invites_v2 r WHERE r.status='pending' AND r.id>?$where AND EXISTS(SELECT 1 FROM wallet_transactions w WHERE w.user_id=r.referred_user_id AND LOWER(w.type) IN ('deposit','topup') AND LOWER(w.status)='success' AND w.direction='in' AND w.amount>=10000 AND w.created_at>=r.created_at) AND (".implode(' OR ',$finished).") ORDER BY r.id LIMIT $limit",'i',[$after]));
}
function rf_process_safe(mysqli $c,int $uid=0,int $limit=30):void {
 try{
  if(!rf_ready($c))return;
  // Call only from a standalone read/worker or AFTER the caller's successful settlement commit.
  foreach(rf_candidates($c,$uid,0,$limit) as $r)try{rf_award($c,(int)$r['id']);}catch(Throwable $e){error_log('REFERRAL_V2_DEFERRED id='.(int)$r['id'].' '.$e->getMessage());}
 }catch(Throwable $e){error_log('REFERRAL_V2_RECONCILE '.$e->getMessage());}
}
