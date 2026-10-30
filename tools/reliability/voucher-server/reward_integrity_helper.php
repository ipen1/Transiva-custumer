<?php
declare(strict_types=1);

require_once __DIR__.'/schema_runtime_guard.php';
require_once __DIR__.'/royalty_mission_helper.php';
require_once __DIR__.'/transiva_point_helper.php';
/**
 * Transiva Reward Integrity V2
 * Coin: customer_coin_ledger remains canonical.
 * Royalti Point: customer_loyalty_point_ledger is canonical and survives order cleanup.
 * Secure balances are signed snapshots used for integrity + legacy recovery.
 */
function ri_integrity_available(): bool {
    return defined('DEVICE_HASH_SECRET') && strlen((string)DEVICE_HASH_SECRET) >= 32;
}
function ri_secret(): string {
    if (!ri_integrity_available()) return '';
    return hash_hmac('sha256', 'TRANSIVA-REWARD-INTEGRITY-V1', (string)DEVICE_HASH_SECRET, true);
}
function ri_sig(int $uid,string $asset,int $balance,int $extra,int $version): string {
    if (!ri_integrity_available()) return '';
    $msg = $uid.'|'.strtoupper($asset).'|'.$balance.'|'.$extra.'|'.$version;
    return hash_hmac('sha256', $msg, ri_secret());
}
function ri_ensure(mysqli $c): void {
    static $schemaReady=[];$schemaKey=spl_object_id($c);if(isset($schemaReady[$schemaKey]))return;
    transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS customer_coin_secure_balance (
      user_id BIGINT NOT NULL PRIMARY KEY,
      balance BIGINT NOT NULL DEFAULT 0,
      version BIGINT NOT NULL DEFAULT 1,
      signature CHAR(64) NOT NULL,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS customer_point_secure_balance (
      user_id BIGINT NOT NULL PRIMARY KEY,
      available_points BIGINT NOT NULL DEFAULT 0,
      earned_points BIGINT NOT NULL DEFAULT 0,
      version BIGINT NOT NULL DEFAULT 1,
      signature CHAR(64) NOT NULL,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS customer_loyalty_point_ledger (
      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      user_id BIGINT NOT NULL,
      order_id VARCHAR(120) DEFAULT NULL,
      entry_type ENUM('earn','adjustment') NOT NULL DEFAULT 'earn',
      points_delta INT NOT NULL DEFAULT 0,
      spend_amount BIGINT NOT NULL DEFAULT 0,
      reference VARCHAR(180) NOT NULL,
      description VARCHAR(255) NOT NULL DEFAULT '',
      occurred_at DATETIME NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY(id),
      UNIQUE KEY uq_loyalty_point_reference(reference),
      KEY idx_loyalty_point_user_time(user_id,occurred_at),
      KEY idx_loyalty_point_order(order_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $schemaReady[$schemaKey]=true;
}
function ri_table_exists(mysqli $c,string $table): bool {
    if(!preg_match('/^[A-Za-z0-9_]+$/',$table)) return false;
    $e=mysqli_real_escape_string($c,$table);$q=transiva_schema_query($c,"SHOW TABLES LIKE '$e'");return $q&&mysqli_num_rows($q)>0;
}
function ri_coin_write(mysqli $c,int $uid,int $balance): void {
    if(pt_ready($c))return; // Legacy ledgers are frozen after unification.
    if(!ri_integrity_available()) return;
    ri_ensure($c); $balance=max(0,$balance);
    $st=mysqli_prepare($c,"SELECT version FROM customer_coin_secure_balance WHERE user_id=? LIMIT 1 FOR UPDATE");
    mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    $v=max(1,(int)($r['version']??0)+1);$sig=ri_sig($uid,'COIN',$balance,0,$v);
    $st=mysqli_prepare($c,"INSERT INTO customer_coin_secure_balance(user_id,balance,version,signature) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE balance=VALUES(balance),version=VALUES(version),signature=VALUES(signature),updated_at=NOW()");
    mysqli_stmt_bind_param($st,'iiis',$uid,$balance,$v,$sig);mysqli_stmt_execute($st);mysqli_stmt_close($st);
}
function ri_coin_verify_or_init(mysqli $c,int $uid,int $ledgerBalance): void {
    if(pt_ready($c))return; // Legacy ledgers are frozen after unification.
    if(!ri_integrity_available()) return;
    ri_ensure($c);$ledgerBalance=max(0,$ledgerBalance);
    $st=mysqli_prepare($c,"SELECT balance,version,signature FROM customer_coin_secure_balance WHERE user_id=? LIMIT 1");
    mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    if(!$r){ri_coin_write($c,$uid,$ledgerBalance);return;}
    $bal=(int)$r['balance'];$v=(int)$r['version'];$ok=hash_equals(ri_sig($uid,'COIN',$bal,0,$v),(string)$r['signature']);
    if(!$ok){
        // Never block checkout/order completion because an old signed snapshot no longer matches.
        // The append-only coin ledger is canonical; heal the snapshot from it.
        error_log('REWARD_INTEGRITY coin signature mismatch healed uid='.$uid);
        ri_coin_write($c,$uid,$ledgerBalance);
        return;
    }
    if($bal>$ledgerBalance && ri_table_exists($c,'customer_coin_ledger')){
        // Valid snapshot is evidence that reward rows were lost by an old cleanup/manual delete.
        // Restore only the positive missing balance. Idempotent unique reference prevents duplicates.
        $missing=$bal-$ledgerBalance;$ref='SECURE-RECOVERY-COIN-'.$uid;$desc='Pemulihan Transiva Coin dari secure snapshot';
        $st=mysqli_prepare($c,"INSERT IGNORE INTO customer_coin_ledger(user_id,order_id,entry_type,coin_delta,rupiah_value,reference,description) VALUES(?,NULL,'adjustment',?,0,?,?)");
        if($st){mysqli_stmt_bind_param($st,'iiss',$uid,$missing,$ref,$desc);@mysqli_stmt_execute($st);mysqli_stmt_close($st);$ledgerBalance=max($ledgerBalance,coin_balance_raw($c,$uid));}
    }
    if($bal!==$ledgerBalance){error_log('REWARD_INTEGRITY coin snapshot refreshed uid='.$uid);ri_coin_write($c,$uid,$ledgerBalance);}
}
function ri_point_write(mysqli $c,int $uid,int $available,int $earned): void {
    if(!ri_integrity_available()) return;
    ri_ensure($c);$available=max(0,$available);$earned=max(0,$earned);
    $st=mysqli_prepare($c,"SELECT version FROM customer_point_secure_balance WHERE user_id=? LIMIT 1 FOR UPDATE");
    mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    $v=max(1,(int)($r['version']??0)+1);$sig=ri_sig($uid,'POINT',$available,$earned,$v);
    $st=mysqli_prepare($c,"INSERT INTO customer_point_secure_balance(user_id,available_points,earned_points,version,signature) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE available_points=VALUES(available_points),earned_points=VALUES(earned_points),version=VALUES(version),signature=VALUES(signature),updated_at=NOW()");
    mysqli_stmt_bind_param($st,'iiiis',$uid,$available,$earned,$v,$sig);mysqli_stmt_execute($st);mysqli_stmt_close($st);
}
function ri_point_secure_snapshot(mysqli $c,int $uid): ?array {
    if(!ri_integrity_available()) return null;
    ri_ensure($c);
    $st=mysqli_prepare($c,"SELECT available_points,earned_points,version,signature FROM customer_point_secure_balance WHERE user_id=? LIMIT 1");
    if(!$st)return null;mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    if(!$r)return null;$a=(int)$r['available_points'];$e=(int)$r['earned_points'];$v=(int)$r['version'];
    if(!hash_equals(ri_sig($uid,'POINT',$a,$e,$v),(string)$r['signature'])){error_log('REWARD_INTEGRITY point signature mismatch ignored uid='.$uid);return null;}
    return ['available'=>$a,'earned'=>$e,'version'=>$v];
}
function ri_point_verify_or_init(mysqli $c,int $uid,int $available,int $earned): void {
    $snap=ri_point_secure_snapshot($c,$uid);
    if(!$snap){ri_point_write($c,$uid,$available,$earned);return;}
    // Point ledger/sources are canonical after migration. Refresh stale snapshot instead of
    // zeroing rewards when operational order rows have been archived/deleted.
    if((int)$snap['available']!==max(0,$available)||(int)$snap['earned']!==max(0,$earned)){
        error_log('REWARD_INTEGRITY point snapshot refreshed uid='.$uid);ri_point_write($c,$uid,$available,$earned);
    }
}
function ri_point_backfill_orders(mysqli $c,int $uid): void {
    if(pt_ready($c))return; // Legacy ledgers are frozen after unification.
    ri_ensure($c);
    if(!ri_table_exists($c,'orders'))return;
    $cfg=rm_settings($c);$cutoff=mysqli_real_escape_string($c,$cfg['effective_at']);
    $sql="INSERT IGNORE INTO customer_loyalty_point_ledger(user_id,order_id,entry_type,points_delta,spend_amount,reference,description,occurred_at)
          SELECT user_id,order_id,'earn',FLOOR(GREATEST(COALESCE(price,0),0)/1000),GREATEST(COALESCE(price,0),0),CONCAT('ORDER-',order_id),'Poin transaksi legacy',COALESCE(created_at,NOW())
          FROM orders WHERE user_id=? AND LOWER(status) IN ('finished','completed','done','delivered','finish','selesai') AND order_id IS NOT NULL AND order_id<>'' AND COALESCE(updated_at,created_at)<'$cutoff'";
    $st=mysqli_prepare($c,$sql);if($st){mysqli_stmt_bind_param($st,'i',$uid);@mysqli_stmt_execute($st);mysqli_stmt_close($st);}
}
function ri_point_legacy_recover(mysqli $c,int $uid): void {
    if(pt_ready($c))return; // Legacy ledgers are frozen after unification.
    // Recover points for orders already deleted before V2 was installed, using the previously
    // signed secure lifetime snapshot. It is intentionally excluded from season calculations.
    $snap=ri_point_secure_snapshot($c,$uid);if(!$snap)return;
    $bonus=0;if(ri_table_exists($c,'customer_loyalty_bonus')){$st=mysqli_prepare($c,"SELECT COALESCE(SUM(points),0) p FROM customer_loyalty_bonus WHERE user_id=?");if($st){mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$bonus=(int)($r['p']??0);}}
    $st=mysqli_prepare($c,"SELECT COALESCE(SUM(points_delta),0) p FROM customer_loyalty_point_ledger WHERE user_id=?");if(!$st)return;mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$ledger=(int)($r['p']??0);
    $missing=max(0,(int)$snap['earned']-($ledger+$bonus));if($missing<=0)return;
    $ref='LEGACY-RECOVERY-'.$uid;$desc='Pemulihan poin lifetime dari secure snapshot sebelum pembersihan histori order';$when='2000-01-01 00:00:00';$st=mysqli_prepare($c,"INSERT IGNORE INTO customer_loyalty_point_ledger(user_id,entry_type,points_delta,spend_amount,reference,description,occurred_at) VALUES(?,'adjustment',?,0,?,?,?)");if($st){mysqli_stmt_bind_param($st,'iiss',$uid,$missing,$ref,$desc,$when);mysqli_stmt_execute($st);mysqli_stmt_close($st);}
}
function ri_point_source_values(mysqli $c,int $uid): array {
    if(pt_ready($c))return pt_source_values($c,$uid);
    ri_ensure($c);ri_point_backfill_orders($c,$uid);ri_point_legacy_recover($c,$uid);
    $transaction=0;$spend=0;$count=0;$bonus=0;$used=0;
    $st=mysqli_prepare($c,"SELECT COALESCE(SUM(points_delta),0) p,COALESCE(SUM(CASE WHEN entry_type='earn' THEN spend_amount ELSE 0 END),0) spend,COALESCE(SUM(CASE WHEN entry_type='earn' THEN 1 ELSE 0 END),0) c FROM customer_loyalty_point_ledger WHERE user_id=?");
    if($st){mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$transaction=(int)($r['p']??0);$spend=(int)($r['spend']??0);$count=(int)($r['c']??0);}
    if(ri_table_exists($c,'customer_loyalty_bonus')){$st=mysqli_prepare($c,"SELECT COALESCE(SUM(points),0) p FROM customer_loyalty_bonus WHERE user_id=?");if($st){mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$bonus=(int)($r['p']??0);}}
    if(ri_table_exists($c,'customer_loyalty_redemptions')){$st=mysqli_prepare($c,"SELECT COALESCE(SUM(points_cost),0) p FROM customer_loyalty_redemptions WHERE user_id=? AND status IN ('issued','used')");if($st){mysqli_stmt_bind_param($st,'i',$uid);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$used=(int)($r['p']??0);}}
    $earned=max(0,$transaction+$bonus);return ['earned'=>$earned,'available'=>max(0,$earned-$used),'transaction_points'=>$transaction,'bonus'=>$bonus,'used'=>$used,'spend'=>$spend,'count'=>$count];
}
function ri_point_sync_from_sources(mysqli $c,int $uid): void {if($uid<=0)return;$v=ri_point_source_values($c,$uid);ri_point_write($c,$uid,$v['available'],$v['earned']);}
function ri_point_award_finished_order(mysqli $c,array $order): array {
    if(pt_ready($c))return pt_award_order($c,$order);
    ri_ensure($c);
    $uid=(int)($order['user_id']??$order['customer_id']??$order['customer_user_id']??0);
    $oid=trim((string)($order['order_id']??$order['public_order_id']??$order['code']??''));
    if($oid==='' && !empty($order['id']))$oid='DB-'.(string)$order['id'];
    if($uid<=0||$oid==='')return ['awarded'=>false,'points'=>0,'reason'=>'invalid_identity'];
    $spend=0;
    foreach(['original_price','price_before_coin','gross_total','total_price','total','price','amount'] as $k){
      if(!array_key_exists($k,$order))continue; $raw=$order[$k];
      if(is_numeric($raw))$v=(int)round((float)$raw);else{$digits=preg_replace('/[^0-9-]/','',trim((string)$raw));$v=(int)($digits?:0);} if($v>0){$spend=$v;break;}
    }
    [$ordered,$completed]=rm_order_dates($order);
    $points=rm_transaction_points($c,$spend,$completed);
    rm_capture_order($c,$order);
    $occur=(string)($order['completed_at']??$order['updated_at']??$order['created_at']??date('Y-m-d H:i:s'));if(strtotime($occur)===false)$occur=date('Y-m-d H:i:s');
    $ref='ORDER-'.$oid;$desc='Poin Royalti transaksi '.$oid;
    $st=mysqli_prepare($c,"INSERT IGNORE INTO customer_loyalty_point_ledger(user_id,order_id,entry_type,points_delta,spend_amount,reference,description,occurred_at) VALUES(?,?,'earn',?,?,?,?,?)");
    if(!$st){error_log('POINT_LEDGER_PREPARE_FAIL order='.$oid.' error='.mysqli_error($c));return ['awarded'=>false,'points'=>0,'reason'=>'ledger_prepare'];}
    mysqli_stmt_bind_param($st,'isiisss',$uid,$oid,$points,$spend,$ref,$desc,$occur);
    $ok=mysqli_stmt_execute($st);$err=mysqli_stmt_error($st);$applied=$ok&&mysqli_stmt_affected_rows($st)>0;mysqli_stmt_close($st);
    if(!$ok){error_log('POINT_LEDGER_WRITE_FAIL order='.$oid.' uid='.$uid.' error='.$err);return ['awarded'=>false,'points'=>0,'reason'=>'ledger_write'];}
    try{ri_point_sync_from_sources($c,$uid);}catch(Throwable $e){error_log('POINT_SYNC_AFTER_AWARD_NON_FATAL uid='.$uid.' order='.$oid.' '.$e->getMessage());}
    return ['awarded'=>$applied,'points'=>$applied?$points:0,'already_awarded'=>!$applied,'spend'=>$spend];
}
?>
