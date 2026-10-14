#!/usr/bin/env python3
"""Real MariaDB regression of the copied production endpoints, in an isolated disposable DB."""
from pathlib import Path
import os,sys,json,subprocess,tempfile,shutil,uuid
root=Path(__file__).resolve().parent;source=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'chat-server'
php=os.getenv('PHP_BIN','php');php_args=json.loads(os.getenv('TEST_PHP_ARGS','[]'));checks=0
with tempfile.TemporaryDirectory(prefix='transiva-chat-sql-') as directory:
 p=Path(directory);env=dict(os.environ,CHAT_TEST_DB='transiva_chat_test_'+uuid.uuid4().hex)
 def put(name,content): (p/name).write_text(content)
 for name in ['getChat.php','sendChat.php','chat_security_helper.php','chat_page_helper.php','chat_delivery_helper.php','get_customer_conversations.php','chat_notification_helper.php']:
  shutil.copy2(source/name,p/name)
 put('config.php',r'''<?php mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$db=getenv('CHAT_TEST_DB');if(!preg_match('/^transiva_chat_test_[a-f0-9]{32}$/D',$db))throw new RuntimeException('Disposable DB required');
$conn=new mysqli(getenv('TEST_DB_HOST')?:'127.0.0.1',getenv('TEST_DB_USER')?:'root',getenv('TEST_DB_PASSWORD')?:'',null,(int)(getenv('TEST_DB_PORT')?:3306),getenv('TEST_DB_SOCKET')?:null);
$conn->query("CREATE DATABASE IF NOT EXISTS `$db`");$conn->select_db($db);$conn->set_charset('utf8mb4');
''')
 put('native_auth.php',r'''<?php
function native_require_user($c){return ['id'=>(int)(getenv('CHAT_TEST_USER')?:1),'role'=>'customer','username'=>'customer'];}
function native_table_exists($c,$t){$t=$c->real_escape_string($t);return $c->query("SHOW TABLES LIKE '$t'")->num_rows>0;}
function native_column_exists($c,$t,$n){$t=$c->real_escape_string($t);$n=$c->real_escape_string($n);return $c->query("SHOW COLUMNS FROM `$t` LIKE '$n'")->num_rows>0;}
''')
 put('p0_security_helper.php',"<?php require_once __DIR__.'/native_auth.php';function p0_require_customer($c){return native_require_user($c);}function p0_rate_limit(...$a){}function p0_client_ip(){return 'fixture';}")
 put('send_fcm.php',"<?php function send_fcm_notification(...$a){if(getenv('CHAT_TEST_FCM_FAIL'))throw new RuntimeException('injected FCM error');return ['success'=>true];}")
 put('request.php',r'''<?php
register_shutdown_function(function(){if($e=error_get_last())fwrite(STDERR,json_encode($e));});
$_GET=json_decode(getenv('CHAT_TEST_GET')?:'{}',true);$_SERVER['REQUEST_METHOD']=getenv('CHAT_TEST_METHOD')?:'GET';
class ChatInputStream{public $context;private $offset=0;function stream_open($p,$m,$o,&$opened){return true;}function stream_read($n){$s=substr(getenv('CHAT_TEST_PAYLOAD')?:'{}',$this->offset,$n);$this->offset+=strlen($s);return $s;}function stream_eof(){return $this->offset>=strlen(getenv('CHAT_TEST_PAYLOAD')?:'{}');}function stream_stat(){return [];}function url_stat($p,$f){return [];} }
if($_SERVER['REQUEST_METHOD']==='POST'){stream_wrapper_unregister('php');stream_wrapper_register('php',ChatInputStream::class);}
require __DIR__.'/'.getenv('CHAT_TEST_ENDPOINT');
''')
 def run_script(content):
  put('sql.php',"<?php require __DIR__.'/config.php';\n"+content)
  r=subprocess.run([php,*php_args,str(p/'sql.php')],env=env,text=True,capture_output=True)
  if r.returncode:raise AssertionError(r.stdout+r.stderr)
  return r.stdout
 def sql(query):return run_script('$conn->query('+json.dumps(query)+');')
 def scalar(query):return int(run_script('$r=$conn->query('+json.dumps(query)+');echo $r->fetch_row()[0];'))
 def request(endpoint,payload=None,query=None,**extra):
  request_env=dict(env,CHAT_TEST_ENDPOINT=endpoint,CHAT_TEST_GET=json.dumps(query or {}),CHAT_TEST_METHOD='POST' if payload is not None else 'GET',CHAT_TEST_PAYLOAD=json.dumps(payload or {}),**extra)
  return subprocess.Popen([php,*php_args,str(p/'request.php')],env=request_env,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 def result(proc):
  out,err=proc.communicate(timeout=15)
  if proc.returncode:raise AssertionError(out+err)
  try:return json.loads(out)
  except Exception:raise AssertionError(out+err)
 def call(*a,**kw):return result(request(*a,**kw))
 def check(ok,name):
  global checks
  if not ok:raise AssertionError(name)
  checks+=1;print('PASS',name)
 try:
  sql("CREATE TABLE users(id INT PRIMARY KEY,username VARCHAR(60),role VARCHAR(20),fcm_token VARCHAR(50)) ENGINE=InnoDB")
  sql("CREATE TABLE order_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(190) UNIQUE,kind VARCHAR(32),payload_json LONGTEXT,status VARCHAR(16) DEFAULT 'pending',attempts INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
  sql("INSERT INTO users VALUES(1,'customer','customer','c'),(2,'driver','driver','d')")
  sql("CREATE TABLE orders(id INT PRIMARY KEY,user_id INT,order_id VARCHAR(60),room_id VARCHAR(100),status VARCHAR(40),driver VARCHAR(60),created_at DATETIME,updated_at DATETIME,order_type VARCHAR(20)) ENGINE=InnoDB")
  sql("INSERT INTO orders VALUES(1,1,'A','ROOM-A','driver_accepted','driver',NOW(),NOW(),'ride'),(2,2,'B','ROOM-B','driver_accepted','driver',NOW(),NOW(),'ride')")
  sql("CREATE TABLE chat_messages(id INT AUTO_INCREMENT PRIMARY KEY,room_id VARCHAR(100),sender_type VARCHAR(20),message TEXT,created_at DATETIME,is_read INT DEFAULT 0,read_at DATETIME,room_key VARCHAR(191) GENERATED ALWAYS AS (UPPER(REPLACE(room_id,'_','-'))) STORED,INDEX idx_chat_room_key_id(room_key,id)) ENGINE=InnoDB")
  sql((source/'maintenance/chat_reliability_schema.sql').read_text())
  sql("INSERT INTO chat_messages(room_id,sender_type,message,created_at) VALUES"+','.join("('ROOM-A','driver','message-%d',NOW())"%i for i in range(1,426)))
  query=dict(room_id='ROOM-A',order_id='A',source='orders',page_mode='latest',limit=60)
  latest=call('getChat.php',query=query);ids=[x['id'] for x in latest['messages']]
  check(latest['success'] and ids==list(range(366,426)) and latest['has_more_older'],'initial page shows latest 60 messages after 200')
  seen=set(ids);cursor=min(ids);more=True
  while more:
   older=call('getChat.php',query=dict(query,page_mode='older',before_id=cursor));page=[x['id'] for x in older['messages']]
   check(page==sorted(page) and not seen.intersection(page),'history cursor has no overlap and stays chronological')
   seen.update(page);cursor=min(page);more=older['has_more_older']
  check(seen==set(range(1,426)),'all 425 history messages reachable')
  legacy=call('getChat.php',query=dict(room_id='ROOM-A',last_id=200))
  check([x['id'] for x in legacy['messages']]==list(range(201,401)),'legacy driver last_id contract preserved')
  sql("INSERT INTO chat_messages(room_id,sender_type,message,created_at) VALUES"+','.join("('ROOM-A','driver','new-%d',NOW())"%i for i in range(1,132)))
  newer=call('getChat.php',query=dict(query,page_mode='newer',last_id=425));check(newer['has_more'] and newer['last_id']==485,'delta pagination catches backlog without rereading history')
  sql('UPDATE chat_messages SET is_read=1,read_at=NOW() WHERE id=10')
  receipt=call('getChat.php',query=dict(query,page_mode='newer',last_id=556,receipt_ids='10,999999'))
  check(not receipt['messages'] and receipt['receipt_updates'][0]['is_read']==1,'read receipts update with no new messages')
  forbidden=call('getChat.php',query=dict(query,room_id='ROOM-B',order_id='B',order_db_id='2'))
  check(not forbidden['success'],'another customer order remains forbidden')
  payload=dict(room_id='ROOM-A',order_id='A',source='orders',message='stable-send',client_message_id='ccm-test-concurrent-01')
  responses=[result(proc) for proc in [request('sendChat.php',payload) for _ in range(6)]]
  check(all(r['success'] for r in responses) and len({r['chat']['id'] for r in responses})==1 and scalar("SELECT COUNT(*) FROM chat_messages WHERE message='stable-send'")==1,'six concurrent retries insert exactly one message')
  conflict=call('sendChat.php',dict(payload,message='changed'));check(not conflict['success'] and conflict['code']=='CHAT_ID_CONFLICT','same client ID cannot change payload')
  sql("UPDATE orders SET status='finished' WHERE id=1")
  replay=call('sendChat.php',payload);check(replay['success'] and replay['replayed'],'ack lost before order finished still recovers original message')
  sql("UPDATE orders SET status='driver_accepted' WHERE id=1")
  failed=dict(payload,client_message_id='ccm-test-rollback-01',message='rollback-test')
  sql("CREATE TRIGGER reject_test_chat BEFORE INSERT ON chat_messages FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'")
  response=call('sendChat.php',failed);sql('DROP TRIGGER reject_test_chat')
  check(not response['success'] and scalar("SELECT COUNT(*) FROM chat_message_receipts WHERE client_message_id='ccm-test-rollback-01'")==0,'message insert failure rolls back receipt reservation')
  check(call('sendChat.php',failed)['success'],'same ID safely retries after rolled-back insert')
  fcm=call('sendChat.php',dict(payload,client_message_id='ccm-test-fcm-fail-01',message='fcm-test'),CHAT_TEST_FCM_FAIL='1')
  check(fcm['success'],'post-commit FCM exception still returns confirmed message')
  listing=call('get_customer_conversations.php');check(listing['success'] and listing['conversations'][0]['last_message']=='fcm-test','batched conversations lookup uses newest room message')
  sql('ALTER TABLE chat_messages DROP COLUMN room_key')
  fallback=call('getChat.php',query=query);check(fallback['success'] and fallback['messages'][-1]['id']>425,'page reader supports pre-index schema')
  print('PASS',checks,'real SQL chat regression scenarios')
 finally:
  try:run_script('$conn->query("DROP DATABASE `".getenv("CHAT_TEST_DB")."`");')
  except Exception:pass
