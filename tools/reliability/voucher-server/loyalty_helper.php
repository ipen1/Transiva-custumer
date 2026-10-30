<?php
declare(strict_types=1);
require_once __DIR__ . '/customer_benefit_helper.php';
require_once __DIR__ . '/voucher_discount_rules.php';
require_once __DIR__ . '/ride_voucher_helper.php';
require_once __DIR__ . '/reward_integrity_helper.php';
function ly_table(mysqli $c,string $t):bool{
    if(!preg_match('/^[A-Za-z0-9_]+$/',$t)) return false;
    $esc=mysqli_real_escape_string($c,$t);$q=mysqli_query($c,"SHOW TABLES LIKE '$esc'");if(!$q)return false;$ok=mysqli_num_rows($q)>0;mysqli_free_result($q);return $ok;
}
function ly_tier(int $p):array{if($p>=5000)return ['PLATINUM',5000,0,''];if($p>=3000)return ['DIAMOND',3000,5000,'PLATINUM'];if($p>=1500)return ['GOLD',1500,3000,'DIAMOND'];if($p>=500)return ['SILVER',500,1500,'GOLD'];return ['BRONZE',0,500,'SILVER'];}
function ly_rank(string $t):int{return ['BRONZE'=>0,'SILVER'=>1,'GOLD'=>2,'DIAMOND'=>3,'PLATINUM'=>4][strtoupper($t)]??0;}
function ly_referral_tables(mysqli $c):void{}
function ly_process_referrals(mysqli $c,int $uid=0):void{
    // Cash referral v2 is reconciled after explicit settlement commits, via its GET endpoint or CLI worker.
    // Reading loyalty/tier stats must never start a nested wallet transaction.
}
function ly_stats(mysqli $c,int $uid):array{
    ly_process_referrals($c,$uid);
    // V2: lifetime Royalti points are read from the permanent point ledger, not orders.
    // This keeps points valid after order history is archived/deleted.
    $v=ri_point_source_values($c,$uid);
    $earned=(int)$v['earned'];$earnedBase=(int)$v['transaction_points'];$bonus=(int)$v['bonus'];$used=(int)$v['used'];
    $count=(int)$v['count'];$spent=(int)$v['spend'];
    [$lifetimeTier,$floor,$next,$nextName]=ls_tier_from_points($c,$earned);
    $season=ls_user_stats($c,$uid);
    ri_point_verify_or_init($c,$uid,(int)$v['available'],$earned);
    return array_merge([
      'earned_points'=>$earned,'transaction_points'=>$earnedBase,'bonus_points'=>$bonus,'points'=>(int)$v['available'],'points_used'=>$used,
      'tier'=>$season['season_tier'],'lifetime_tier'=>$lifetimeTier,'lifetime_tier_floor'=>$floor,'lifetime_next_tier_points'=>$next,'lifetime_next_tier_name'=>$nextName,
      'completed_orders'=>$count,'eligible_spend'=>$spent
    ],$season);
}

function ly_catalog(mysqli $c,array $stats):array{
    $out=[];if(!ly_table($c,'loyalty_reward_catalog'))return $out;
    $activeTier=(string)($stats['season_tier']??$stats['tier']??'BRONZE');
    $activePoints=(int)($stats['season_points']??0);
    $q=mysqli_query($c,"SELECT * FROM loyalty_reward_catalog WHERE is_active=1 ORDER BY unlock_points ASC,points_cost ASC,id ASC");
    while($q&&$r=mysqli_fetch_assoc($q)){$quota=(int)$r['quota_total'];$used=(int)$r['quota_used'];$r['quota_remaining']=$quota>0?max(0,$quota-$used):-1;$r['unlocked']=ly_rank($activeTier)>=ly_rank((string)$r['min_tier'])&&$activePoints>=(int)$r['unlock_points'];$r['can_redeem']=$r['unlocked']&&(int)$stats['points']>=(int)$r['points_cost']&&($quota<=0||$used<$quota);$out[]=$r;}
    return $out;
}
function ly_apply_voucher(mysqli $c,int $uid,string $code,int $orderTotal,int $deliveryFee,bool $lock=false):?array{$code=strtoupper(trim($code));if($code==='')return null;cb_assert_voucher_verified($c,$uid,$code);if(!ly_table($c,'customer_loyalty_redemptions'))return null;$sql="SELECT r.id redemption_id,r.voucher_code,r.status,r.expires_at,c.* FROM customer_loyalty_redemptions r JOIN loyalty_reward_catalog c ON c.id=r.reward_id WHERE r.user_id=? AND UPPER(r.voucher_code)=? AND r.status='issued' LIMIT 1".($lock?' FOR UPDATE':'');$st=mysqli_prepare($c,$sql);mysqli_stmt_bind_param($st,'is',$uid,$code);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);if(!$r)return null;if(rm_ready($c)){$snap=rm_rows($c,'SELECT terms_json FROM loyalty_voucher_snapshots WHERE redemption_id=?','i',[(int)$r['redemption_id']]);if($snap){$terms=json_decode($snap[0]['terms_json'],true);if(is_array($terms))$r=array_merge($r,$terms);}}if(!empty($r['expires_at'])&&strtotime($r['expires_at'])<time())throw new Exception('Voucher loyalty sudah kedaluwarsa');if($orderTotal<(int)$r['min_order'])throw new Exception('Minimal order voucher Rp '.number_format((int)$r['min_order'],0,',','.'));$discount=ly_voucher_discount($r,$orderTotal,$deliveryFee);return ['redemption_id'=>(int)$r['redemption_id'],'code'=>$r['voucher_code'],'title'=>$r['title'],'discount'=>$discount,'platform_subsidy'=>$discount,'original_delivery_fee'=>$deliveryFee];}
function ly_mark_used(mysqli $c,array $v,string $orderId):void{if(empty($v['redemption_id']))return;$id=(int)$v['redemption_id'];$st=mysqli_prepare($c,"UPDATE customer_loyalty_redemptions SET status='used',used_at=NOW(),order_id=? WHERE id=? AND status='issued' LIMIT 1");mysqli_stmt_bind_param($st,'si',$orderId,$id);mysqli_stmt_execute($st);if(mysqli_stmt_affected_rows($st)<1)throw new Exception('Voucher loyalty sudah digunakan');mysqli_stmt_close($st);}
?>
<?php
/** Shared catalogue/application formula. Percentage vouchers apply to delivery fee only. */
function ly_voucher_discount(array $r,int $total,int $delivery):int {
    return vd_delivery_discount($r,$total,$delivery);
}
function ly_restore_cancelled_voucher(mysqli $c,int $uid,string $oid):void {
    rv_restore_cancelled_voucher($c,$uid,$oid);
    if(!ly_table($c,'customer_loyalty_redemptions'))return;
    $s=rm_query($c,"UPDATE customer_loyalty_redemptions SET status='issued',used_at=NULL,order_id=NULL WHERE user_id=? AND order_id=? AND status='used'",'is',[$uid,$oid]);mysqli_stmt_close($s);
}
?>
