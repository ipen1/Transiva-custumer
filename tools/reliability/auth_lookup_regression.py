"""Execute production lookup endpoints/helpers with a read-only SQL adapter; no real DB."""
from pathlib import Path
import tempfile,subprocess,os,json,shutil
root=Path(__file__).resolve().parent
source=Path(os.environ.get('AUTH_SOURCE',str(root/'auth-server')))
php=os.environ.get('PHP_BIN','php');checks=0
with tempfile.TemporaryDirectory(prefix='transiva-lookup-') as tmp:
 p=Path(tmp);state=p/'state.json'
 for name in ['customer_registration_helper.php','customer_auth_lookup_helper.php','customer_auth_check.php','customer_login_preview.php']:shutil.copy2(source/name,p/name)
 shutil.copy2(root/'auth_sql_fixture.php',p/'fixture.php')
 # CLI adapter: php://input is HTTP-only; substitute the controlled body, preserving endpoint validation.
 for name in ['customer_auth_check.php','customer_login_preview.php']:
  f=p/name;f.write_text(f.read_text().replace("file_get_contents('php://input',false,null,0,8193)","(string)getenv('AUTH_TEST_INPUT')").replace("file_get_contents('php://input',false,null,0,2049)","(string)getenv('AUTH_TEST_INPUT')"))
 (p/'config.php').write_text(r'''<?php require_once __DIR__.'/fixture.php';
class LookupFixture extends AuthFixture {
 function query(string $q,int $mode=MYSQLI_STORE_RESULT):mysqli_result|bool{
  $schema=$this->state['schema']??[];
  if(str_contains($q,'information_schema.TABLES')){
   $rows=[];foreach(($schema['engines']??['users'=>'InnoDB','customer_registration_receipts'=>'InnoDB','customer_email_challenges'=>'InnoDB']) as $table=>$engine)$rows[]=['TABLE_NAME'=>$table,'ENGINE'=>$engine];return new AuthRows($rows);
  }
  if(preg_match('/SHOW COLUMNS FROM `([^`]+)`/',$q,$m)){
   $all=['users'=>['id','username','email','phone','password','role','profile_photo','email_verified','email_verified_at','auth_username_key','auth_customer_email_key','auth_customer_phone_key'],'customer_registration_receipts'=>['operation_id','request_hash','password_proof','response_json'],'customer_email_challenges'=>['user_id','email','otp_hash','expires_at','resend_after','attempts','proof_hash','proof_expires_at','consumed_at']];
   return new AuthRows(array_map(fn($name)=>['Field'=>$name],$schema['columns'][$m[1]]??$all[$m[1]]));
  }
  if($q==='SHOW INDEX FROM users')return new AuthRows($schema['indexes']??array_map(fn($c)=>['Non_unique'=>0,'Key_name'=>'uq_'.$c,'Column_name'=>$c],['auth_username_key','auth_customer_email_key','auth_customer_phone_key']));
  throw new RuntimeException('Unexpected non-read-only lookup SQL: '.$q);
 }
 function run(string $q,array $v):array{
  if(preg_match('/SELECT id FROM users WHERE `(auth_[a-z_]+)`=\? LIMIT 1/',$q,$m)){
   $field=['auth_username_key'=>'username','auth_customer_email_key'=>'email','auth_customer_phone_key'=>'phone'][$m[1]];
   foreach($this->state['users'] as $r){if($field!=='username'&&strtolower(trim($r['role']))!=='customer')continue;
    $value=$field==='phone'?cr_phone($r[$field]):strtolower(trim($r[$field]));if($value===$v[0])return [['id'=>$r['id']]];
   }return [];
  }
  if(str_starts_with($q,'SELECT username,')){
   foreach($this->state['users'] as $r)if(strcasecmp($r['username'],$v[0])===0&&in_array(strtolower(trim($r['role'])),['','customer','pelanggan','user'],true))return [['username'=>$r['username'],'profile_photo'=>str_contains($q,"'' AS profile_photo")?'':($r['profile_photo']??'')]];
   return [];
  }
  throw new RuntimeException('Unexpected lookup statement: '.$q);
 }
}
$conn=new LookupFixture();$_SERVER['REQUEST_METHOD']=getenv('AUTH_TEST_METHOD')?:'POST';
''')
 def seed(schema=None,photo='uploads/profiles/meipen.jpg'):
  data={'users':{'1':dict(id=1,username='Meipen',role='customer',email='meipen@example.test',phone='6281234567890',profile_photo=photo),'2':dict(id=2,username='Driver',role='driver',email='driver@example.test',phone='6287777777777'),'3':dict(id=3,username='Admin',role='admin',email='admin@example.test',phone='6286666666666')},'receipts':{},'challenges':{}}
  if schema is not None:data['schema']=schema
  state.write_text(json.dumps(data))
 def call(endpoint,body,method='POST'):
  before=state.read_bytes();raw=json.dumps(body) if not isinstance(body,str) else body
  r=subprocess.run([php,str(p/endpoint)],text=True,capture_output=True,env=dict(os.environ,AUTH_TEST_STATE=str(state),AUTH_TEST_INPUT=raw,AUTH_TEST_METHOD=method))
  assert r.returncode==0,r.stdout+r.stderr
  try:result=json.loads(r.stdout)
  except Exception:raise AssertionError(r.stdout+r.stderr)
  assert state.read_bytes()==before,'lookup mutated state'
  return result
 def check(ok,message):
  global checks
  assert ok,message;checks+=1;print('PASS',message,flush=True)
 def lookup(fields):return call('customer_auth_check.php',{'fields':fields})
 seed();r=lookup({'username':'meipen'});check(r['success'] and not r['fields']['username']['available'],'existing customer username detected without case ambiguity')
 r=lookup({'username':'driver'});check(not r['fields']['username']['available'],'driver username remains globally reserved')
 r=lookup({'email':'MEIPEN@EXAMPLE.TEST'});check(r['fields']['email']['message']=='Email sudah digunakan.','specific duplicate email message')
 for phone in ['081234567890','6281234567890','+62 812-3456-7890','006281234567890']:
  r=lookup({'phone':phone});check(not r['fields']['phone']['available'],'same phone detected: '+phone)
 r=lookup({'email':'driver@example.test','phone':'087777777777'});check(all(v['available'] for v in r['fields'].values()),'driver email and phone remain available to customer role')
 r=lookup({'username':'new_user','email':'new@example.test','phone':'081299999999'});check(all(v['available'] for v in r['fields'].values()),'all new fields available in one read-only response')
 for fields in [{'email':'wrong'},{'phone':'123'},{'username':'no spaces'},{'username':'ab'}]:
  r=lookup(fields);check(not next(iter(r['fields'].values()))['valid'],'invalid format never reports available '+str(fields))
 for body in [{'fields':{}},{'fields':{'password':'secret'}},{'fields':{'email':['bad']}},'broken-json',{'fields':{'email':'a'*255}}]:
  r=call('customer_auth_check.php',body);check(not r['success'],'reject malformed or oversized lookup input')
 r=call('customer_auth_check.php',{'fields':{'username':'new_user'}},'GET');check(not r['success'],'lookup rejects GET')
 for schema in [dict(engines={'users':'InnoDB'}),dict(engines={'users':'MyISAM','customer_registration_receipts':'InnoDB','customer_email_challenges':'InnoDB'}),dict(indexes=[]),dict(columns={'customer_registration_receipts':['operation_id','request_hash','response_json']}),dict(columns={'users':['id','username']})]:
  seed(schema);r=lookup({'username':'new_user'});check(not r['success'] and r['code']=='AUTH_SCHEMA_REQUIRED','schema problem is explicit before signup')
 seed();r=call('customer_login_preview.php',{'username':'Meipen'});check(r['success'] and r['found'] and r['profile']['photo']=='uploads/profiles/meipen.jpg','existing username returns its avatar')
 check(set(r['profile'])=={'username','photo'},'preview does not expose phone email ID password or token')
 data=json.loads(state.read_text());data['users']['1']['role']='pelanggan';state.write_text(json.dumps(data));r=call('customer_login_preview.php',{'username':'Meipen'});check(r['found'],'legacy customer role still reaches password stage')
 r=call('customer_login_preview.php',{'username':'missing'});check(r['success'] and not r['found'],'unknown username cannot enter password stage')
 r=call('customer_login_preview.php',{'username':'Admin'});check(not r['found'],'admin account avatar is not returned by customer preview')
 for value in ['',[], 'x'*101]:
  r=call('customer_login_preview.php',{'username':value});check(not r['success'],'invalid preview username rejected')
 seed(photo='server/uploads/chat/hd/private.jpg');r=call('customer_login_preview.php',{'username':'Meipen'});check(r['profile']['photo']=='','chat media path is not exposed as avatar')
 seed(schema={'columns':{'users':['id','username','role']}});r=call('customer_login_preview.php',{'username':'Meipen'});check(r['found'] and not r['profile']['photo'],'legacy profile without photo column gets initials fallback')
 print('PASS',checks,'read-only lookup fixture checks; real SQL remains required')
