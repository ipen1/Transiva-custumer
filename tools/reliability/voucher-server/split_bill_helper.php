<?php
declare(strict_types=1);

require_once __DIR__.'/schema_runtime_guard.php';
require_once __DIR__.'/send_fcm.php';
function sb_bootstrap(mysqli $c):void{
    static $schemaReady=[];$schemaKey=spl_object_id($c);if(isset($schemaReady[$schemaKey]))return;
 transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS split_bill_sessions(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,session_key VARCHAR(64) NOT NULL UNIQUE,owner_user_id BIGINT UNSIGNED NOT NULL,context VARCHAR(16) NOT NULL DEFAULT 'ride',group_size TINYINT UNSIGNED NOT NULL,expected_total BIGINT NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL DEFAULT 'inviting',expires_at DATETIME NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY(owner_user_id,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
 transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS split_bill_participants(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,session_key VARCHAR(64) NOT NULL,user_id BIGINT UNSIGNED NOT NULL,percentage DECIMAL(5,2) NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL DEFAULT 'pending',invited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,accepted_at DATETIME NULL,UNIQUE KEY uq_sb_user(session_key,user_id),KEY(session_key,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
 transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS split_bill_payments(id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,session_key VARCHAR(64) NOT NULL,order_id VARCHAR(80) NOT NULL,user_id BIGINT UNSIGNED NOT NULL,percentage DECIMAL(5,2) NOT NULL,amount BIGINT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'held',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uq_sb_pay(order_id,user_id),KEY(session_key),KEY(order_id,status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $schemaReady[$schemaKey]=true;
}
function sb_session(mysqli $c,string $key):?array{$s=mysqli_prepare($c,"SELECT * FROM split_bill_sessions WHERE session_key=? LIMIT 1");mysqli_stmt_bind_param($s,'s',$key);mysqli_stmt_execute($s);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($s));mysqli_stmt_close($s);return $r?:null;}
function sb_parts(mysqli $c,string $key):array{$s=mysqli_prepare($c,"SELECT p.user_id,p.percentage,p.status,p.invited_at,p.accepted_at,u.username,u.balance FROM split_bill_participants p JOIN users u ON u.id=p.user_id WHERE p.session_key=? ORDER BY p.id");mysqli_stmt_bind_param($s,'s',$key);mysqli_stmt_execute($s);$r=mysqli_stmt_get_result($s);$o=[];while($r&&$x=mysqli_fetch_assoc($r))$o[]=$x;mysqli_stmt_close($s);return $o;}
function sb_wallet_log(mysqli $c,int $uid,int $amount,int $before,int $after,string $orderId,string $context):void{
 if(!function_exists('tp_add_wallet_log')) return;
 $ref='SPLIT-'.$orderId.'-U'.$uid;
 // Idempotent per peserta. Ini juga mencegah mutasi ganda saat request retry.
 $safeRef=mysqli_real_escape_string($c,$ref);$q=transiva_schema_query($c,"SELECT id FROM wallet_transactions WHERE user_id=".(int)$uid." AND reference='$safeRef' LIMIT 1");
 if($q && mysqli_num_rows($q)>0) return;
 // Gunakan tipe pembayaran yang sudah kompatibel dengan schema wallet lama maupun baru.
 $type=$context==='food'?'food_payment':'ride_payment';
 if(!tp_add_wallet_log($c,$uid,$type,$amount,$before,$after,$ref,'Split Pay '.$orderId)){ $dbErr=mysqli_error($c); throw new Exception('Gagal mencatat mutasi Split Pay peserta'.($dbErr!==''?': '.$dbErr:'')); }
}
function sb_apply_payment(mysqli $c,string $key,string $orderId,int $total,int $ownerId,string $walletType='split_payment'):array{
 sb_bootstrap($c);$sess=sb_session($c,$key);if(!$sess||intval($sess['owner_user_id'])!==$ownerId)throw new Exception('Sesi Split Pay tidak valid');if(strtotime((string)$sess['expires_at'])<time())throw new Exception('Undangan Split Pay sudah kedaluwarsa');
 $parts=sb_parts($c,$key);if(count($parts)!==intval($sess['group_size']))throw new Exception('Jumlah peserta Split Pay belum lengkap');foreach($parts as $p)if($p['status']!=='accepted')throw new Exception('Semua peserta Split Pay harus menerima undangan');
 $sum=0.0;foreach($parts as $p)$sum+=(float)$p['percentage'];if(abs($sum-100.0)>0.05)throw new Exception('Total pembagian Split Pay harus 100%');
 // Preflight semua saldo SEBELUM satu rupiah pun dipotong.
 $alloc=[];$remaining=$total;foreach($parts as $i=>$p){$amount=$i===count($parts)-1?$remaining:(int)round($total*(float)$p['percentage']/100);$remaining-=$amount;$alloc[]=['user_id'=>(int)$p['user_id'],'percentage'=>(float)$p['percentage'],'amount'=>$amount];}
 foreach($alloc as $a){$q=mysqli_prepare($c,"SELECT balance FROM users WHERE id=? AND role='customer' LIMIT 1 FOR UPDATE");mysqli_stmt_bind_param($q,'i',$a['user_id']);mysqli_stmt_execute($q);$u=mysqli_fetch_assoc(mysqli_stmt_get_result($q));mysqli_stmt_close($q);if(!$u||(int)$u['balance']<$a['amount'])throw new Exception('Saldo salah satu peserta Split Pay tidak cukup');}
 $context=strpos(strtolower($walletType),'food')!==false?'food':'ride';$out=[];
 foreach($alloc as $a){$uid=$a['user_id'];$amount=$a['amount'];$q=mysqli_prepare($c,"SELECT balance FROM users WHERE id=? LIMIT 1 FOR UPDATE");mysqli_stmt_bind_param($q,'i',$uid);mysqli_stmt_execute($q);$u=mysqli_fetch_assoc(mysqli_stmt_get_result($q));mysqli_stmt_close($q);$before=(int)$u['balance'];$after=$before-$amount;
   $q=mysqli_prepare($c,"UPDATE users SET balance=balance-? WHERE id=? AND balance>=? LIMIT 1");mysqli_stmt_bind_param($q,'iii',$amount,$uid,$amount);mysqli_stmt_execute($q);if($amount>0 && mysqli_stmt_affected_rows($q)<1){mysqli_stmt_close($q);throw new Exception('Gagal menahan saldo peserta Split Pay');}mysqli_stmt_close($q);
   // Catat hold segera setelah debit, agar kompensasi selalu tahu siapa yang sudah terpotong.
   $q=mysqli_prepare($c,"INSERT INTO split_bill_payments(session_key,order_id,user_id,percentage,amount,status) VALUES(?,?,?,?,?,'held') ON DUPLICATE KEY UPDATE percentage=VALUES(percentage),amount=VALUES(amount)");$pct=$a['percentage'];mysqli_stmt_bind_param($q,'ssidi',$key,$orderId,$uid,$pct,$amount);if(!mysqli_stmt_execute($q)){ $err=mysqli_stmt_error($q);mysqli_stmt_close($q);throw new Exception('Gagal mencatat hold Split Pay: '.$err);}mysqli_stmt_close($q);
   if($amount>0)sb_wallet_log($c,$uid,$amount,$before,$after,$orderId,$context);$out[]=$a;
 }
 $q=mysqli_prepare($c,"UPDATE split_bill_sessions SET status='held' WHERE session_key=?");mysqli_stmt_bind_param($q,'s',$key);mysqli_stmt_execute($q);mysqli_stmt_close($q);return $out;
}
// Kompensasi untuk server/schema lama non-transactional: jangan biarkan saldo tertahan bila order gagal dibuat.
function sb_compensate_failed_order(mysqli $c,string $orderId):void{
 if($orderId==='')return;sb_bootstrap($c);$safe=mysqli_real_escape_string($c,$orderId);$q=transiva_schema_query($c,"SELECT id,user_id,amount FROM split_bill_payments WHERE order_id='$safe' AND status='held' FOR UPDATE");if(!$q)return;
 while($p=mysqli_fetch_assoc($q)){$uid=(int)$p['user_id'];$amt=(int)$p['amount'];$id=(int)$p['id'];if($uid<=0||$amt<=0)continue;$u=transiva_schema_query($c,"SELECT balance FROM users WHERE id=$uid LIMIT 1 FOR UPDATE");$r=$u?mysqli_fetch_assoc($u):null;if(!$r)continue;$before=(int)$r['balance'];$after=$before+$amt;transiva_schema_query($c,"UPDATE users SET balance=balance+$amt WHERE id=$uid LIMIT 1");if(function_exists('tp_add_wallet_log'))@tp_add_wallet_log($c,$uid,'refund',$amt,$before,$after,'REFUND-SPLIT-FAILED-'.$orderId.'-U'.$uid,'Refund otomatis Split Pay karena order gagal '.$orderId);transiva_schema_query($c,"UPDATE split_bill_payments SET status='refunded' WHERE id=$id LIMIT 1");}
}
?>
