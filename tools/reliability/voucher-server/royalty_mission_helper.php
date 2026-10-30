<?php
declare(strict_types=1);
/** No runtime DDL. Import migrations/20261029_royalty_missions.sql first. */
function rm_query(mysqli $c,string $sql,string $types='',array $args=[]): mysqli_stmt {
    $s=mysqli_prepare($c,$sql); if(!$s)throw new RuntimeException('Royalti belum siap. Jalankan migrasi Royalti.');
    if($types!=='')mysqli_stmt_bind_param($s,$types,...$args);
    mysqli_stmt_execute($s);return $s;
}
function rm_rows(mysqli $c,string $sql,string $types='',array $args=[]):array {
    $s=rm_query($c,$sql,$types,$args);$r=mysqli_stmt_get_result($s);$out=[];
    while($r&&($x=mysqli_fetch_assoc($r)))$out[]=$x;mysqli_stmt_close($s);return $out;
}
function rm_ready(mysqli $c):bool {
    static $cache=[];$key=spl_object_id($c);if(isset($cache[$key]))return $cache[$key];
    $r=rm_rows($c,"SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='loyalty_mission_settings'");return $cache[$key]=(bool)$r;
}
function rm_settings(mysqli $c):array {
    if(!rm_ready($c))return ['effective_at'=>'9999-12-31 23:59:59','transaction_points_enabled'=>1,'spend_per_point'=>1000];
    return rm_rows($c,'SELECT * FROM loyalty_mission_settings WHERE id=1')[0];
}
function rm_order_dates(array $order):array {
    $created=(string)($order['created_at']??date('Y-m-d H:i:s'));
    $completed=(string)($order['completed_at']??$order['updated_at']??$created);
    if(!strtotime($created))$created=date('Y-m-d H:i:s');if(!strtotime($completed))$completed=$created;
    return [$created,$completed];
}
function rm_transaction_points(mysqli $c,int $spend,string $completed):int {
    $cfg=rm_settings($c);$unit=1000;
    if($completed>=$cfg['effective_at']){
        if(!(int)$cfg['transaction_points_enabled'])return 0;$unit=max(1,(int)$cfg['spend_per_point']);
    }
    return (int)floor(max(0,$spend)/$unit);
}
function rm_capture_order(mysqli $c,array $o):void {
    if(!rm_ready($c))return;
    if(!in_array(strtolower((string)($o['status']??'')),['finished','completed','done','delivered','finish','selesai'],true))return;
    $uid=(int)($o['user_id']??$o['customer_id']??0);$oid=trim((string)($o['order_id']??''));
    if($uid<=0||$oid==='')return;
    // Only settlement paths and a finished-row recovery may call this function.
    [$created,$done]=rm_order_dates($o);$cfg=rm_settings($c);if($done<$cfg['effective_at'])return;
    $source=(string)($o['_reward_source']??'orders');$spend=0;
    foreach(['original_price','price_before_coin','gross_total','total_price','total','price','amount'] as $k){if(isset($o[$k])&&is_numeric($o[$k])&&(float)$o[$k]>0){$spend=(int)round((float)$o[$k]);break;}}
    $s=rm_query($c,'INSERT IGNORE INTO loyalty_order_events(user_id,order_id,source_table,spend_amount,ordered_at,completed_at) VALUES(?,?,?,?,?,?)','ississ',[$uid,$oid,$source,$spend,$created,$done]);mysqli_stmt_close($s);
}
function rm_recover_orders(mysqli $c,int $uid):void {
    $cfg=rm_settings($c);
    foreach(['orders','pickup_orders'] as $table){
        if(!ri_table_exists($c,$table))continue;
        // Recover every missing event, in bounded batches. No old order becomes a new mission event.
        $batch=rm_rows($c,"SELECT o.* FROM `$table` o WHERE o.user_id=? AND o.created_at>=? AND LOWER(o.status) IN ('finished','completed','done','delivered','finish','selesai') AND NOT EXISTS(SELECT 1 FROM loyalty_order_events e WHERE e.source_table=? AND BINARY e.order_id=BINARY o.order_id) ORDER BY o.id LIMIT 500",'iss',[$uid,$cfg['effective_at'],$table]);
        foreach($batch as $o){$o['_reward_source']=$table;ri_point_award_finished_order($c,$o);}
    }
}
function rm_progress(mysqli $c,int $uid,array $m):array {
    return rm_rows($c,'SELECT id,completed_at FROM loyalty_order_events WHERE user_id=? AND ordered_at>=? AND completed_at>=? AND completed_at<=? AND spend_amount>=? AND completed_at<=NOW() ORDER BY completed_at,id LIMIT '.max(1,(int)$m['target_orders']),'isssi',[$uid,$m['starts_at'],$m['starts_at'],$m['ends_at'],(int)$m['min_order_amount']]);
}
/** Call outside settlement transactions. Serialize mission bonuses with voucher redemptions. */
function rm_sync(mysqli $c,int $uid):array {
    if($uid<=0||!rm_ready($c))return [];
    rm_recover_orders($c,$uid);
    mysqli_begin_transaction($c);
    try{
        $s=rm_query($c,'SELECT id FROM users WHERE id=? FOR UPDATE','i',[$uid]);mysqli_stmt_close($s);
        $missions=rm_rows($c,'SELECT m.* FROM loyalty_missions m WHERE m.is_active=1 AND m.starts_at<=NOW() AND NOT EXISTS(SELECT 1 FROM loyalty_mission_awards a WHERE a.user_id=? AND a.mission_id=m.id) AND (m.ends_at>=NOW() OR EXISTS(SELECT 1 FROM loyalty_order_events e WHERE e.user_id=? AND e.ordered_at>=m.starts_at AND e.completed_at<=m.ends_at AND e.spend_amount>=m.min_order_amount)) ORDER BY m.id','ii',[$uid,$uid]);$changed=false;
        foreach($missions as $m){
            $existing=rm_rows($c,'SELECT id FROM loyalty_mission_awards WHERE user_id=? AND mission_id=?','ii',[$uid,(int)$m['id']]);if($existing)continue;
            $events=rm_progress($c,$uid,$m);if(count($events)<(int)$m['target_orders'])continue;
            $last=end($events);$mid=(int)$m['id'];$pts=(int)$m['reward_points'];$desc='Misi: '.$m['title'];
            $s=rm_query($c,"INSERT INTO customer_loyalty_bonus(user_id,source_type,source_id,points,description,created_at) VALUES(?,'mission',?,?,?,?)",'iiiss',[$uid,$mid,$pts,$desc,$last['completed_at']]);mysqli_stmt_close($s);
            if(function_exists('pt_ready')&&pt_ready($c))pt_insert($c,$uid,'mission',$pts,$pts,'MISSION-'.$mid.'-'.$uid,$desc,$last['completed_at']);
            $s=rm_query($c,'INSERT INTO loyalty_mission_awards(user_id,mission_id,reward_points,order_event_id,completed_at) VALUES(?,?,?,?,?)','iiiis',[$uid,$mid,$pts,(int)$last['id'],$last['completed_at']]);mysqli_stmt_close($s);$changed=true;
        }
        if($changed)ri_point_sync_from_sources($c,$uid);
        mysqli_commit($c);
    }catch(Throwable $e){mysqli_rollback($c);throw $e;}
    return rm_status($c,$uid);
}
function rm_status(mysqli $c,int $uid):array {
    if(!rm_ready($c))return [];
    $missions=rm_rows($c,'SELECT m.*,a.id award_id,a.reward_points awarded_points FROM loyalty_missions m LEFT JOIN loyalty_mission_awards a ON a.mission_id=m.id AND a.user_id=? WHERE m.is_active=1 OR a.id IS NOT NULL ORDER BY m.ends_at DESC,m.id DESC LIMIT 100','i',[$uid]);$out=[];$now=date('Y-m-d H:i:s');
    foreach($missions as $m){
        $events=rm_progress($c,$uid,$m);$m['progress']=count($events);$m['target_orders']=(int)$m['target_orders'];$m['reward_points']=(int)$m['reward_points'];
        $m['status']=!empty($m['award_id'])?'completed':($now<$m['starts_at']?'upcoming':($now>$m['ends_at']?'expired':'active'));
        $m['progress_text']=$m['progress'].' dari '.$m['target_orders'].' pesanan';unset($m['award_id']);$out[]=$m;
    }return $out;
}
function rm_tier_copy(array $s,int $percent,int $bronzePercent):array {
    $points=(int)($s['season_points']??0);$next=(int)($s['season_next_tier_points']??0);$tier=ucfirst(strtolower((string)($s['season_tier']??'BRONZE')));
    $extra=$bronzePercent>0?(int)round(($percent/$bronzePercent-1)*100):0;
    return ['card_title'=>$tier.' • '.$points.($next>0?'/'.$next:'').' poin',
      'card_progress'=>$next>0?'Kurang '.max(0,$next-$points).' poin untuk naik level.':'Kamu sudah mencapai level tertinggi periode ini.',
      'card_benefit'=>$extra>0?$tier.' mendapat poin '.$extra.'% lebih banyak.':$tier.' mendapat poin dari setiap pesanan yang memenuhi syarat.'];
}
