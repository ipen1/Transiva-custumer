<?php
/** Disposable MySQL integration test; no hosting config is loaded. */
if(PHP_SAPI!=='cli')exit(1);
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$server=$argv[1]??'';
if(!is_file($server.'/customer_wallet_operation_helper.php'))throw new RuntimeException('Supply server directory');
$db=getenv('TEST_DB_NAME')?:'transiva_wallet_operation_test';
if($db!=='transiva_wallet_operation_test')throw new RuntimeException('Disposable DB name required');
$conn=new mysqli(getenv('TEST_DB_HOST')?:'127.0.0.1',getenv('TEST_DB_USER')?:'root',getenv('TEST_DB_PASSWORD')?:'',null,(int)(getenv('TEST_DB_PORT')?:3306),getenv('TEST_DB_SOCKET')?:null);
$conn->query("CREATE DATABASE IF NOT EXISTS `$db`");$conn->select_db($db);
require $server.'/wallet_helper.php';require $server.'/customer_wallet_operation_helper.php';
function operation($conn,$key,$amount=10000,$owner=1,$failBusiness=false){
 $hash=customer_wallet_hash('wallet_withdraw',['amount'=>$amount]);$new=false;$ready=false;
 $conn->begin_transaction();
 try {
  $r=customer_wallet_reserve($conn,$owner,'wallet_withdraw',$key,$hash,$new);
  if($r!==null){$conn->rollback();return $r;}
  if(!$conn->query('SAVEPOINT customer_wallet_business'))throw new RuntimeException('Savepoint failed');$ready=true;
  $s=$conn->prepare('SELECT balance FROM users WHERE id=? FOR UPDATE');$s->bind_param('i',$owner);customer_wallet_execute($s);$s->bind_result($balance);if(!$s->fetch())throw new RuntimeException('Owner missing');$s->close();
  if($balance<$amount)throw new RuntimeException('Insufficient');
  $s=$conn->prepare('UPDATE users SET balance=balance-? WHERE id=?');$s->bind_param('ii',$amount,$owner);customer_wallet_execute($s);$s->close();
  $s=$conn->prepare('INSERT INTO wallet_test_holds(owner_id,operation_id,amount) VALUES(?,?,?)');$s->bind_param('isi',$owner,$key,$amount);customer_wallet_execute($s);$s->close();
  if($failBusiness)throw new RuntimeException('Injected business/ledger failure');
  customer_wallet_queue_intent($conn,$owner.':'.$key.':in',2,'Test','Test',['reference'=>$owner.':'.$key]);
  customer_wallet_queue_intent($conn,$owner.':'.$key.':out',$owner,'Test','Test',['reference'=>$owner.':'.$key]);
  $r=['success'=>true,'committed'=>true,'terminal'=>true,'operation_state'=>'succeeded','operation_id'=>$key,'reference'=>$key,'message'=>'Saved','balance'=>$balance-$amount];
  customer_wallet_store($conn,$owner,'wallet_withdraw',$key,$hash,$r);
  if(!$conn->commit()){$new=false;throw new RuntimeException('Commit unknown');}return $r;
 }catch(Throwable $e){
  if($new&&$ready){$r=customer_wallet_finalize_rejection($conn,$owner,'wallet_withdraw',$key,$hash,$e->getMessage());if($r!==null)return $r;}
  $conn->rollback();throw $e;
 }
}
if(($argv[2]??'')==='--child'){echo json_encode(operation($conn,$argv[3]));exit;}
foreach(['order_notification_outbox','wallet_test_holds','api_idempotency','users'] as $t)$conn->query("DROP TABLE IF EXISTS `$t`");
$conn->query('CREATE TABLE users(id INT PRIMARY KEY,balance BIGINT NOT NULL) ENGINE=InnoDB');
$conn->query('CREATE TABLE api_idempotency(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id INT NOT NULL,action_name VARCHAR(64) NOT NULL,idem_key VARCHAR(120) NOT NULL,response_json LONGTEXT NOT NULL,UNIQUE KEY operation_slot(user_id,action_name,idem_key)) ENGINE=InnoDB');
$conn->query('CREATE TABLE wallet_test_holds(id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id INT,operation_id VARCHAR(120),amount BIGINT) ENGINE=InnoDB');
$conn->query("CREATE TABLE order_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(190) NOT NULL UNIQUE,kind VARCHAR(32),payload_json LONGTEXT,status VARCHAR(16) DEFAULT 'pending',available_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
$conn->query('INSERT INTO users VALUES(1,100000),(2,100000)');
customer_wallet_require_transactional($conn,['users','api_idempotency','wallet_test_holds','order_notification_outbox']);
function check_db($ok,$title){if(!$ok)throw new RuntimeException($title);echo 'PASS '.$title.PHP_EOL;}
function balance_db($c,$id=1){return (int)$c->query('SELECT balance FROM users WHERE id='.$id)->fetch_row()[0];}
$children=[];
for($i=0;$i<2;$i++){$p=proc_open([PHP_BINARY,__FILE__,$server,'--child','concurrent'],[0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);fclose($pipes[0]);$children[]=[$p,$pipes];}
foreach($children as [$p,$pipes]){$out=stream_get_contents($pipes[1]);$err=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);if(proc_close($p)!==0||empty(json_decode($out,true)['success']))throw new RuntimeException('Concurrent child failed: '.$err);}
check_db(balance_db($conn)===90000&&(int)$conn->query('SELECT COUNT(*) FROM wallet_test_holds')->fetch_row()[0]===1,'two concurrent same-key requests debit exactly once');
$r=operation($conn,'concurrent');check_db(!empty($r['replayed'])&&balance_db($conn)===90000,'retry receipt keeps balance');
$conflict=false;try{operation($conn,'concurrent',11000);}catch(Throwable $e){$conflict=true;}check_db($conflict&&balance_db($conn)===90000,'payload conflict keeps prior result');
operation($conn,'concurrent',10000,2);check_db(balance_db($conn,2)===90000,'same key separate owner is isolated');
$conn->query("CREATE TRIGGER fail_wallet_receipt BEFORE UPDATE ON api_idempotency FOR EACH ROW BEGIN IF NEW.idem_key='failed-receipt' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture receipt failure'; END IF; END");
$failed=false;try{operation($conn,'failed-receipt');}catch(Throwable $e){$failed=true;}
check_db($failed&&balance_db($conn)===90000&&(int)$conn->query("SELECT COUNT(*) FROM wallet_test_holds WHERE operation_id='failed-receipt'")->fetch_row()[0]===0,'receipt failure rolls back debit and hold');
$conn->query('DROP TRIGGER fail_wallet_receipt');
check_db((int)$conn->query("SELECT COUNT(*) FROM order_notification_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.data.reference'))='1:concurrent'")->fetch_row()[0]===2,'success stores two intents; retry stores no duplicate');
$r=operation($conn,'rejected-final',10000,1,true);
check_db(empty($r['success'])&&$r['operation_state']==='rejected'&&balance_db($conn)===90000,'business rollback commits terminal rejection');
$r=operation($conn,'rejected-final');
check_db(empty($r['success'])&&!empty($r['replayed'])&&balance_db($conn)===90000,'same rejected ID remains rejected after fault recovery');
$status=customer_wallet_lookup_status($conn,1,'wallet_withdraw','rejected-final');
check_db($status['operation_state']==='rejected'&&$status['terminal'],'status reads durable rejection');
$status=customer_wallet_lookup_status($conn,1,'wallet_withdraw','never-sent');
check_db($status['operation_state']==='not_found'&&(int)$conn->query("SELECT COUNT(*) FROM api_idempotency WHERE idem_key='never-sent'")->fetch_row()[0]===0,'not_found status creates no receipt');
$conn->query("CREATE TRIGGER reject_outbox BEFORE INSERT ON order_notification_outbox FOR EACH ROW BEGIN IF JSON_UNQUOTE(JSON_EXTRACT(NEW.payload_json,'$.data.reference'))='1:outbox-failure' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture outbox failure'; END IF; END");
$r=operation($conn,'outbox-failure');
check_db(empty($r['success'])&&$r['operation_state']==='rejected'&&balance_db($conn)===90000&&(int)$conn->query("SELECT COUNT(*) FROM wallet_test_holds WHERE operation_id='outbox-failure'")->fetch_row()[0]===0,'outbox failure rolls back financial intent and stores rejection');
$conn->query('DROP TRIGGER reject_outbox');
echo "PASS disposable database integration; do not run against production\n";
