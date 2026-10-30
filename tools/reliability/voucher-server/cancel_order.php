<?php
header("Content-Type: application/json; charset=utf-8");
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
require_once __DIR__ . "/config.php";
require_once __DIR__ . "/p0_security_helper.php";
require_once __DIR__."/order_reliability_helper.php";
require_once __DIR__."/pickup_payment_helper.php";
$auth = p0_require_customer($conn);
p0_rate_limit($conn, "cancel_order", "u:".(int)$auth["id"], 12, 60);

require_once __DIR__ . "/transiva_pay_helper.php";
require_once __DIR__ . "/loyalty_season_helper.php";
require_once __DIR__ . "/customer_coin_helper.php";

function out($success,$message,$extra=[]){ echo json_encode(array_merge(["success"=>$success,"message"=>$message],$extra), JSON_UNESCAPED_UNICODE); exit; }
function esc($conn,$v){ return mysqli_real_escape_string($conn, trim((string)($v ?? ""))); }
function hasTable($conn,$table){ $t=esc($conn,$table); $q=mysqli_query($conn,"SHOW TABLES LIKE '$t'"); return $q && mysqli_num_rows($q)>0; }
function hasCol($conn,$table,$col){ $t=esc($conn,$table); $c=esc($conn,$col); $q=mysqli_query($conn,"SHOW COLUMNS FROM `$t` LIKE '$c'"); return $q && mysqli_num_rows($q)>0; }
function noteArray($note){ $d=json_decode(trim((string)$note),true); return is_array($d)?$d:[]; }
function isFoodBalancePaid($order){ $n=noteArray($order["note"]??""); $ot=strtolower(trim((string)($order["order_type"]??""))); $nt=strtolower(trim((string)($n["type"]??""))); $pm=strtolower(trim((string)($n["payment_method"]??""))); $ps=strtolower(trim((string)($n["payment_status"]??""))); return ($ot==="food"||$nt==="food") && in_array($pm,["balance","saldo","wallet","transiva_pay","transivapay"],true) && in_array($ps,["paid","paid_held","held"],true); }
function walletTableExists($conn){ $q=mysqli_query($conn,"SHOW TABLES LIKE 'wallet_transactions'"); return $q && mysqli_num_rows($q)>0; }
function refundFoodIfNeeded($conn,$order){
  if(!isFoodBalancePaid($order)) return ["refunded"=>false,"amount"=>0,"balance"=>null];
  $userId=intval($order["user_id"]??0); $orderId=esc($conn,$order["order_id"]??""); $amount=intval($order["price"]??0);
  if($userId<=0 || $orderId==="" || $amount<=0) throw new Exception("Data refund tidak valid");
  if(walletTableExists($conn)){ $ref=esc($conn,"REFUND-".$orderId); $cq=mysqli_query($conn,"SELECT id FROM wallet_transactions WHERE user_id='$userId' AND type='refund' AND reference='$ref' LIMIT 1"); if($cq && mysqli_num_rows($cq)>0) return ["refunded"=>false,"amount"=>0,"balance"=>null,"already_refunded"=>true]; }
  $uq=mysqli_query($conn,"SELECT id,balance FROM users WHERE id='$userId' LIMIT 1 FOR UPDATE"); if(!$uq || mysqli_num_rows($uq)<1) throw new Exception("Customer tidak ditemukan untuk refund");
  $u=mysqli_fetch_assoc($uq); $before=intval($u["balance"]??0); $after=$before+$amount;
  if(!mysqli_query($conn,"UPDATE users SET balance=balance+$amount WHERE id='$userId' LIMIT 1")) throw new Exception("Gagal refund saldo");
  if(walletTableExists($conn)){ $ref=esc($conn,"REFUND-".$orderId); $desc=esc($conn,"Refund pembatalan pesanan food ".$orderId); mysqli_query($conn,"INSERT INTO wallet_transactions (user_id,type,amount,balance_before,balance_after,status,reference,description,created_at) VALUES ('$userId','refund','$amount','$before','$after','success','$ref','$desc',NOW())"); }
  return ["refunded"=>true,"amount"=>$amount,"balance"=>$after];
}

