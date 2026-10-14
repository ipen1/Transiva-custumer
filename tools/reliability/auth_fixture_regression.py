"""Execute production PHP with controlled transaction faults (no real SQL engine)."""
from pathlib import Path
import tempfile,subprocess,os,json,shutil,uuid,time
from concurrent.futures import ThreadPoolExecutor
root=Path(__file__).resolve().parent
php=os.environ.get('PHP_BIN','php')
source=Path(os.environ.get('AUTH_SOURCE',str(root/'auth-server')))
checks=0
with tempfile.TemporaryDirectory(prefix='transiva-auth-test-') as tmp:
 p=Path(tmp);state=p/'state.json'
 for name in ['customer_registration_helper.php','customer_email_helper.php','verifyEmailOtp.php','consumeEmailProof.php']:shutil.copy2(source/name,p/name)
 # Adapt only mysqli's read-only insert_id property to the test adapter.
 helper=p/'customer_registration_helper.php';helper.write_text(helper.read_text().replace('$c->insert_id','$c->fixture_insert_id'))
 shutil.copy2(root/'auth_sql_fixture.php',p/'fixture.php')
 (p/'config.php').write_text("<?php require_once __DIR__.'/fixture.php';$conn=new AuthFixture();")
 (p/'native_auth.php').write_text("""<?php require_once __DIR__.'/config.php';
 function native_require_user($c){$id=(int)(getenv('AUTH_TEST_USER')?:1);return $c->state['users'][$id]??['id'=>$id,'role'=>'customer'];}
 function native_input(){return json_decode(getenv('AUTH_TEST_INPUT'),true);}
 function native_out($success,$message,$extra=[],$status=200,$code=''){echo json_encode(array_merge(['success'=>$success,'message'=>$message,'code'=>$code],$extra));exit;}
 $_SERVER['REQUEST_METHOD']='POST';
 """)
 (p/'signup.php').write_text("""<?php require __DIR__.'/config.php';require __DIR__.'/customer_registration_helper.php';
 try{echo json_encode(cr_register($conn,json_decode(getenv('AUTH_TEST_INPUT'),true),getenv('AUTH_TEST_GOOGLE')==='1'));}
 catch(Throwable $e){echo json_encode(['success'=>false,'message'=>$e->getMessage()]);}
 """)
 def read():return json.loads(state.read_text())
 def seed(data=None):state.write_text(json.dumps(data or {'users':{},'receipts':{},'challenges':{}}))
 def call(endpoint,data,user=1,fault='',google=False):
  env=dict(os.environ,AUTH_TEST_STATE=str(state),AUTH_TEST_USER=str(user),AUTH_TEST_INPUT=json.dumps(data),AUTH_TEST_FAIL=fault,AUTH_TEST_GOOGLE='1' if google else '0')
  r=subprocess.run([php,str(p/endpoint)],env=env,text=True,capture_output=True)
  if r.returncode:raise AssertionError(r.stdout+r.stderr)
  try:return json.loads(r.stdout)
  except Exception:raise AssertionError(r.stdout+r.stderr)
 def check(ok,name):
  global checks
  assert ok,name;checks+=1;print('PASS',name,flush=True)
 signup=dict(username='meipen',email='meipen@example.test',phone='081234567890',password='Strong pass 8',operation_id='signup-'+uuid.uuid4().hex,email_verified=1)
 seed();r=call('signup.php',signup);check(r['success'] and read()['users']['1']['email_verified']==0,'client email_verified cannot grant verification')
 again=call('signup.php',signup);check(again['success'] and again['replayed'] and len(read()['users'])==1,'lost response replay returns same account')
 changed_password=call('signup.php',dict(signup,password='Different pass 8'));check(not changed_password['success'] and len(read()['users'])==1,'same operation cannot change password')
 changed=dict(signup,email='other@example.test');r=call('signup.php',changed);check(not r['success'] and len(read()['users'])==1,'same operation cannot change payload')
 other=dict(signup,operation_id='signup-'+uuid.uuid4().hex);r=call('signup.php',other);check(not r['success'] and len(read()['users'])==1,'competing operation respects unique identity')
 seed();r=call('signup.php',signup,fault='receipt');check(not r['success'] and not read()['users'],'receipt failure rolls back account insert')
 seed();r=call('signup.php',signup,fault='commit_ack');check(not r['success'] and len(read()['users'])==1,'ambiguous commit never creates a second account');r=call('signup.php',signup);check(r['success'] and r['replayed'] and len(read()['users'])==1,'same ID recovers committed signup')
 seed();with_args=[signup]*4
 with ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(lambda d:call('signup.php',d),with_args))
 check(all(r['success'] for r in results) and len(read()['users'])==1,'concurrent receipt requests serialize under fixture lock')
 for password in ['1234567','short','é'*7,'x'*73]:
  seed();r=call('signup.php',dict(signup,password=password));check(not r['success'] and not read()['users'],'reject invalid new password length '+str(len(password)))
 seed();r=call('signup.php',dict(signup,password='é'*8));check(r['success'],'8 unicode characters satisfy minimum')
 seed();r=call('signup.php',signup,google=True);check(r['success'] and read()['users']['1']['email_verified']==1,'trusted Google branch creates verified customer')
 # OTP hashes seeded using PHP password_hash, never plaintext stored in challenge.
 hashed=subprocess.run([php,'-r','echo password_hash("123456",PASSWORD_DEFAULT);'],capture_output=True,text=True,check=True).stdout.strip()
 proof='a'*64
 def otp_seed():
  seed({'users':{'1':dict(id=1,username='meipen',email='meipen@example.test',phone='6281234567890',role='customer',email_verified=0),'2':dict(id=2,username='other',email='other@example.test',role='customer',email_verified=0)},'receipts':{},'challenges':{'1':dict(user_id=1,email='meipen@example.test',otp_hash=hashed,expires_at=time.strftime('%Y-%m-%d %H:%M:%S',time.gmtime(time.time()+300)),attempts=0,proof_hash=None,proof_expires_at=None,consumed_at=None)}})
 otp_seed();r=call('verifyEmailOtp.php',{'otp':'999999'});check(not r['success'] and read()['challenges']['1']['attempts']==1,'wrong OTP attempt persists atomically')
 data=read();data['challenges']['1']['attempts']=5;seed(data);r=call('verifyEmailOtp.php',{'otp':'123456'});check(not r['success'],'correct OTP rejected after attempt ceiling')
 otp_seed();r=call('verifyEmailOtp.php',{'otp':'123456'});check(r['success'] and read()['users']['1']['email_verified']==0,'valid OTP issues proof without trusting client verification')
 proof=r['verification_proof'];r=call('verifyEmailOtp.php',{'otp':'123456'});check(not r['success'],'same OTP cannot issue another proof')
 r=call('consumeEmailProof.php',{'verification_proof':proof},user=2);check(not r['success'] and read()['users']['2']['email_verified']==0,'proof cannot verify a different account')
 r=call('consumeEmailProof.php',{'verification_proof':proof},fault='proof');check(not r['success'] and read()['users']['1']['email_verified']==0,'proof receipt failure rolls back verification')
 r=call('consumeEmailProof.php',{'verification_proof':proof},fault='commit_ack');check(not r['success'] and read()['users']['1']['email_verified']==1,'lost proof commit acknowledgment retains one durable verification')
 r=call('consumeEmailProof.php',{'verification_proof':proof});check(r['success'] and r['replayed'],'proof retry acknowledges consumed receipt without second mutation')
 otp_seed();r=call('verifyEmailOtp.php',{'otp':'123456'});proof=r['verification_proof'];data=read();data['users']['1']['email']='changed@example.test';seed(data);r=call('consumeEmailProof.php',{'verification_proof':proof});check(not r['success'],'changed account email invalidates proof')
 otp_seed();r=call('verifyEmailOtp.php',{'otp':'123456'});proof=r['verification_proof'];data=read();data['challenges']['1']['proof_expires_at']='2020-01-01 00:00:00';seed(data);r=call('consumeEmailProof.php',{'verification_proof':proof});check(not r['success'],'expired proof cannot verify email')
 print('PASS',checks,'auth fault fixture checks; real SQL migration and device tests remain required')
