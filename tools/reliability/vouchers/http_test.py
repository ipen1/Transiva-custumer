import os,sys,subprocess,time,json,urllib.request,urllib.error,datetime,socket,tempfile,shutil
from pathlib import Path
HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'voucher-server'
from common import php_command, stop_process
php=php_command()
subprocess.run(php+[str(HERE/'http_setup.php')],check=True,timeout=60)
# The HTTP document root exists only for this run and never contains hosting credentials.
tmp=tempfile.TemporaryDirectory(prefix='transiva-voucher-http-')
root=Path(tmp.name)
for name in json.loads((HERE.parent/'voucher-sources.json').read_text()):shutil.copyfile(SOURCE/name,root/name)
for name,code in json.loads((HERE/'http-stubs.json').read_text()).items():(root/name).write_text('<?php\n'+code+'\n')
with socket.socket() as sock:
 sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
base_url='http://127.0.0.1:'+str(port)
log=open(os.environ.get('VOUCHER_HTTP_LOG','voucher-http.log'),'w')
env=os.environ.copy();env['VOUCHER_CI_DB_HELPER']=str(HERE/'db.php')
p=subprocess.Popen(php+['-S','127.0.0.1:'+str(port),'-t',str(root)],env=env,stdout=log,stderr=log)
try:
 for _ in range(50):
  try:
   urllib.request.build_opener(urllib.request.ProxyHandler({})).open(base_url+'/',timeout=.2);break
  except urllib.error.HTTPError:break
  except OSError:time.sleep(.1)
 else:raise RuntimeError('Fixture HTTP server did not start')
 opener=urllib.request.build_opener(urllib.request.ProxyHandler({}));n=0
 def post(path,data):
  req=urllib.request.Request(base_url+'/'+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
  try:r=opener.open(req,timeout=10)
  except urllib.error.HTTPError as e:r=e
  body=r.read().decode()
  try:return json.loads(body)
  except Exception:raise AssertionError('Non-JSON response '+body[:150])
 def check(got,msg):
  global n
  assert got,msg
  n+=1;print('PASS '+msg,flush=True)
 code={'order_total':58000,'delivery_fee':8000,'voucher_code':'FOOD50','service_type':'TransFood'}
 r=post('customer_royalty_vouchers.php',code);check(r.get('code_validation',{}).get('discount')==4000,'HTTP preview validates admin food promo against delivery')
 r=post('customer_royalty_vouchers.php',{**code,'voucher_code':'NO_SUCH_CODE'});check(r['code_validation']['eligible']==False and r['code_validation']['discount']==0,'HTTP invalid promo returns a reason and no local discount')
 r=post('customer_royalty_vouchers.php',{**code,'voucher_code':'RIDE'});check(not r['code_validation']['eligible'],'HTTP preview enforces service restriction')
 r=post('admin_manage_promos.php',{'action':'create','title':'Food test','description':'Fixture promo','promo_code':'ADMIN_FOOD','discount_type':'percent','discount_value':50,'service_type':'TransFood','min_order':20000})
 check(r.get('success') and r.get('promo',{}).get('service_type')=='TransFood','HTTP admin creates a TransFood-specific voucher')
 r=post('customer_royalty_vouchers.php',{**code,'voucher_code':'ADMIN_FOOD'});check(r['code_validation']['discount']==4000,'New admin TransFood voucher is usable by customer preview')
 base={'order_type':'TransRide','driver_type':'bike','pickup':{'latitude':-.705,'longitude':120.093,'address':'Test pickup'},'delivery':{'latitude':-.706,'longitude':120.094,'address':'Test destination'},'payment_method':'cash','note':'Test order','scheduled_at':(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=2)).isoformat()}
 def order(oid,code,**kw):return post('createOrder.php',{**base,'id':oid,'voucher_code':code,**kw})
 r=order('TEST-HTTP-SCHEDULE-1','FREE');check(r.get('success') and r.get('status')=='scheduled' and r.get('price')==0,'HTTP scheduled zero-pay ride commits successfully: '+str(r))
 r2=order('TEST-HTTP-SCHEDULE-1','FREE');check(r2==r,'HTTP scheduled retry replays the same result')
 r=order('TEST-HTTP-SCHEDULE-2','FREE');check(not r.get('success'),'HTTP scheduled voucher cannot be spent on a second booking')
 r=post('cancel_order.php',{'order_id':'TEST-HTTP-SCHEDULE-1','table':'orders'});check(r.get('success'),'HTTP customer can cancel scheduled booking: '+str(r))
 r=post('cancel_order.php',{'order_id':'TEST-HTTP-SCHEDULE-1','table':'orders'});check(r.get('success'),'HTTP cancellation retry is idempotent')
 r=order('TEST-HTTP-SCHEDULE-3','FREE');check(r.get('success'),'HTTP cancelled scheduled voucher becomes usable again')
 r=order('TEST-HTTP-LOYALTY-1','TRV-OWN');check(r.get('success'),'HTTP loyalty voucher can reserve a scheduled ride')
 r=order('TEST-HTTP-LOYALTY-2','TRV-OWN');check(not r.get('success'),'HTTP owned voucher cannot reserve a second scheduled ride')
 post('cancel_order.php',{'order_id':'TEST-HTTP-LOYALTY-1','table':'orders'})
 r=order('TEST-HTTP-NORMAL-ZERO','TRV-OWN',payment_method='balance',scheduled_at='',price_mode='hemat')
 check(r.get('success') and r.get('price')==0,'HTTP ordinary zero-pay balance ride does not attempt a debit or point redemption: '+str(r))
 print(f'RESULT: {n} HTTP endpoint checks passed (fixture auth, route, dispatch and point services)',flush=True)
finally:
 stop_process(p);log.close()

 tmp.cleanup()
