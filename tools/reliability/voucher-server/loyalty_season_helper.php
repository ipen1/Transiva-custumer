<?php
declare(strict_types=1);


require_once __DIR__.'/schema_runtime_guard.php';
require_once __DIR__.'/transiva_point_helper.php';
/** Hybrid loyalty season engine. Lifetime remains permanent; active tier follows server-time seasons. */
function ls_table_exists(mysqli $c,string $table):bool{
    if(!preg_match('/^[A-Za-z0-9_]+$/',$table)) return false;
    $esc=mysqli_real_escape_string($c,$table);
    $q=transiva_schema_query($c,"SHOW TABLES LIKE '$esc'");
    if(!$q) return false;
    $ok=mysqli_num_rows($q)>0;
    mysqli_free_result($q);
    return $ok;
}
function ls_column_exists(mysqli $c,string $table,string $column):bool{
    $s=mysqli_prepare($c,"SELECT COUNT(*) n FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?");
    if(!$s) return false;
    mysqli_stmt_bind_param($s,'ss',$table,$column);
    mysqli_stmt_execute($s);
    $r=mysqli_fetch_assoc(mysqli_stmt_get_result($s));
    mysqli_stmt_close($s);
    return (int)($r['n']??0)>0;
}
function ls_ensure(mysqli $c):void{
    static $schemaReady=[];$schemaKey=spl_object_id($c);if(isset($schemaReady[$schemaKey]))return;
    transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS loyalty_season_settings (
      id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
      season_name VARCHAR(80) NOT NULL DEFAULT 'Transiva Season',
      anchor_date DATE NOT NULL,
      duration_months TINYINT UNSIGNED NOT NULL DEFAULT 6,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $year=(int)date('Y');
    $anchor=sprintf('%04d-01-01',$year);
    $st=mysqli_prepare($c,"INSERT IGNORE INTO loyalty_season_settings(id,season_name,anchor_date,duration_months) VALUES(1,'Transiva Season',?,6)");
    if($st){mysqli_stmt_bind_param($st,'s',$anchor);mysqli_stmt_execute($st);mysqli_stmt_close($st);}

    transiva_schema_query($c,"CREATE TABLE IF NOT EXISTS loyalty_season_tier_rules (
      tier VARCHAR(20) NOT NULL PRIMARY KEY,
      min_points INT NOT NULL DEFAULT 0,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $defaults=['BRONZE'=>0,'SILVER'=>500,'GOLD'=>1500,'DIAMOND'=>3000,'PLATINUM'=>5000];
    $st=mysqli_prepare($c,"INSERT IGNORE INTO loyalty_season_tier_rules(tier,min_points) VALUES(?,?)");
    if($st){foreach($defaults as $tier=>$points){mysqli_stmt_bind_param($st,'si',$tier,$points);mysqli_stmt_execute($st);}mysqli_stmt_close($st);}
    $schemaReady[$schemaKey]=true;
}
function ls_rules(mysqli $c):array{
    ls_ensure($c);
    $rows=[];
    $q=transiva_schema_query($c,"SELECT tier,min_points,updated_at FROM loyalty_season_tier_rules ORDER BY FIELD(tier,'BRONZE','SILVER','GOLD','DIAMOND','PLATINUM')");
    while($q&&($r=mysqli_fetch_assoc($q))) $rows[]=['name'=>strtoupper((string)$r['tier']),'points'=>(int)$r['min_points'],'updated_at'=>$r['updated_at']??null];
    if(!$rows) $rows=[['name'=>'BRONZE','points'=>0],['name'=>'SILVER','points'=>500],['name'=>'GOLD','points'=>1500],['name'=>'DIAMOND','points'=>3000],['name'=>'PLATINUM','points'=>5000]];
    return $rows;
}
function ls_tier_from_points(mysqli $c,int $points):array{
    $rules=ls_rules($c); $tier='BRONZE'; $floor=0; $next=0; $nextName='';
    foreach($rules as $i=>$r){
        $min=(int)$r['points'];
        if($points >= $min){$tier=(string)$r['name'];$floor=$min;}
        elseif($next===0){$next=$min;$nextName=(string)$r['name'];break;}
    }
    if($tier==='PLATINUM'){$next=0;$nextName='';}
    return [$tier,$floor,$next,$nextName,$rules];
}
function ls_period(mysqli $c):array{
    ls_ensure($c);
    $q=transiva_schema_query($c,"SELECT season_name,anchor_date,duration_months,updated_at FROM loyalty_season_settings WHERE id=1 LIMIT 1");
    $r=$q?mysqli_fetch_assoc($q):null;
    $name=trim((string)($r['season_name']??'Transiva Season')) ?: 'Transiva Season';
    $duration=max(1,min(24,(int)($r['duration_months']??6)));
    $anchorRaw=(string)($r['anchor_date']??date('Y').'-01-01');
    try{$anchor=new DateTimeImmutable($anchorRaw.' 00:00:00');}catch(Throwable $e){$anchor=new DateTimeImmutable(date('Y').'-01-01 00:00:00');}
    $now=new DateTimeImmutable('now');
    if($now < $anchor){$start=$anchor;}
    else{
        $months=((int)$now->format('Y')-(int)$anchor->format('Y'))*12 + ((int)$now->format('n')-(int)$anchor->format('n'));
        $steps=(int)floor(max(0,$months)/$duration);
        $start=$anchor->modify('+'.($steps*$duration).' months');
        while($now < $start) $start=$start->modify('-'.$duration.' months');
        while($now >= $start->modify('+'.$duration.' months')) $start=$start->modify('+'.$duration.' months');
    }
    $endExclusive=$start->modify('+'.$duration.' months');
    $end=$endExclusive->modify('-1 day');
    $periodLabel=$start->format('d M Y').' - '.$end->format('d M Y');
    return [
      'name'=>$name,
      'label'=>$name.' • '.$periodLabel,
      'start'=>$start->format('Y-m-d'),
      'end'=>$end->format('Y-m-d'),
      'end_exclusive'=>$endExclusive->format('Y-m-d'),
      'duration_months'=>$duration,
      'server_now'=>$now->format('Y-m-d H:i:s'),
      'updated_at'=>$r['updated_at']??null
    ];
}
function ls_user_stats(mysqli $c,int $uid):array{
    $period=ls_period($c);
    if(function_exists('pt_ready')&&pt_ready($c)){
        $v=pt_season_values($c,$uid,$period['start'].' 00:00:00',$period['end_exclusive'].' 00:00:00');
        $points=max(0,(int)$v['points']);[$tier,$floor,$next,$nextName,$rules]=ls_tier_from_points($c,$points);
        return ['season'=>$period,'season_points'=>$points,'season_transaction_points'=>$points-(int)$v['bonus'],'season_bonus_points'=>(int)$v['bonus'],'season_spend'=>(int)$v['spent'],'season_completed_orders'=>(int)$v['orders'],'season_tier'=>$tier,'season_tier_floor'=>$floor,'season_next_tier_points'=>$next,'season_next_tier_name'=>$nextName,'season_tiers'=>$rules];
    }
    $spend=0; $count=0; $bonus=0; $transactionPoints=0;
    $from=$period['start'].' 00:00:00'; $to=$period['end_exclusive'].' 00:00:00';

    // Prefer immutable Royalti ledger. Operational order rows may be archived after 3 months.
    if(ls_table_exists($c,'customer_loyalty_point_ledger')){
        $st=mysqli_prepare($c,"SELECT COALESCE(SUM(points_delta),0) p,COALESCE(SUM(spend_amount),0) spent,COUNT(*) c FROM customer_loyalty_point_ledger WHERE user_id=? AND entry_type='earn' AND occurred_at>=? AND occurred_at<?");
        if($st){mysqli_stmt_bind_param($st,'iss',$uid,$from,$to);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$transactionPoints=(int)($r['p']??0);$spend=(int)($r['spent']??0);$count=(int)($r['c']??0);}
    }else{
        // Backward-compatible fallback before the V2 ledger has been created.
        $st=mysqli_prepare($c,"SELECT COUNT(*) c,COALESCE(SUM(GREATEST(price,0)),0) spent FROM orders WHERE user_id=? AND created_at>=? AND created_at<? AND LOWER(status) IN ('finished','completed','done','delivered','finish','selesai')");
        if($st){mysqli_stmt_bind_param($st,'iss',$uid,$from,$to);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$count=(int)($r['c']??0);$spend=(int)round((float)($r['spent']??0));}
        $transactionPoints=(int)floor($spend/1000);
    }
    if(ls_table_exists($c,'customer_loyalty_bonus') && ls_column_exists($c,'customer_loyalty_bonus','points') && ls_column_exists($c,'customer_loyalty_bonus','created_at')){
        $st=mysqli_prepare($c,"SELECT COALESCE(SUM(points),0) bonus FROM customer_loyalty_bonus WHERE user_id=? AND created_at>=? AND created_at<?");
        if($st){mysqli_stmt_bind_param($st,'iss',$uid,$from,$to);mysqli_stmt_execute($st);$r=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);$bonus=(int)($r['bonus']??0);}
    }
    $points=$transactionPoints+$bonus;
    [$tier,$floor,$next,$nextName,$rules]=ls_tier_from_points($c,$points);
    return [
      'season'=>$period,'season_points'=>$points,'season_transaction_points'=>$transactionPoints,'season_bonus_points'=>$bonus,
      'season_spend'=>$spend,'season_completed_orders'=>$count,'season_tier'=>$tier,'season_tier_floor'=>$floor,
      'season_next_tier_points'=>$next,'season_next_tier_name'=>$nextName,'season_tiers'=>$rules
    ];
}

?>
