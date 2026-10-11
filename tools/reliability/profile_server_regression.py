from pathlib import Path
import subprocess,tempfile,sys
root=Path(__file__).resolve().parent
source=(root/'server/customer_profile_helper.php').read_text()
fixture=r'''<?php
class Rows{function __construct(public $rows){}function fetch(){return array_shift($this->rows);}}
class Fixture{public $columns,$user,$fail=false,$executed=[];function __construct($fields){$this->columns=$fields;$this->user=['id'=>7,'username'=>'meipen','role'=>'customer','email'=>'account@example.test','phone'=>'0800000000','balance'=>1234,'delivery_address'=>'fixture','email_verified'=>1];}}
function mysqli_real_escape_string($c,$v){return $v;}
function mysqli_query($c,$q){if($q!=='SHOW COLUMNS FROM users')throw new Exception('Unexpected query');return new Rows(array_map(fn($f)=>['Field'=>$f],$c->columns));}
function mysqli_fetch_assoc($r){return $r->fetch();}
function mysqli_prepare($c,$q){$c->executed[]=$q;if($c->fail)return false;return (object)['c'=>$c,'q'=>$q,'results'=>[]];}
function mysqli_stmt_bind_param($s,$t,&...$v){$s->id=$v[0];}
function mysqli_stmt_execute($s){return true;}
function mysqli_stmt_bind_result($s,&...$v){$s->results=&$v;}
function mysqli_stmt_fetch($s){if($s->id!==7)return false;$select=substr($s->q,7,strpos($s->q,' FROM ')-7);$parts=explode(',',$select);foreach($parts as $i=>$p){if(preg_match('/^`([a-z_]+)`$/',$p,$m))$value=$s->c->user[$m[1]]??null;elseif(str_starts_with($p,'NULL '))$value=null;elseif(str_starts_with($p,'0 '))$value=0;elseif(str_starts_with($p,"'' "))$value='';else throw new Exception('Unexpected select expression');$s->results[$i]=$value;}return true;}
function mysqli_stmt_close($s){}
'''
checks=r'''
$n=0;function check($v,$m){global $n;if(!$v)throw new Exception($m);$n++;echo "PASS $m\n";}
$c=new Fixture(['id','username','role','email','phone','balance','delivery_address','email_verified']);$u=profile_user($c,7);check($u['email']==='account@example.test'&&$u['phone']==='0800000000','existing email phone preserved');check($u['id']===7&&$u['delivery_address']==='fixture','profile owner and address preserved');check($u['email_verified_at']===null&&$u['created_at']===null,'missing optional timestamps do not fail read');check($u['verified_by_admin']===0,'missing approval column never grants verification');check(profile_user($c,8)===null,'unknown owner not replaced by another user');check(count(array_filter($c->executed,fn($q)=>str_contains($q,' WHERE id=? LIMIT 1')))===2,'prepared owner query retained');
$minimal=new Fixture(['id','username','role']);$u=profile_user($minimal,7);check($u['username']==='meipen'&&$u['email']===''&&$u['email_verified']===0,'minimal legacy schema remains readable');
$broken=new Fixture(['id','username']);$failed=false;try{profile_user($broken,7);}catch(RuntimeException $e){$failed=true;}check($failed,'missing required role fails safely');
$failedDb=new Fixture(['id','username','role']);$failedDb->fail=true;$failed=false;try{profile_user($failedDb,7);}catch(RuntimeException $e){$failed=true;}check($failed,'failed prepare never binds false');
echo "PASS $n profile SQL compatibility checks with controlled collaborator\n";
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'fixture.php';p.write_text(fixture+source.removeprefix('<?php')+checks)
 subprocess.run([sys.argv[1] if len(sys.argv)>1 else 'php','-n',str(p)],check=True)
