#!/usr/bin/env python3
"""Real SQL + multipart HTTP regression. Uses only a disposable test database.
Run: TEST_DB_PORT=3306 PHP_BIN=php python3 tools/reliability/chat_upgrade_regression.py /path/to/server
"""
from pathlib import Path
import os,sys,json,subprocess,tempfile,shutil,uuid,time,socket,urllib.request,urllib.error,struct,base64
from concurrent.futures import ThreadPoolExecutor
root=Path(__file__).resolve().parent
source=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'chat-server'
php=os.getenv('PHP_BIN','php');checks=0
with tempfile.TemporaryDirectory(prefix='transiva-chat-upgrade-') as directory:
 p=Path(directory);env=dict(os.environ,CHAT_TEST_DB='transiva_chat_test_'+uuid.uuid4().hex)
 def put(name,content):(p/name).write_text(content)
 for name in ['sendChat.php','chat_delivery_helper.php','chat_notification_helper.php','chat_media_helper.php','chat_operation_status.php','upload_chat_image.php','upload_chat_voice.php','search_chat.php','chat_security_helper.php','customer_support_native.php','customer_support_order_helper.php']:
  shutil.copy2(source/name,p/name)
  if name=='upload_chat_voice.php':(p/name).write_text((p/name).read_text().replace("error_log('CHAT_VOICE_UNCONFIRMED')","error_log('CHAT_VOICE_UNCONFIRMED '.get_class($e).' '.$e->getMessage())"))
  if name=='upload_chat_image.php':(p/name).write_text((p/name).read_text().replace("error_log('CHAT_IMAGE_UNCONFIRMED')","error_log('CHAT_IMAGE_UNCONFIRMED '.get_class($e).' '.$e->getMessage())"))
 put('config.php',r'''<?php mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$db=getenv('CHAT_TEST_DB');if(!preg_match('/^transiva_chat_test_[a-f0-9]{32}$/D',$db))throw new RuntimeException('Disposable DB required');
$conn=new mysqli(getenv('TEST_DB_HOST')?:'127.0.0.1',getenv('TEST_DB_USER')?:'root',getenv('TEST_DB_PASSWORD')?:'',null,(int)(getenv('TEST_DB_PORT')?:3306));$conn->query("CREATE DATABASE IF NOT EXISTS `$db`");$conn->select_db($db);$conn->set_charset('utf8mb4');
''')
 put('native_auth.php',r'''<?php
function native_require_user($c){$id=(int)($_SERVER['HTTP_X_TEST_USER']??1);return ['id'=>$id,'role'=>$id===3?'driver':'customer','username'=>$id===3?'driver':'customer'];}
function native_table_exists($c,$t){return $c->query("SHOW TABLES LIKE '".$c->real_escape_string($t)."'")->num_rows>0;}
function native_column_exists($c,$t,$n){return $c->query("SHOW COLUMNS FROM `$t` LIKE '".$c->real_escape_string($n)."'")->num_rows>0;}
''')
 put('p0_security_helper.php',"<?php require_once __DIR__.'/native_auth.php';function p0_require_customer($c){return native_require_user($c);}function p0_rate_limit(...$a){}function p0_client_ip(){return 'fixture';}")
 put('schema_runtime_guard.php',"<?php function transiva_schema_query($c,$sql){return $c->query($sql);}")
 put('send_fcm.php',"<?php function send_fcm_notification(...$a){if(is_file(__DIR__.'/fcm-fail'))return ['success'=>false];return ['success'=>true];}")
 put('sql.php',"<?php require __DIR__.'/config.php';$r=$conn->query(getenv('CHAT_TEST_SQL'));if($r instanceof mysqli_result)echo json_encode($r->fetch_all(MYSQLI_ASSOC));")
 def sql(query):
  r=subprocess.run([php,str(p/'sql.php')],env=dict(env,CHAT_TEST_SQL=query),text=True,capture_output=True)
  if r.returncode:raise AssertionError(r.stdout+r.stderr)
  return json.loads(r.stdout or '[]')
 def count(table):return int(sql('SELECT COUNT(*) AS n FROM '+table)[0]['n'])
 def check(ok,name):
  global checks
  if not ok:raise AssertionError(name)
  checks+=1;print('PASS',name,flush=True)
 with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
 log=open(p/'http.log','w');http=subprocess.Popen([php,'-S',f'127.0.0.1:{port}','-t',str(p)],env=dict(env,PHP_CLI_SERVER_WORKERS='4'),stdout=log,stderr=log)
 def request(endpoint,payload=None,files=None,user=1,query=''):
  headers={'X-Test-User':str(user)};body=None
  if files is not None:
   boundary='transiva-test-'+uuid.uuid4().hex;chunks=[]
   for key,value in (payload or {}).items():chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
   for key,bytes_,mime in files:chunks.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"; filename="test.bin"\r\nContent-Type: {mime}\r\n\r\n'.encode(),bytes_,b'\r\n'])
   chunks.append(f'--{boundary}--\r\n'.encode());body=b''.join(chunks);headers['Content-Type']='multipart/form-data; boundary='+boundary
  elif payload is not None:body=json.dumps(payload).encode();headers['Content-Type']='application/json'
  req=urllib.request.Request(f'http://127.0.0.1:{port}/{endpoint}'+query,data=body,headers=headers)
  try:r=urllib.request.urlopen(req,timeout=20)
  except urllib.error.HTTPError as e:r=e
  raw=r.read().decode()
  try:return json.loads(raw)
  except Exception:raise AssertionError(raw+'\n'+(p/'http.log').read_text())
 try:
  for _ in range(60):
   try:socket.create_connection(('127.0.0.1',port),.1).close();break
   except OSError:time.sleep(.1)
  sql("CREATE TABLE users(id INT PRIMARY KEY,username VARCHAR(60),role VARCHAR(20),fcm_token VARCHAR(100)) ENGINE=InnoDB")
  sql("INSERT INTO users VALUES(1,'customer','customer','c'),(2,'other','customer','o'),(3,'driver','driver','d')")
  sql("CREATE TABLE orders(id INT PRIMARY KEY,user_id INT,order_id VARCHAR(60),room_id VARCHAR(100),status VARCHAR(40),driver VARCHAR(60),price INT DEFAULT 10000) ENGINE=InnoDB")
  sql("INSERT INTO orders(id,user_id,order_id,room_id,status,driver) VALUES(1,1,'A','ROOM-A','driver_accepted','driver'),(2,2,'B','ROOM-B','driver_accepted','driver')")
  sql("CREATE TABLE chat_messages(id INT AUTO_INCREMENT PRIMARY KEY,room_id VARCHAR(100),sender_type VARCHAR(20),message TEXT,created_at DATETIME,is_read INT DEFAULT 0,read_at DATETIME) ENGINE=InnoDB")
  sql("CREATE TABLE order_notification_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(190) UNIQUE,kind VARCHAR(32),payload_json LONGTEXT,status VARCHAR(16) DEFAULT 'pending',attempts INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
  sql((source/'maintenance/chat_reliability_schema.sql').read_text())
  for statement in (source/'maintenance/chat_media_upgrade.sql').read_text().split(';'):
   if statement.strip():sql(statement)
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6iUAAAAAASUVORK5CYII=')
  image=[('preview_image',png,'image/png'),('hd_image',png,'image/png')]
  payload=dict(room_id='ROOM-A',source='orders',order_id='A',order_db_id='1',sender_type='driver',operation_id='ccm-test-image-concurrent-01')
  forbidden=request('upload_chat_image.php',payload,image,user=2);check(not forbidden['success'] and forbidden['code']=='CHAT_FORBIDDEN' and count('chat_messages')==0,'another customer cannot inject a photo')
  with ThreadPoolExecutor(max_workers=6) as pool:responses=list(pool.map(lambda _:request('upload_chat_image.php',payload,image),range(6)))
  if not all(r['success'] for r in responses):print(responses,(p/'http.log').read_text(),flush=True)
  check(all(r['success'] for r in responses) and len({r['id'] for r in responses})==1 and count('chat_messages')==1 and count('chat_media_receipts')==1 and count('order_notification_outbox')==1,'six concurrent photo retries create one message receipt and outbox job')
  check(sql('SELECT sender_type FROM chat_messages')[0]['sender_type']=='customer','photo sender is derived from verified account despite spoofed sender_type')
  changed=[('preview_image',png+b'changed','image/png'),('hd_image',png,'image/png')]
  conflict=request('upload_chat_image.php',payload,changed);check(not conflict['success'] and conflict['code']=='CHAT_ID_CONFLICT','same photo operation ID rejects changed file hash')
  sql("UPDATE orders SET status='finished' WHERE id=1")
  replay=request('upload_chat_image.php',payload,image);check(replay['success'] and replay['replayed'] and count('chat_messages')==1,'lost photo acknowledgment recovers after order finishes')
  before=(count('chat_messages'),count('chat_media_receipts'),count('order_notification_outbox'))
  status=request('chat_operation_status.php',query='?operation_id='+payload['operation_id']);check(status['success'] and status['id']==responses[0]['id'] and before==(count('chat_messages'),count('chat_media_receipts'),count('order_notification_outbox')),'operation status lookup does not mutate messages receipts or outbox')
  other=request('chat_operation_status.php',user=2,query='?operation_id='+payload['operation_id']);check(not other['success'],'other account cannot read operation result')
  sql("UPDATE orders SET status='driver_accepted' WHERE id=1")
  sql("CREATE TRIGGER reject_outbox BEFORE INSERT ON order_notification_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'")
  failed=dict(payload,operation_id='ccm-test-image-rollback-01');r=request('upload_chat_image.php',failed,image)
  check(not r['success'] and before==(count('chat_messages'),count('chat_media_receipts'),count('order_notification_outbox')),'outbox insertion failure rolls back message and media receipt')
  sql('DROP TRIGGER reject_outbox');check(request('upload_chat_image.php',failed,image)['success'],'same photo ID retries safely after rollback')
  voice=struct.pack('>I',24)+b'ftypM4A '+struct.pack('>I',0)+b'M4A mp42'
  vp=dict(payload,operation_id='ccm-test-voice-concurrent-01',duration_ms='3000')
  audio=[('audio',voice,'audio/mp4')]
  with ThreadPoolExecutor(max_workers=5) as pool:vr=list(pool.map(lambda _:request('upload_chat_voice.php',vp,audio),range(5)))
  if not all(r['success'] for r in vr):print(vr,(p/'http.log').read_text(),flush=True)
  check(all(r['success'] for r in vr) and len({r['id'] for r in vr})==1 and int(sql("SELECT COUNT(*) AS n FROM chat_messages WHERE message LIKE '[[VOICE]]%'")[0]['n'])==1,'voice multipart retries create exactly one voice message')
  legacy=dict(vp);legacy.pop('operation_id');n=count('chat_messages');lr=request('upload_chat_voice.php',legacy,audio);check(lr['success'] and count('chat_messages')==n,'legacy driver voice upload remains upload-only')
  text=dict(room_id='ROOM-A',source='orders',order_id='A',message='search 100% literal_test',client_message_id='ccm-test-text-notification-01')
  tx=request('sendChat.php',text);check(tx['success'] and count('chat_messages')==count('order_notification_outbox'),'text messages and notification jobs commit together')
  put('fcm-fail','1');put('deliver.php',"<?php require __DIR__.'/config.php';require __DIR__.'/send_fcm.php';require __DIR__.'/chat_notification_helper.php';$p=json_decode($conn->query(\"SELECT payload_json FROM order_notification_outbox ORDER BY id DESC LIMIT 1\")->fetch_row()[0],true);try{chat_notification_deliver($conn,$p);echo 'sent';}catch(Throwable $e){echo 'retry';}")
  r=subprocess.check_output([php,str(p/'deliver.php')],env=env,text=True);check(r=='retry' and request('sendChat.php',text)['replayed'] and count('chat_messages')==count('order_notification_outbox'),'FCM failure keeps committed intent and replay does not duplicate jobs')
  (p/'fcm-fail').unlink();check(subprocess.check_output([php,str(p/'deliver.php')],env=env,text=True)=='sent','notification helper delivers once provider recovers')
  from urllib.parse import urlencode
  query='?'+urlencode(dict(room_id='ROOM-A',source='orders',order_id='A',q='100% literal_'))
  sr=request('search_chat.php',query=query);check(sr['success'] and len(sr['results'])==1 and int(sr['results'][0]['id'])==tx['chat']['id'],'search treats percent and underscore as literal characters')
  check(not request('search_chat.php',user=2,query=query)['success'],'conversation search rejects another account')
  jump=request('search_chat.php',query='?room_id=ROOM-A&order_id=A&target_id='+str(tx['chat']['id']));check(jump['success'] and int(jump['messages'][-1]['id'])==tx['chat']['id'],'search jump returns context ending at requested message')
  complaint=dict(category='Pesanan / perjalanan',subject='Keluhan pesanan A',detail='Mohon periksa kendala pesanan saya.',order_id='A',source='orders',attachment_base64=base64.b64encode(png).decode())
  check(not request('customer_support_native.php',complaint,user=2)['success'],'order complaint rejects another customer order')
  ticket=request('customer_support_native.php',complaint);detail=sql('SELECT detail FROM customer_support_tickets')[0]['detail'];check(ticket['success'] and 'Order: A' in detail and 'Status: driver_accepted' in detail and 'Bukti foto:' in detail,'complaint stores server-derived order evidence and selected photo')
  print('PASS',checks,'upgrade HTTP/SQL scenarios',flush=True)
 finally:
  try:sql('DROP DATABASE `'+env['CHAT_TEST_DB']+'`')
  finally:http.terminate();http.wait(timeout=5);log.close()
