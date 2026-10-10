<?php
declare(strict_types=1);
require_once __DIR__.'/transiva_pay_helper.php';
require_once __DIR__.'/order_reliability_helper.php';
/** Caller must own a transaction and lock the pickup order row before transferring escrow. */
function pickup_transfer_held(mysqli $conn,array $order,int $recipient,bool $refund): array {
    // Always re-read under the order lock; stale caller snapshots must never pay twice.
    $id=(int)$order['id'];
    $locked=$conn->query("SELECT * FROM pickup_orders WHERE id=$id FOR UPDATE")->fetch_assoc();
    if(!$locked)throw new RuntimeException('Pesanan tidak ditemukan.');
    $order=$locked;
    $amount=(int)($order['escrow_amount']??0);
    if($amount<=0 || ($order['driver_settlement_status']??'')!=='held')return ['refunded'=>false,'released'=>false,'amount'=>0];
    if(!tp_is_balance_method(tp_get_payment_method($order)))throw new RuntimeException('Escrow tidak sesuai metode pembayaran.');
    if($refund && in_array(strtolower((string)$order['status']),['finished','completed','finish'],true))throw new RuntimeException('Pesanan selesai tidak dapat direfund.');
    if($recipient<=0)throw new RuntimeException('Penerima saldo tidak valid.');
    // A held state with an existing terminal ledger is inconsistent: never guess and pay again.
    $releaseRef='RELEASE-SEND-'.$order['order_id'];$refundRef='REFUND-SEND-'.$order['order_id'];
    $ledger=$conn->prepare("SELECT id FROM wallet_transactions WHERE reference IN (?,?) AND type IN ('refund','escrow_release') AND status='success' LIMIT 1");
    $ledger->bind_param('ss',$releaseRef,$refundRef);$ledger->execute();$exists=$ledger->get_result()->num_rows>0;$ledger->close();
    if($exists)throw new DomainException('Riwayat escrow tidak konsisten; hubungi admin untuk rekonsiliasi.');
    $st=mysqli_prepare($conn,'SELECT balance FROM users WHERE id=? FOR UPDATE');
    mysqli_stmt_bind_param($st,'i',$recipient);mysqli_stmt_execute($st);$row=mysqli_fetch_assoc(mysqli_stmt_get_result($st));mysqli_stmt_close($st);
    if(!$row)throw new RuntimeException('Akun penerima saldo tidak ditemukan.');
    $before=(int)$row['balance'];$after=$before+$amount;
    $st=mysqli_prepare($conn,'UPDATE users SET balance=balance+? WHERE id=?');mysqli_stmt_bind_param($st,'ii',$amount,$recipient);mysqli_stmt_execute($st);mysqli_stmt_close($st);
    $type=$refund?'refund':'escrow_release';$reference=($refund?'REFUND-':'RELEASE-').'SEND-'.$order['order_id'];
    if(!tp_add_wallet_log($conn,$recipient,$type,$amount,$before,$after,$reference,'TransSend '.$order['order_id']))throw new RuntimeException('Gagal menyimpan mutasi saldo TransSend.');
    $settlement=$refund?'refunded':'released';$payment=$refund?'refunded':'paid';$id=(int)$order['id'];
    $st=mysqli_prepare($conn,'UPDATE pickup_orders SET escrow_amount=0,driver_settlement_status=?,payment_status=? WHERE id=? AND driver_settlement_status=\'held\'');
    mysqli_stmt_bind_param($st,'ssi',$settlement,$payment,$id);mysqli_stmt_execute($st);if(mysqli_stmt_affected_rows($st)!==1)throw new RuntimeException('Status saldo TransSend berubah.');mysqli_stmt_close($st);
    return ['refunded'=>$refund,'released'=>!$refund,'amount'=>$amount,'balance'=>$after];
}

/** Both finish endpoints call this inside their locked transaction, before updating status. */
function pickup_finish_payment_locked(mysqli $conn,array $order,array $auth,string $otp): array {
    $owner=trim((string)($order['driver_username']??''));
    if($owner==='' || !hash_equals($owner,(string)$auth['username']))throw new DomainException('Order bukan milik driver ini.');
    $expected=trim((string)($order['delivery_otp']??''));
    if($expected==='' || $otp==='' || !hash_equals($expected,$otp))throw new DomainException('OTP penerima salah.');
    $state=strtolower(trim((string)$order['status']));
    if(in_array($state,['finished','completed','finish'],true)){
        if(($order['driver_settlement_status']??'')==='held')throw new DomainException('Pesanan lama memerlukan rekonsiliasi escrow; hubungi admin.');
        return ['released'=>false,'amount'=>0,'idempotent'=>true];
    }
    if($state!=='arrived_delivery')throw new DomainException('Driver harus tiba di lokasi pengantaran sebelum finish.');
    if((int)($order['customer_received']??0)!==1)throw new DomainException('Tunggu konfirmasi Terima Pesanan dari customer.');
    if(strtolower((string)($order['price_change_status']??''))==='pending')throw new DomainException('Perubahan harga belum dikonfirmasi.');
    $balance=tp_is_balance_method(tp_get_payment_method($order));
    if($balance && (($order['driver_settlement_status']??'')!=='held' || (int)($order['escrow_amount']??0)<=0))throw new DomainException('Escrow belum valid; hubungi admin.');
    $id=(int)$order['id'];
    // Waiting charges are already held in escrow. Pay the entire remaining escrow ONCE.
    $bill=$conn->query("SELECT * FROM smart_waiting_bills WHERE source='pickup_orders' AND order_id=$id FOR UPDATE")->fetch_assoc();
    if($balance && $bill){
        $due=max(0,(int)$bill['applied_fee']-(int)$bill['credited_fee']);
        if($due>(int)$order['escrow_amount'])throw new DomainException('Biaya tunggu melebihi escrow.');
    }
    $result=pickup_transfer_held($conn,$order,(int)$auth['id'],false);
    if($balance && $bill)$conn->query("UPDATE smart_waiting_bills SET credited_fee=applied_fee WHERE source='pickup_orders' AND order_id=$id");
    return $result;
}
