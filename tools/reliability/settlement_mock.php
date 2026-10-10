<?php
// Executes the production payment helper with a controlled mysqli collaborator. No native extension/DB.
class FakeRows{public $num_rows;function __construct(public $rows){$this->num_rows=count($rows);}function fetch_assoc(){return array_shift($this->rows);}}
class mysqli{
 public $orders=[],$users=[1=>75000,2=>1000],$ledger=[],$failLedger=false,$snapshot;
 function begin_transaction(){$this->snapshot=[$this->orders,$this->users,$this->ledger];}
 function rollback(){[$this->orders,$this->users,$this->ledger]=$this->snapshot;}
 function commit(){}
 function prepare($sql){return new FakeStatement($this,$sql);}
 function query($sql){
  if(preg_match('/SELECT \* FROM pickup_orders WHERE id=(\d+)/',$sql,$m))return new FakeRows(isset($this->orders[(int)$m[1]])?[$this->orders[(int)$m[1]]]:[]);
  if(str_starts_with($sql,'SHOW TABLES')||str_starts_with($sql,'SHOW COLUMNS'))return new FakeRows([['exists'=>1]]);
  if(str_contains($sql,'INSERT INTO wallet_transactions')){if($this->failLedger)return false;preg_match('/VALUES\s*\((.*?)\)/s',$sql,$m);preg_match_all("/'([^']*)'/",$m[1],$v);$this->ledger[]=['id'=>count($this->ledger)+1,'user_id'=>(int)$v[1][0],'type'=>$v[1][1],'amount'=>(int)$v[1][3],'reference'=>$v[1][7]];return true;}
  throw new Exception('Unhandled fixture query: '.$sql);
 }
}
class FakeStatement{
 public $v=[],$rows=[],$affected=0;
 function __construct(public $c,public $sql){}
 function bind_param($type,&...$v){$this->v=$v;}
 function execute(){
  $v=$this->v;$c=$this->c;$this->affected=0;
  if(str_contains($this->sql,'reference IN')){$this->rows=array_values(array_filter($c->ledger,fn($r)=>in_array($r['reference'],$v,true)));}
  elseif(str_contains($this->sql,'SELECT balance'))$this->rows=isset($c->users[$v[0]])?[['balance'=>$c->users[$v[0]]]]:[];
  elseif(str_contains($this->sql,'UPDATE users')){$c->users[$v[1]]+=$v[0];$this->affected=1;}
  elseif(str_contains($this->sql,'UPDATE pickup_orders')){if($c->orders[$v[2]]['driver_settlement_status']==='held'){$c->orders[$v[2]]['driver_settlement_status']=$v[0];$c->orders[$v[2]]['payment_status']=$v[1];$c->orders[$v[2]]['escrow_amount']=0;$this->affected=1;}}
  else throw new Exception('Unhandled fixture statement');
  return true;
 }
 function get_result(){return new FakeRows($this->rows);}function close(){}
}
function mysqli_prepare($c,$s){return $c->prepare($s);}function mysqli_stmt_bind_param($s,$t,&...$v){$s->bind_param($t,...$v);}function mysqli_stmt_execute($s){return $s->execute();}function mysqli_stmt_get_result($s){return $s->get_result();}function mysqli_stmt_close($s){$s->close();}function mysqli_stmt_affected_rows($s){return $s->affected;}function mysqli_fetch_assoc($r){return $r->fetch_assoc();}function mysqli_num_rows($r){return $r->num_rows;}function mysqli_real_escape_string($c,$v){return addslashes($v);}function mysqli_query($c,$s){return $c->query($s);}
require __DIR__.'/server/pickup_payment_helper.php';
$n=0;function check($v,$m){global $n;if(!$v)throw new Exception($m);$n++;echo "PASS $m\n";}
$c=new mysqli();$order=['id'=>1,'order_id'=>'ONE','escrow_amount'=>25000,'driver_settlement_status'=>'held','payment_status'=>'paid_held','payment_method'=>'balance','status'=>'arrived_delivery'];$c->orders[1]=$order;
$c->begin_transaction();$r=pickup_transfer_held($c,$order,2,false);$c->commit();check($r['amount']===25000&&$c->users[2]===26000,'production helper releases full escrow');check($c->orders[1]['escrow_amount']===0&&count($c->ledger)===1,'production helper closes escrow and records ledger');
$c->begin_transaction();$r=pickup_transfer_held($c,$order,2,false);$c->commit();check($r['amount']===0&&$c->users[2]===26000,'stale finish snapshot cannot pay twice');
$order['id']=2;$order['order_id']='TWO';$c->orders[2]=$order;$c->begin_transaction();pickup_transfer_held($c,$order,1,true);$c->commit();check($c->users[1]===100000&&$c->orders[2]['driver_settlement_status']==='refunded','production helper refunds held customer funds');$c->begin_transaction();$r=pickup_transfer_held($c,$order,1,true);$c->commit();check($r['amount']===0&&$c->users[1]===100000,'stale refund snapshot cannot pay twice');
$order['id']=3;$order['order_id']='THREE';$c->orders[3]=$order;$c->failLedger=true;$c->begin_transaction();$failed=false;try{pickup_transfer_held($c,$order,2,false);}catch(RuntimeException $e){$failed=true;$c->rollback();}check($failed&&$c->users[2]===26000&&$c->orders[3]['escrow_amount']===25000,'ledger failure + caller rollback restores funds');$c->failLedger=false;
$c->ledger[]=['id'=>99,'reference'=>'RELEASE-SEND-THREE'];$c->begin_transaction();$failed=false;try{pickup_transfer_held($c,$order,2,false);}catch(DomainException $e){$failed=true;$c->rollback();}check($failed&&$c->users[2]===26000,'existing ledger blocks inconsistent held payout');
echo "PASS $n production helper checks with controlled SQL collaborator; real SQL integration remains separate\n";
