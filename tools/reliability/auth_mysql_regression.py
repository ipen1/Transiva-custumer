"""Real MariaDB integration. Only creates/drops an isolated transiva_auth_test_* database.
Requires native PHP + mysqli + mbstring and TEST_DB_* credentials. Never targets production.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import os,sys,json,subprocess,tempfile,shutil,uuid,socket,time,urllib.request,urllib.error
root=Path(__file__).resolve().parent
source=Path(os.environ.get('AUTH_SOURCE',str(root/'auth-server')))
php=os.environ.get('PHP_BIN','php');checks=0
with tempfile.TemporaryDirectory(prefix='transiva-auth-mysql-') as tmp:
 p=Path(tmp);env=dict(os.environ,AUTH_TEST_DB='transiva_auth_test_'+uuid.uuid4().hex)
 for name in ['register.php','customer_registration_helper.php','customer_auth_lookup_helper.php','customer_auth_check.php','customer_login_preview.php','customer_email_helper.php','verifyEmailOtp.php','consumeEmailProof.php','maintenance/migrate_customer_auth.php','maintenance/install_auth_2_6_1.sql']:
  dest=p/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source/name,dest)
 (p/'config.php').write_text(r'''<?php mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$db=getenv('AUTH_TEST_DB');if(!preg_match('/^transiva_auth_test_[a-f0-9]{32}$/D',$db))throw new RuntimeException('Disposable DB required');
$conn=new mysqli(getenv('TEST_DB_HOST')?:'127.0.0.1',getenv('TEST_DB_USER')?:'root',getenv('TEST_DB_PASSWORD')?:'',null,(int)(getenv('TEST_DB_PORT')?:3306));
$conn->query("CREATE DATABASE IF NOT EXISTS `$db`");$conn->select_db($db);$conn->set_charset('utf8mb4');
''')
 (p/'sql.php').write_text("<?php require __DIR__.'/config.php';$r=$conn->query(getenv('AUTH_TEST_SQL'));if($r instanceof mysqli_result)echo json_encode($r->fetch_all(MYSQLI_ASSOC));")
 (p/'native_auth.php').write_text(r'''<?php require_once __DIR__.'/config.php';
function native_require_user($c){$id=(int)($_SERVER['HTTP_X_TEST_USER']??1);$s=$c->prepare('SELECT * FROM users WHERE id=?');$s->bind_param('i',$id);$s->execute();return $s->get_result()->fetch_assoc()?:['id'=>$id,'role'=>'customer'];}
function native_input(){return json_decode(file_get_contents('php://input'),true)?:[];}
function native_out($success,$message,$extra=[],$status=200,$code=''){http_response_code($status);header('Content-Type: application/json');echo json_encode(array_merge(['success'=>$success,'message'=>$message,'code'=>$code],$extra));exit;}
''')
 def sql(query):
  r=subprocess.run([php,str(p/'sql.php')],env=dict(env,AUTH_TEST_SQL=query),text=True,capture_output=True)
  assert r.returncode==0,r.stdout+r.stderr
  return json.loads(r.stdout or '[]')
 def count(table):return int(sql('SELECT COUNT(*) n FROM '+table)[0]['n'])
 def check(ok,label):
  global checks
  assert ok,label;checks+=1;print('PASS',label,flush=True)
 with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
 log=open(p/'http.log','w');http=None
 try:
  sql("CREATE TABLE users(id INT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(100) NOT NULL,email VARCHAR(254) NOT NULL,phone VARCHAR(32) NOT NULL,password VARCHAR(255) NOT NULL,role VARCHAR(32) NOT NULL,profile_photo VARCHAR(255) NULL,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL) ENGINE=InnoDB")
  sql("INSERT INTO users(username,email,phone,password,role,created_at,updated_at) VALUES('driver','driver@example.test','6281234500000','fixture-only','driver',NOW(),NOW())")
  # Reproduce partial installation from earlier auth versions.
  sql("CREATE TABLE customer_registration_receipts(operation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,request_hash CHAR(64) NOT NULL,response_json MEDIUMTEXT NULL) ENGINE=InnoDB")
  migration=subprocess.run([php,str(p/'maintenance/migrate_customer_auth.php'),'--apply'],env=env,text=True,capture_output=True)
  check(migration.returncode==0,'CLI migration repairs old receipt and verification columns: '+migration.stdout.strip())
  again=subprocess.run([php,str(p/'maintenance/migrate_customer_auth.php'),'--apply'],env=env,text=True,capture_output=True);check(again.returncode==0,'CLI migration can be repeated')
  (p/'import.php').write_text("<?php require __DIR__.'/config.php';$conn->multi_query(file_get_contents(__DIR__.'/maintenance/install_auth_2_6_1.sql'));do{if($r=$conn->store_result())$r->free();}while($conn->more_results()&&$conn->next_result());")
  imported=subprocess.run([php,str(p/'import.php')],env=env,text=True,capture_output=True);check(imported.returncode==0,'phpMyAdmin SQL syntax executes and repeats on MariaDB: '+imported.stderr)
  http=subprocess.Popen([php,'-S',f'127.0.0.1:{port}','-t',str(p)],env=dict(env,PHP_CLI_SERVER_WORKERS='4'),stdout=log,stderr=log)
  for _ in range(100):
   try:
    with socket.create_connection(('127.0.0.1',port),timeout=.1):break
   except OSError:time.sleep(.05)
  def request(endpoint,body,user=1):
   req=urllib.request.Request(f'http://127.0.0.1:{port}/'+endpoint,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Test-User':str(user)})
   try:
    with urllib.request.urlopen(req,timeout=30) as response:return response.status,json.loads(response.read())
   except urllib.error.HTTPError as response:return response.code,json.loads(response.read())
  _,r=request('customer_auth_check.php',{'fields':{'username':'driver','email':'driver@example.test','phone':'081234500000'}})
  check(r['success'] and not r['fields']['username']['available'] and r['fields']['email']['available'] and r['fields']['phone']['available'],'actual indexed checks preserve global username/customer contact scope')
  payload=dict(username='customer',email='driver@example.test',phone='081234500000',password='Strong pass 8',operation_id='signup-'+uuid.uuid4().hex,email_verified=1)
  with ThreadPoolExecutor(max_workers=6) as pool:responses=list(pool.map(lambda _:request('register.php',payload),range(6)))
  check(all(r[1]['success'] for r in responses) and count('users')==2 and count('customer_registration_receipts')==1,'real SQL concurrent same operation inserts exactly one customer')
  uid=int(responses[0][1]['user_id']);check(int(sql(f'SELECT email_verified FROM users WHERE id={uid}')[0]['email_verified'])==0,'client cannot grant verified email')
  _,r=request('register.php',dict(payload,password='Another pass 8'));check(not r['success'] and r['code']=='REGISTRATION_ID_CONFLICT','real receipt binds the original password')
  _,r=request('register.php',dict(payload,operation_id='signup-'+uuid.uuid4().hex));check(not r['success'] and set(r['field_errors'])=={'username','email','phone'},'final unique conflict returns specific fields')
  before=count('users');_,r=request('register.php',dict(payload,username='short_pass',email='short@example.test',phone='081299990001',password='1234567',operation_id='signup-'+uuid.uuid4().hex));check(not r['success'] and count('users')==before,'short password never inserts account')
  _,r=request('customer_auth_check.php',{'fields':{'email':'DRIVER@example.test','phone':'006281234500000'}});check(all(not v['available'] for v in r['fields'].values()),'normalized contact reads find existing customer')
  sql(f"UPDATE users SET profile_photo='server/uploads/profile/fixture.jpg' WHERE id={uid}")
  _,r=request('customer_login_preview.php',{'username':'customer'});check(r['found'] and set(r['profile'])=={'username','photo'} and r['profile']['photo'].endswith('fixture.jpg'),'login preview returns only correct avatar/login name')
  _,r=request('customer_login_preview.php',{'username':'driver'});check(not r['found'],'driver cannot enter customer password stage')
  # Actual proof consumption transaction and replay.
  hashed=subprocess.run([php,'-r','echo password_hash("123456",PASSWORD_DEFAULT);'],text=True,capture_output=True,check=True).stdout.strip()
  sql(f"INSERT INTO customer_email_challenges(user_id,email,otp_hash,expires_at,resend_after) VALUES({uid},'driver@example.test','{hashed}',DATE_ADD(NOW(),INTERVAL 5 MINUTE),NOW())")
  _,r=request('verifyEmailOtp.php',{'otp':'123456'},uid);check(r['success'] and bool(r.get('verification_proof')),'real OTP transaction produces one-time proof');proof=r['verification_proof']
  _,r=request('verifyEmailOtp.php',{'otp':'123456'},uid);check(not r['success'],'consumed OTP cannot issue another proof')
  with ThreadPoolExecutor(max_workers=5) as pool:proof_results=list(pool.map(lambda _:request('consumeEmailProof.php',{'verification_proof':proof},uid),range(5)))
  check(all(r[1]['success'] for r in proof_results) and sum(not r[1].get('replayed',False) for r in proof_results)==1,'real row locks permit one proof mutation and read-only replays')
  check(int(sql(f'SELECT email_verified FROM users WHERE id={uid}')[0]['email_verified'])==1,'proof consumption verifies actual account')
  sql('DROP TABLE customer_registration_receipts')
  status,r=request('register.php',dict(payload,username='schema_test'));check(status==503 and r['code']=='AUTH_SCHEMA_REQUIRED' and count('users')==before,'missing receipt schema reports setup error without account mutation')
  _,r=request('customer_auth_check.php',{'fields':{'username':'schema_test'}});check(r['code']=='AUTH_SCHEMA_REQUIRED','missing schema is reported before next form stage')
  print('PASS',checks,'real MariaDB auth integration checks')
 finally:
  if http:http.terminate();http.wait(timeout=10)
  log.close()
  try:sql('DROP DATABASE `'+env['AUTH_TEST_DB']+'`')
  except Exception:pass