$data=json_decode(file_get_contents("php://input"),true); if(!is_array($data)) out(false,"Data kosong");
$orderId=esc($conn,$data["order_id"]??""); $table=strtolower(esc($conn,$data["table"]??"")); $userId=(int)$auth["id"]; $username=esc($conn,(string)$auth["username"]);
if($orderId==="") out(false,"Order ID kosong");
$candidates = (strpos($table,"pickup")!==false) ? ["pickup_orders","orders"] : ["orders","pickup_orders"];
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
try{
  order_require_transactional($conn,["users","orders","wallet_transactions","customer_coin_ledger","order_cancellations"]);
  mysqli_begin_transaction($conn);
  foreach($candidates as $t){
    if(!hasTable($conn,$t)) continue;
    order_require_transactional($conn,[$t]);
    $q=mysqli_query($conn,"SELECT * FROM `$t` WHERE order_id='$orderId' LIMIT 1 FOR UPDATE");
    if(!$q || mysqli_num_rows($q)<1) continue;
    $order=mysqli_fetch_assoc($q); $status=strtolower(trim((string)($order["status"]??"")));
    if($userId>0 && isset($order["user_id"]) && intval($order["user_id"]) !== $userId) throw new Exception("Order bukan milik customer ini");
    if($username!=="" && $t==="pickup_orders" && isset($order["username"]) && trim((string)$order["username"])!=="" && trim((string)$order["username"])!==$username) throw new Exception("Order bukan milik customer ini");
    if(in_array($status,["canceled","cancelled"],true)) {
      if($t==='orders'){require_once __DIR__.'/loyalty_helper.php';ly_restore_cancelled_voucher($conn,$userId,(string)$order['order_id']);}
      mysqli_commit($conn);out(true,"Order sudah dibatalkan",["order"=>["order_id"=>$orderId,"status"=>$status,"source"=>$t],"already_canceled"=>true]);
    }
    if(in_array($status,["finished","completed","finish"],true)) throw new Exception("Order sudah selesai dan tidak bisa dibatalkan");
    $allowed = ($t==="pickup_orders") ? ["pending"] : ["pending","scheduled","merchant_accepted"];
    if(!in_array($status,$allowed,true)) throw new Exception("Order tidak bisa dibatalkan karena sudah diproses driver");

    $refund=["refunded"=>false,"amount"=>0,"balance"=>null];
    if($t==="orders") $refund=tp_refund_held_order($conn,$order,"Refund pembatalan pesanan ".$orderId);
    else $refund=pickup_transfer_held($conn,$order,$userId,true);
    $coinRefund = $t==="orders" ? coin_refund_order($conn,$userId,$orderId) : ["refunded"=>false,"coins"=>0];
    $note=noteArray($order['note']??'');$note['canceled_by']='customer';$note['canceled_at']=date('c');
    $auditJson=json_encode(['order'=>$order,'refund'=>$refund,'coin_refund'=>$coinRefund],JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
    $audit=mysqli_prepare($conn,'INSERT INTO order_cancellations(source_table,order_id,user_id,details_json) VALUES(?,?,?,?)');
    $rawOrderId=(string)$order['order_id'];mysqli_stmt_bind_param($audit,'ssis',$t,$rawOrderId,$userId,$auditJson);mysqli_stmt_execute($audit);mysqli_stmt_close($audit);
    $del=mysqli_query($conn,"UPDATE `$t` SET status='canceled' WHERE order_id='$orderId' LIMIT 1");
    if(!$del || mysqli_affected_rows($conn)!==1) throw new Exception("Order gagal dibatalkan atau status sudah berubah");
    if($t==='orders'){require_once __DIR__.'/loyalty_helper.php';ly_restore_cancelled_voucher($conn,$userId,(string)$order['order_id']);}
    mysqli_commit($conn);
    out(true, $t==="pickup_orders" ? "Order pickup berhasil dibatalkan" : ($refund["refunded"] ? "Order dibatalkan dan saldo sudah dikembalikan" : "Order berhasil dibatalkan"), ["order"=>["order_id"=>$orderId,"status"=>"canceled","_transiva_table"=>$t,"source"=>$t],"refund"=>$refund,"coin_refund"=>$coinRefund]);
  }
  throw new Exception("Order tidak ditemukan");
}catch(Throwable $e){ mysqli_rollback($conn); error_log("CANCEL_ORDER: ".$e->getMessage()); if($e instanceof mysqli_sql_exception){http_response_code(503);out(false,"Pembatalan belum tersimpan. Periksa aktivitas dan coba kembali.");} out(false,$e->getMessage()); }
