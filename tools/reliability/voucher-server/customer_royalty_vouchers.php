<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
require_once __DIR__.'/config.php';
require_once __DIR__.'/p0_security_helper.php';
require_once __DIR__.'/loyalty_helper.php';
try {
    $u=p0_require_customer($conn);$uid=(int)$u['id'];
    $d=json_decode(file_get_contents('php://input'),true) ?: $_GET;
    $total=max(0,(int)($d['order_total']??0));
    $fee=max(0,min($total,(int)($d['delivery_fee']??0)));
    $verified=cb_is_verified($conn,$uid);$out=[];
    $rows=rm_rows($conn,"SELECT r.id redemption_id,r.voucher_code,r.expires_at,c.* FROM customer_loyalty_redemptions r JOIN loyalty_reward_catalog c ON c.id=r.reward_id WHERE r.user_id=? AND r.status='issued' AND (r.expires_at IS NULL OR r.expires_at>=NOW()) ORDER BY r.id DESC LIMIT 100",'i',[$uid]);
    foreach ($rows as $r) {
        if (rm_ready($conn)) {
            $snap=rm_rows($conn,'SELECT terms_json FROM loyalty_voucher_snapshots WHERE redemption_id=?','i',[(int)$r['redemption_id']]);
            if ($snap) {$terms=json_decode($snap[0]['terms_json'],true);if(is_array($terms))$r=array_merge($r,$terms);}
        }
        $discount=ly_voucher_discount($r,$total,$fee);
        $r['eligible']=$verified && $discount>0;
        $r['discount']=$r['eligible']?$discount:0;
        $r['reason']=!$verified?'Verifikasi akun terlebih dahulu.':($total<(int)$r['min_order']?'Minimum transaksi Rp'.number_format((int)$r['min_order'],0,',','.').'.':($fee<=0?'Tentukan tujuan agar ongkir dapat dihitung.':($discount<=0?'Voucher belum menghasilkan potongan.':'')));
        $out[]=$r;
    }
    $response=['success'=>true,'vouchers'=>$out,'preview_only'=>true,'message'=>'Potongan akhir divalidasi ulang server saat memesan.'];
    // Optional direct-code preview uses the exact engine and delivery base of checkout.
    if (array_key_exists('voucher_code',$d)) {
        $code=strtoupper(trim((string)$d['voucher_code']));
        $service=(string)($d['service_type']??'TransFood');
        if (!in_array($service,['TransFood','TransRide','TransCar'],true)) throw new InvalidArgumentException('Layanan voucher tidak valid.');
        $validation=['code'=>$code,'eligible'=>false,'discount'=>0,'final_price'=>$total,'reason'=>''];
        if ($code!=='') {
            try {
                $v=ly_apply_voucher($conn,$uid,$code,$total,$fee,false);
                if (!$v) $v=rv_apply($conn,$uid,$code,$total,$service,false,$fee);
                $discount=max(0,(int)$v['discount']);
                $validation=array_merge($validation,['eligible'=>$discount>0,'discount'=>$discount,'final_price'=>$total-$discount,'title'=>$v['title']??'',
                    'reason'=>$discount>0?'Voucher siap digunakan.':'Voucher belum menghasilkan potongan.']);
            } catch (mysqli_sql_exception $e) {throw $e;}
            catch (Exception $e) {$validation['reason']=$e->getMessage();}
        }
        $response['code_validation']=$validation;
    }
    echo json_encode($response,JSON_UNESCAPED_UNICODE);
} catch (Throwable $e) {
    error_log('ROYALTY_VOUCHERS '.$e->getMessage());
    http_response_code(503);
    echo json_encode(['success'=>false,'message'=>'Voucher belum dapat dimuat. Coba lagi.']);
}
