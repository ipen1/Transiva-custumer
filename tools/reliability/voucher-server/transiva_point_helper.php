<?php
declare(strict_types=1);
require_once __DIR__.'/royalty_mission_helper.php';
/** One spendable ledger for missions, order rewards, direct discounts, and vouchers. */
function pt_ready(mysqli $c):bool {
    static $ready=[];$key=spl_object_id($c);if(!empty($ready[$key]))return true;
    $r=rm_rows($c,"SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='transiva_point_settings'");
    if(!$r)return false;$cfg=rm_rows($c,'SELECT migration_completed FROM transiva_point_settings WHERE id=1');if((int)($cfg[0]['migration_completed']??0)===1){$ready[$key]=true;return true;}return false;
}
function pt_require(mysqli $c):void {if(!pt_ready($c))throw new RuntimeException('Jalankan migrasi Poin Transiva terlebih dahulu.');}
function pt_settings(mysqli $c):array {
    pt_require($c);$r=rm_rows($c,'SELECT c.*,p.points_per_unit,p.cutover_at FROM customer_coin_settings c JOIN transiva_point_settings p ON p.id=c.id WHERE c.id=1')[0];
    return ['spend_per_coin'=>max(1,(int)$r['spend_per_coin']),'coin_value_rupiah'=>1,'min_redeem_coins'=>max(1,(int)$r['min_redeem_coins']),
      'min_order_after_discount'=>max(0,(int)$r['min_order_after_discount']),'max_redeem_percent'=>max(1,min(100,(int)$r['max_redeem_percent'])),
      'is_enabled'=>(bool)$r['is_enabled'],'spend_per_point'=>max(1,(int)$r['spend_per_coin']),'points_per_unit'=>max(1,(int)$r['points_per_unit']),
      'point_value_rupiah'=>1,'min_redeem_points'=>max(1,(int)$r['min_redeem_coins']),'cutover_at'=>$r['cutover_at'],'asset_name'=>'Poin Transiva'];
}
function pt_balance(mysqli $c,int $uid,bool $current=false):int {
    pt_require($c);
    // A current locking read prevents stale RR snapshots from overspending a shared balance.
    if($current){$rows=rm_rows($c,'SELECT points_delta FROM transiva_point_ledger WHERE user_id=? FOR UPDATE','i',[$uid]);$n=0;foreach($rows as $r)$n+=(int)$r['points_delta'];return max(0,$n);}
    $r=rm_rows($c,'SELECT COALESCE(SUM(points_delta),0) balance FROM transiva_point_ledger WHERE user_id=?','i',[$uid]);return max(0,(int)$r[0]['balance']);
}
function pt_lock(mysqli $c,int $uid):void {$s=rm_query($c,'SELECT id FROM users WHERE id=? FOR UPDATE','i',[$uid]);mysqli_stmt_close($s);}
function pt_tx(mysqli $c,callable $fn){
    $active=(int)rm_rows($c,'SELECT @@in_transaction active')[0]['active']===1;
    if(!$active)mysqli_begin_transaction($c);
    try{$out=$fn();if(!$active)mysqli_commit($c);return $out;}catch(Throwable $e){if(!$active)mysqli_rollback($c);throw $e;}
}
function pt_insert(mysqli $c,int $uid,string $kind,int $delta,int $tier,string $ref,string $description,string $when,?string $oid=null,string $source='orders',int $spend=0,int $reserved=0):bool {
    $s=rm_query($c,'INSERT IGNORE INTO transiva_point_ledger(user_id,entry_kind,points_delta,tier_delta,reference,description,occurred_at,order_id,source_table,spend_amount,reserved_points) VALUES(?,?,?,?,?,?,?,?,?,?,?)','isiisssssii',[$uid,$kind,$delta,$tier,$ref,$description,$when,$oid,$source,$spend,$reserved]);$ok=mysqli_stmt_affected_rows($s)===1;mysqli_stmt_close($s);return $ok;
}
function pt_season_values(mysqli $c,int $uid,string $from,string $to):array {
    $r=rm_rows($c,"SELECT COALESCE(SUM(tier_delta),0) points,COALESCE(SUM(spend_amount),0) spent,SUM(CASE WHEN entry_kind IN ('order_earn','legacy_status') AND order_id IS NOT NULL THEN 1 ELSE 0 END) orders,COALESCE(SUM(CASE WHEN entry_kind IN ('mission','legacy_bonus') THEN tier_delta ELSE 0 END),0) bonus FROM transiva_point_ledger WHERE user_id=? AND occurred_at>=? AND occurred_at<?",'iss',[$uid,$from,$to])[0];return $r;
}
function pt_source_values(mysqli $c,int $uid):array {
    $r=rm_rows($c,"SELECT COALESCE(SUM(tier_delta),0) earned,COALESCE(SUM(points_delta),0) balance,COALESCE(SUM(CASE WHEN points_delta<0 THEN -points_delta ELSE 0 END),0) used,COALESCE(SUM(spend_amount),0) spent,SUM(CASE WHEN entry_kind IN ('order_earn','legacy_status') AND order_id IS NOT NULL THEN 1 ELSE 0 END) orders,COALESCE(SUM(CASE WHEN entry_kind IN ('mission','legacy_bonus') THEN tier_delta ELSE 0 END),0) bonus FROM transiva_point_ledger WHERE user_id=?",'i',[$uid])[0];
    return ['earned'=>max(0,(int)$r['earned']),'available'=>max(0,(int)$r['balance']),'used'=>(int)$r['used'],'spend'=>(int)$r['spent'],'count'=>(int)$r['orders'],'bonus'=>(int)$r['bonus'],'transaction_points'=>max(0,(int)$r['earned']-(int)$r['bonus'])];
}
function pt_tier(mysqli $c,int $uid):string {$period=ls_period($c);$s=pt_season_values($c,$uid,$period['start'].' 00:00:00',$period['end_exclusive'].' 00:00:00');return ls_tier_from_points($c,(int)$s['points'])[0];}
function pt_status(mysqli $c,int $uid):array {$cfg=pt_settings($c);$balance=pt_balance($c,$uid);$tier=pt_tier($c,$uid);return $cfg+['balance'=>$balance,'points_balance'=>$balance,'tier'=>$tier,'earn_percent'=>coin_earn_percent($c,$tier),'can_redeem'=>$cfg['is_enabled']&&$balance>=$cfg['min_redeem_points'],'balance_value_rupiah'=>$balance];}
function pt_quote(mysqli $c,int $uid,int $amount,bool $current=false):array {
    $s=pt_status($c,$uid);if($current){$s['balance']=pt_balance($c,$uid,true);$s['points_balance']=$s['balance'];}
    if(!$s['is_enabled'])throw new DomainException('Pemakaian Poin Transiva sedang dinonaktifkan.');
    if($s['balance']<$s['min_redeem_points'])throw new DomainException('Minimal penggunaan '.$s['min_redeem_points'].' poin. Saldo kamu '.$s['balance'].' poin.');
    $points=min($s['balance'],max(0,$amount-$s['min_order_after_discount']),(int)floor(max(0,$amount)*$s['max_redeem_percent']/100));
    if($points<$s['min_redeem_points'])throw new DomainException('Nilai transaksi belum cukup untuk memakai poin.');
    return ['points'=>$points,'coins'=>$points,'discount'=>$points,'balance_before'=>$s['balance'],'balance_after'=>$s['balance']-$points]+$s;
}
function pt_pay(mysqli $c,int $uid,string $oid,int $amount):array {
    return pt_tx($c,function()use($c,$uid,$oid,$amount){pt_lock($c,$uid);
        $old=rm_rows($c,"SELECT reserved_points FROM transiva_point_ledger WHERE user_id=? AND reference=? FOR UPDATE",'is',[$uid,'PAY-'.$oid]);
        if($old){$n=(int)$old[0]['reserved_points'];return ['points'=>$n,'coins'=>$n,'discount'=>$n,'idempotent'=>true];}
        $q=pt_quote($c,$uid,$amount,true);pt_insert($c,$uid,'payment',-$q['points'],0,'PAY-'.$oid,'Pemakaian Poin Transiva',date('Y-m-d H:i:s'),$oid,'orders',0,$q['points']);return $q;
    });
}
function pt_refund(mysqli $c,int $uid,string $oid):array {
    return pt_tx($c,function()use($c,$uid,$oid){pt_lock($c,$uid);$r=rm_rows($c,"SELECT reserved_points FROM transiva_point_ledger WHERE user_id=? AND reference IN (?,?) ORDER BY id LIMIT 1 FOR UPDATE",'iss',[$uid,'PAY-'.$oid,'LEGACY-PAY-'.$oid]);
        if(!$r)return ['refunded'=>false,'points'=>0,'coins'=>0];$n=(int)$r[0]['reserved_points'];$ok=pt_insert($c,$uid,'refund',$n,0,'REFUND-'.$oid,'Pengembalian Poin Transiva',date('Y-m-d H:i:s'),$oid);return ['refunded'=>$ok,'points'=>$ok?$n:0,'coins'=>$ok?$n:0];
    });
}
function pt_voucher_debit_locked(mysqli $c,int $uid,int $cost,int $redemption):void {
    if($cost<0||pt_balance($c,$uid,true)<$cost)throw new DomainException('Poin belum cukup.');
    if(!pt_insert($c,$uid,'voucher',-$cost,0,'VOUCHER-'.$redemption,'Penukaran voucher Royalti',date('Y-m-d H:i:s')))throw new DomainException('Penukaran sudah tersimpan.');
}
function pt_award_order(mysqli $c,array $order):array {
    pt_require($c);$uid=(int)($order['user_id']??$order['customer_id']??0);$oid=trim((string)($order['order_id']??''));$source=($order['_reward_source']??'orders')==='pickup_orders'?'pickup_orders':'orders';
    if($uid<=0||$oid==='')return ['awarded'=>false,'points'=>0,'coins'=>0];
    // Read the committed completed row; settlement callers may hold an old status/timestamp.
    $fresh=rm_rows($c,"SELECT * FROM `$source` WHERE order_id=? AND user_id=? AND LOWER(status) IN ('finished','completed','done','delivered','finish','selesai') LIMIT 1",'si',[$oid,$uid]);
    if(!$fresh)return ['awarded'=>false,'points'=>0,'coins'=>0,'reason'=>'not_finished'];$order=array_merge($order,$fresh[0]);$order['_reward_source']=$source;[$created,$done]=rm_order_dates($order);$cfg=pt_settings($c);
    // Old wallet balances have already been migrated. Never award an old order twice.
    if($done<$cfg['cutover_at']){rm_capture_order($c,$order);return ['awarded'=>false,'points'=>0,'coins'=>0,'reason'=>'before_cutover'];}
    return pt_tx($c,function()use($c,$uid,$oid,$order,$source,$done,$cfg){pt_lock($c,$uid);rm_capture_order($c,$order);
        $spend=coin_order_identity($order)[2];$tier=pt_tier($c,$uid);$pct=coin_earn_percent($c,$tier);$service=coin_service_key($order);$sr=coin_service_rate($c,$service);
        $base=(int)floor($spend/$cfg['spend_per_coin']);$points=(int)floor((int)floor($base*$pct/100)*$sr['earn_percent']/100)*$cfg['points_per_unit'];
        if(!$cfg['is_enabled']||!$sr['is_enabled'])$points=0;
        $ok=pt_insert($c,$uid,'order_earn',$points,$points,'ORDER-'.$source.'-'.$oid,'Poin pesanan '.$service,$done,$oid,$source,$spend);
        return ['awarded'=>$ok,'points'=>$ok?$points:0,'coins'=>$ok?$points:0,'already_awarded'=>!$ok,'balance'=>pt_balance($c,$uid,true),'tier'=>$tier];
    });
}
