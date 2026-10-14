<?php
/** Controlled SQL failures for production auth helper/endpoints; not a replacement for MariaDB tests. */
class AuthRows extends mysqli_result {
    private array $rows;
    function __construct(array $rows){$this->rows=$rows;}
    function fetch_assoc():array|false|null{return array_shift($this->rows);}
}
class AuthStmt extends mysqli_stmt {
    private AuthFixture $db;private string $sql;private array $values=[];private array $rows=[];
    function __construct(AuthFixture $db,string $sql){$this->db=$db;$this->sql=$sql;}
    function bind_param(string $types,mixed &...$vars):bool{$this->values=[];foreach($vars as &$v)$this->values[]=&$v;return true;}
    function execute(?array $params=null):bool{$this->rows=$this->db->run($this->sql,$params??$this->values);return true;}
    function get_result():mysqli_result|false{return new AuthRows($this->rows);}
}
class AuthFixture extends mysqli {
    public array $state; public int $fixture_insert_id=0;private $lock;private array $before;
    function __construct(){ $this->state=json_decode(file_get_contents(getenv('AUTH_TEST_STATE')),true); }
    function prepare(string $q):mysqli_stmt|false{return new AuthStmt($this,$q);}
    function begin_transaction(int $flags=0,?string $name=null):bool{$this->lock=fopen(getenv('AUTH_TEST_STATE').'.lock','c+');flock($this->lock,LOCK_EX);$this->state=json_decode(file_get_contents(getenv('AUTH_TEST_STATE')),true);$this->before=$this->state;return true;}
    function commit(int $flags=0,?string $name=null):bool{file_put_contents(getenv('AUTH_TEST_STATE'),json_encode($this->state));$this->unlock();if(getenv('AUTH_TEST_FAIL')==='commit_ack')throw new RuntimeException('Commit response lost');return true;}
    function rollback(int $flags=0,?string $name=null):bool{if(isset($this->before))$this->state=$this->before;$this->unlock();return true;}
    private function unlock(){if($this->lock){flock($this->lock,LOCK_UN);fclose($this->lock);$this->lock=null;}}
    function query(string $query,int $result_mode=MYSQLI_STORE_RESULT):mysqli_result|bool{$this->run($query,[]);return true;}
    function run(string $q,array $v):array {
        if(str_starts_with($q,'INSERT INTO customer_registration_receipts')){
            if(!isset($this->state['receipts'][$v[0]]))$this->state['receipts'][$v[0]]=['request_hash'=>$v[1],'password_proof'=>$v[2],'response_json'=>null];return [];
        }
        if(str_starts_with($q,'SELECT request_hash,password_proof,response_json FROM customer_registration_receipts'))return [$this->state['receipts'][$v[0]]];
        if(str_starts_with($q,'INSERT INTO users(')){
            foreach($this->state['users'] as $r){if(strcasecmp($v[0],$r['username'])===0 || ($r['role']==='customer'&&(strcasecmp($v[1],$r['email'])===0||$v[2]===$r['phone'])))throw new mysqli_sql_exception('Duplicate',1062);}
            $id=count($this->state['users'])+1;$this->fixture_insert_id=$id;$this->state['users'][$id]=['id'=>$id,'username'=>$v[0],'email'=>$v[1],'phone'=>$v[2],'password'=>$v[3],'role'=>'customer','email_verified'=>$v[4]];return [];
        }
        if(str_starts_with($q,'UPDATE customer_registration_receipts SET response_json')){if(getenv('AUTH_TEST_FAIL')==='receipt')throw new RuntimeException('Receipt unavailable');$this->state['receipts'][$v[1]]['response_json']=$v[0];return [];}
        if(str_starts_with($q,'SELECT id,email,email_verified FROM users'))return isset($this->state['users'][$v[0]])?[$this->state['users'][$v[0]]]:[];
        if(str_starts_with($q,'SELECT * FROM customer_email_challenges'))return isset($this->state['challenges'][$v[0]])?[$this->state['challenges'][$v[0]]]:[];
        if(str_starts_with($q,'UPDATE customer_email_challenges SET otp_hash=NULL,proof_hash=')){$r=&$this->state['challenges'][$v[1]];$r['otp_hash']=null;$r['proof_hash']=$v[0];$r['proof_expires_at']=date('Y-m-d H:i:s',time()+300);$r['consumed_at']=null;return [];}
        if(preg_match('/UPDATE customer_email_challenges SET attempts=attempts\+1 WHERE user_id=(\d+)/',$q,$m)){$this->state['challenges'][$m[1]]['attempts']++;return [];}
        if(preg_match('/UPDATE users SET email_verified=1,email_verified_at=NOW\(\) WHERE id=(\d+)/',$q,$m)){$this->state['users'][$m[1]]['email_verified']=1;return [];}
        if(preg_match('/UPDATE customer_email_challenges SET consumed_at=NOW\(\) WHERE user_id=(\d+)/',$q,$m)){if(getenv('AUTH_TEST_FAIL')==='proof')throw new RuntimeException('Proof receipt unavailable');$this->state['challenges'][$m[1]]['consumed_at']=date('Y-m-d H:i:s');return [];}
        throw new RuntimeException('Unhandled SQL fixture: '.$q);
    }
}
