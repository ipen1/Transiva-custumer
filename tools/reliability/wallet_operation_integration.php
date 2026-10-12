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
function operation($conn,$key,$amount=10000,$owner=1){
 $hash=customer_wallet_hash('wallet_withdraw',['amount'=>$amount]);$new=false;
 $conn->begin_transaction();
 try {
  $r=customer_wallet_reserve($conn,$owner,'wallet_withdraw',$key,$hash,$new);
  if($r!==null){$conn->rollback();return $r;}
  $s=$conn->prepare('SELECT balance FROM users WHERE id=? FOR UPDATE');$s->bind_param('i',$owner);$s->execute();$s->bind_result($balance);if(!$s->fetch())throw new RuntimeException('Owner missing');$s->close();
  if($balance<$amount)throw new RuntimeException('Insufficient');
  $s=$conn->prepare('UPDATE users SET balance=balance-? WHERE id=?');$s->bind_param('ii',$amount,$owner);$s->execute();$s->close();
  $s=$conn->prepare('INSERT INTO wallet_test_holds(owner_id,operation_id,amount) VALUES(?,?,?)');$s->bind_param('isi',$owner,$key,$amount);$s->execute();$s->close();
  $r=['success'=>true,'committed'=>true,'reference'=>$key,'message'=>'Saved','balance'=>$balance-$amount];
  customer_wallet_store($conn,$owner,'wallet_withdraw',$key,$hash,$r);$conn->commit();return $r;
 }catch(Throwable $e){$conn->rollback();throw $e;}
}
if(($argv[2]??'')==='--child'){echo json_encode(operation($conn,$argv[3]));exit;}
foreach(['wallet_test_holds','api_idempotency','users'] as $t)$conn->query("DROP TABLE IF EXISTS `$t`");
$conn->query('CREATE TABLE users(id INT PRIMARY KEY,balance BIGINT NOT NULL) ENGINE=InnoDB');
$conn->query('CREATE TABLE api_idempotency(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id INT NOT NULL,action_name VARCHAR(64) NOT NULL,idem_key VARCHAR(120) NOT NULL,response_json LONGTEXT NOT NULL,UNIQUE KEY operation_slot(user_id,action_name,idem_key)) ENGINE=InnoDB');
$conn->query('CREATE TABLE wallet_test_holds(id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id INT,operation_id VARCHAR(120),amount BIGINT) ENGINE=InnoDB');
$conn->query('INSERT INTO users VALUES(1,100000),(2,100000)');
customer_wallet_require_transactional($conn,['users','api_idempotency','wallet_test_holds']);
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
echo "PASS disposable database integration; do not run against production\n";
