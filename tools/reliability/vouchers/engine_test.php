<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
require_once __DIR__.'/db.php';
$server=dirname(__DIR__).'/voucher-server';
require_once $server.'/ride_voucher_helper.php';
require_once $server.'/loyalty_helper.php';
require_once $server.'/transiva_pay_helper.php';
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
function db():mysqli {return voucher_ci_db();}
$c=db();
function runsql(mysqli $c,string $sql):void {$c->multi_query($sql);do{if($r=$c->store_result())$r->free();}while($c->more_results()&&$c->next_result());}
runsql($c,file_get_contents(__DIR__.'/schema.sql'));
runsql($c,file_get_contents(__DIR__.'/refund-schema.sql'));
$c->query('DELETE FROM voucher_usage_refunds');
$c->query("INSERT INTO users(id,username,password,role,email_verified,verified_by_admin,balance) VALUES(1,'test_customer','x','customer',1,0,50000),(2,'test_customer_two','x','customer',1,0,50000),(3,'test_unverified','x','customer',0,0,50000),(4,'test_driver','x','driver',1,0,10000)");
$c->query("INSERT INTO customer_promos(id,title,description,promo_code,discount_type,discount_value,min_order,service_type,quota_total,per_user_limit) VALUES (1,'Food half','test','FOOD50','percent',50,20000,'TransFood',1,1),(2,'Free','test','FREE','percent',100,0,'TransRide',10,1),(3,'Ride only','test','RIDE','fixed',1000,0,'TransRide',10,1),(4,'Concurrent','test','RACE','fixed',1000,0,'TransFood',1,1)");
$c->query("INSERT INTO ride_vouchers(id,code,title,discount_type,discount_value,quota_total,quota_used,per_user_limit,service_type) VALUES(1,'OLD','Legacy','fixed',2000,1,0,1,'TransRide')");
$c->query("INSERT INTO loyalty_reward_catalog(id,code,title,discount_type,discount_value,min_order,points_cost) VALUES(1,'L1','Point voucher','percent',100,0,100)");
$c->query("INSERT INTO customer_loyalty_redemptions(id,user_id,reward_id,voucher_code,points_cost,status,expires_at) VALUES(1,1,1,'TRV-OWN',100,'issued',DATE_ADD(NOW(),INTERVAL 1 DAY)),(2,1,1,'TRV-EXPIRED',100,'issued',DATE_SUB(NOW(),INTERVAL 1 DAY))");
$tests=0;
function eq($got,$want,string $label):void {global $tests;if($got!==$want)throw new RuntimeException($label.' expected '.json_encode($want).' got '.json_encode($got));$tests++;echo "PASS $label\n";}
function rejected(callable $fn,string $label):void {try{$fn();}catch(Exception $e){if($e instanceof mysqli_sql_exception)throw $e;eq(true,true,$label);return;}throw new RuntimeException($label.' unexpectedly accepted');}
$v=rv_apply($c,1,'FOOD50',58000,'TransFood',false,8000);
eq($v['discount'],4000,'Food admin 50% applies to Rp8k delivery, not Rp58k order');
eq($v['final_price'],54000,'Food payable subtracts delivery-only discount');
eq(ly_voucher_discount(['discount_type'=>'percent','discount_value'=>50,'min_order'=>20000,'max_discount'=>0],58000,8000),$v['discount'],'Loyalty and admin formulas agree');
rejected(fn()=>rv_apply($c,1,'FOOD50',58000,'TransFood',false),'Food caller must supply delivery base');
rejected(fn()=>rv_apply($c,1,'FOOD50',18000,'TransFood',false,8000),'Full transaction minimum enforced');
rejected(fn()=>rv_apply($c,1,'RIDE',58000,'TransFood',false,8000),'Ride-only code rejected on food');
rejected(fn()=>rv_apply($c,3,'FREE',8000,'TransRide'),'Unverified user cannot use voucher');
eq(rv_apply($c,1,'FREE',8000,'TransRide')['final_price'],0,'Admin 100% voucher supports zero payable');
eq(ly_apply_voucher($c,1,'TRV-OWN',8000,8000)['discount'],8000,'Owned point voucher supports zero payable');
eq(ly_apply_voucher($c,2,'TRV-OWN',8000,8000),null,'Owned voucher cannot be used by another user');
rejected(fn()=>ly_apply_voucher($c,1,'TRV-EXPIRED',8000,8000),'Expired loyalty voucher rejected');
$c->begin_transaction();$v=rv_apply($c,1,'FOOD50',58000,'TransFood',true,8000);rv_record_usage($c,$v,1,'TEST-FOOD-1');$c->commit();
rejected(fn()=>rv_apply($c,1,'FOOD50',58000,'TransFood',false,8000),'Per-user usage limit enforced');
rejected(fn()=>rv_apply($c,2,'FOOD50',58000,'TransFood',false,8000),'Global promo quota enforced');
$c->begin_transaction();ly_restore_cancelled_voucher($c,1,'TEST-FOOD-1');ly_restore_cancelled_voucher($c,1,'TEST-FOOD-1');$c->commit();
eq((int)$c->query('SELECT COUNT(*) n FROM voucher_usage_refunds')->fetch_assoc()['n'],1,'Double cancellation restores quota once');
eq((int)$c->query('SELECT COUNT(*) n FROM customer_promo_usages')->fetch_assoc()['n'],1,'Cancellation retains original usage audit');
eq(rv_apply($c,2,'FOOD50',58000,'TransFood',false,8000)['discount'],4000,'Cancelled global quota becomes available again');
$c->begin_transaction();$v=rv_apply($c,1,'OLD',8000,'TransRide',true);rv_record_usage($c,$v,1,'TEST-OLD-1');$c->commit();
eq((int)$c->query('SELECT quota_used FROM ride_vouchers WHERE id=1')->fetch_assoc()['quota_used'],1,'Legacy quota increments');
$c->begin_transaction();ly_restore_cancelled_voucher($c,1,'TEST-OLD-1');$c->rollback();
eq((int)$c->query('SELECT quota_used FROM ride_vouchers WHERE id=1')->fetch_assoc()['quota_used'],1,'Rollback also rolls back quota refund');
$c->begin_transaction();ly_restore_cancelled_voucher($c,1,'TEST-OLD-1');ly_restore_cancelled_voucher($c,1,'TEST-OLD-1');$c->commit();
eq((int)$c->query('SELECT quota_used FROM ride_vouchers WHERE id=1')->fetch_assoc()['quota_used'],0,'Legacy quota decrements exactly once');
eq(rv_apply($c,1,'OLD',8000,'TransRide')['discount'],2000,'Legacy voucher reusable after cancel');
$c->begin_transaction();$v=ly_apply_voucher($c,1,'TRV-OWN',8000,8000,true);ly_mark_used($c,$v,'TEST-SCHEDULED');$c->commit();
eq(ly_apply_voucher($c,1,'TRV-OWN',8000,8000),null,'Voucher reserved for scheduled order cannot be reused');
$c->begin_transaction();ly_restore_cancelled_voucher($c,1,'TEST-SCHEDULED');$c->commit();
eq(ly_apply_voucher($c,1,'TRV-OWN',8000,8000)['discount'],8000,'Scheduled cancellation restores owned voucher');
$c->begin_transaction();$v=ly_apply_voucher($c,1,'TRV-OWN',8000,8000,true);ly_mark_used($c,$v,'TEST-ROLLBACK');$c->rollback();
eq(ly_apply_voucher($c,1,'TRV-OWN',8000,8000)['discount'],8000,'Failed order transaction does not consume voucher');
$split=vd_order_breakdown(['note'=>json_encode(['discount_breakdown_version'=>2,'voucher_discount'=>2000,'point_discount'=>1000,'total_discount'=>3000])]);
eq($split['voucher_discount'],2000,'New data keeps voucher separate');eq($split['point_discount'],1000,'New data keeps points separate');eq($split['total_discount'],3000,'Driver subsidy includes both components');
$old=vd_order_breakdown(['note'=>json_encode(['voucher_discount'=>3000,'hemat_discount'=>1000])]);
eq($old['voucher_discount'],2000,'Legacy food aggregate decoded without double counting');eq($old['total_discount'],3000,'Legacy driver subsidy preserved');
$ride=vd_order_breakdown(['voucher_discount'=>3000,'note'=>json_encode(['voucher_discount'=>3000])]);eq($ride['total_discount'],3000,'Legacy ride aggregate preserved when split unknown');
// A snapshot made before another connection commits must not bypass the quota.
$b=db();$b->begin_transaction();$b->query('SELECT COUNT(*) FROM customer_promo_usages');
$c->begin_transaction();$v=rv_apply($c,1,'RACE',58000,'TransFood',true,8000);rv_record_usage($c,$v,1,'TEST-RACE');$c->commit();
rejected(fn()=>rv_apply($b,2,'RACE',58000,'TransFood',true,8000),'Current locking read rejects stale-snapshot quota overspend');$b->rollback();
// Zero held amount should finalize status without any synthetic debit/release/refund.
$c->query("INSERT INTO orders(id,user_id,order_id,order_type,price,note,status,driver,payment_method,payment_status,escrow_amount) VALUES(1,1,'TEST-ZERO','Transbike',0,'{}','arrived_delivery','test_driver','balance','paid_held',0),(2,1,'TEST-ZERO-CANCEL','Transbike',0,'{}','scheduled','','balance','paid_held',0)");
$o=$c->query('SELECT * FROM orders WHERE id=1')->fetch_assoc();$before=(int)$c->query('SELECT balance FROM users WHERE id=4')->fetch_assoc()['balance'];
$c->begin_transaction();$release=tp_release_held_order_to_driver($c,$o,0);$subsidy=tp_adjust_driver_wallet($c,'test_driver',8000,'promo_subsidy','PROMO-TEST-ZERO','Test subsidy','in');$c->commit();
eq($release['amount'],0,'Zero customer hold can be released without error');
eq($c->query('SELECT payment_status FROM orders WHERE id=1')->fetch_assoc()['payment_status'],'released','Zero release persists terminal payment status');
eq((int)$c->query('SELECT balance FROM users WHERE id=4')->fetch_assoc()['balance'],$before+8000,'Driver still receives full subsidy on zero-pay ride');
$o=$c->query('SELECT * FROM orders WHERE id=2')->fetch_assoc();$before=(int)$c->query('SELECT balance FROM users WHERE id=1')->fetch_assoc()['balance'];
$c->begin_transaction();$refund=tp_refund_held_order($c,$o);$c->commit();eq($refund['amount'],0,'Zero paid order can be cancelled without refund error');
eq((int)$c->query('SELECT balance FROM users WHERE id=1')->fetch_assoc()['balance'],$before,'Zero refund does not create customer money');
eq($c->query('SELECT payment_status FROM orders WHERE id=2')->fetch_assoc()['payment_status'],'refunded','Zero refund persists terminal status');
$c->begin_transaction();tp_refund_held_order($c,$c->query('SELECT * FROM orders WHERE id=2')->fetch_assoc());$c->commit();
eq((int)$c->query('SELECT balance FROM users WHERE id=1')->fetch_assoc()['balance'],$before,'Repeated zero refund does not credit customer');
$c->query("INSERT INTO orders(id,user_id,order_id,order_type,price,note,status,driver,payment_method,payment_status,escrow_amount) VALUES(3,1,'TEST-ZERO-SPLIT','Transbike',0,'{}','pending','','balance','paid_held',0)");
$c->query("INSERT INTO split_bill_payments(session_key,order_id,user_id,percentage,amount,status) VALUES('fixture','TEST-ZERO-SPLIT',1,100,0,'held')");
$c->begin_transaction();$o=$c->query('SELECT * FROM orders WHERE id=3')->fetch_assoc();tp_refund_held_order($c,$o);$c->commit();
eq($c->query("SELECT status FROM split_bill_payments WHERE order_id='TEST-ZERO-SPLIT'")->fetch_assoc()['status'],'refunded','Zero-valued Split Pay share is closed on cancellation');
// Missing usage tables must reject rather than grant untracked discounts.
$c->query('RENAME TABLE ride_voucher_usages TO missing_usage_backup');
rejected(fn()=>rv_apply($c,1,'OLD',8000,'TransRide'),'Missing usage table fails closed');
$c->query('RENAME TABLE missing_usage_backup TO ride_voucher_usages');
echo "RESULT: $tests checks passed on MariaDB / PHP ".PHP_VERSION."\n";
