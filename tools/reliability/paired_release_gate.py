#!/usr/bin/env python3
"""Two disposable API 36 emulators/devices, real release SDK media, ADB-only signaling. No production accounts."""
import argparse,base64,json,subprocess,threading,queue,time,secrets,pathlib,re

def run(args,timeout=30):
 p=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
 if p.returncode:raise RuntimeError('Command failed: '+args[0]+' '+p.stderr[-500:])
 return p.stdout

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--caller',required=True);ap.add_argument('--callee',required=True);ap.add_argument('--turn',required=True);ap.add_argument('--turn-password',required=True);ap.add_argument('--output',default='paired-release.json');a=ap.parse_args()
 assert a.caller!=a.callee,'Two distinct devices required'
 nonce=secrets.token_hex(12);events=queue.Queue();processes={};summaries={};devices={'caller':a.caller,'callee':a.callee}
 action='com.transiva.test.PAIR_'+nonce
 def adb(role,*args):return run(['adb','-s',devices[role],*args])
 def send(role,value):
  payload=base64.b64encode(json.dumps(value).encode()).decode()
  adb(role,'shell','am','broadcast','--receiver-foreground','-a',action,'-p','com.transiva.customer','--es','nonce',nonce,'--es','payload',payload)
 def reader(role,proc):
  summary=[]
  for line in proc.stdout:
   if line.startswith('INSTRUMENTATION_STATUS: pair_event='):
    try:events.put((role,json.loads(base64.b64decode(line.split('=',1)[1]))))
    except Exception as error:events.put((role,{'type':'error','message':str(error)}))
   elif any(s in line for s in ['FAILURES','INSTRUMENTATION_FAILED','shortMsg','OK (','Failure in']):summary.append(line.strip())
  summaries[role]=summary;events.put((role,{'type':'exit'}))
 report={'device_roles':['caller','callee'],'scope':'release SDK fixture; ADB signaling; TURN on CI host; no production backend','events':[]};ready=set();media=set();done={};answers=0;switched=False;pip=False;locked=False;phase_at=None;started=time.monotonic();network_deadline=None;network_changed=False
 try:
  for role,serial in devices.items():
   sdk=int(adb(role,'shell','getprop','ro.build.version.sdk').strip());assert sdk>=36,'API 36+ required'
   adb(role,'shell','input','keyevent','224');adb(role,'shell','wm','dismiss-keyguard');adb(role,'shell','svc','wifi','enable')
   adb(role,'shell','pm','grant','com.transiva.customer','android.permission.RECORD_AUDIO')
   args=['adb','-s',serial,'shell','am','instrument','-w','-r','-e','class','com.transiva.app.ReleasePairedCallTest','-e','pair_nonce',nonce,'-e','pair_role',role,'-e','pair_turn',a.turn,'-e','pair_turn_password',a.turn_password,'com.transiva.customer.test/androidx.test.runner.AndroidJUnitRunner']
   processes[role]=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
   threading.Thread(target=reader,args=(role,processes[role]),daemon=True).start()
  limit=time.monotonic()+210
  while time.monotonic()<limit and len(done)<2:
   try:role,event=events.get(timeout=1)
   except queue.Empty:
    if phase_at and time.monotonic()-phase_at>12 and pip and locked:
     send('caller',{'type':'finish'});send('callee',{'type':'finish'});phase_at=None
    continue
   kind=event['type'];report['events'].append({'role':role,'type':kind,'elapsed_s':round(time.monotonic()-started,2)})
   if kind=='ready':
    ready.add(role)
    if len(ready)==2:send('caller',{'type':'go'})
   elif kind in ('offer','answer','candidate'):
    send('callee' if role=='caller' else 'caller',event)
    if kind=='answer':
     answers+=1
     if answers>=2:
      phase_at=time.monotonic();send('caller',{'type':'pip'});adb('callee','shell','input','keyevent','223');time.sleep(1);send('callee',{'type':'lock_state'})
   elif kind=='media':
    media.add(role)
    if len(media)==2 and not switched:
     switched=True
     report['active_call_pss_kb']={}
     for participant in devices:
      match=re.search(r'TOTAL PSS:\s*(\d+)',adb(participant,'shell','dumpsys','meminfo','com.transiva.customer'))
      report['active_call_pss_kb'][participant]=int(match.group(1)) if match else None
     send('caller',{'type':'network_before'})
   elif kind=='network_before':
    assert event.get('transport')=='wifi','Caller did not start on Wi-Fi'
    report['network_before']='wifi';adb('caller','shell','svc','data','enable');adb('caller','shell','svc','wifi','disable');network_deadline=time.monotonic()+25;send('caller',{'type':'network_after'})
   elif kind=='network_after':
    if event.get('transport')=='cellular':
     network_changed=True;report['network_after']='cellular';send('caller',{'type':'restart'})
    else:
     assert time.monotonic()<network_deadline,'Caller did not switch to cellular';time.sleep(1);send('caller',{'type':'network_after'})
   elif kind=='pip':pip=bool(event.get('active'));assert pip,'PiP did not become active'
   elif kind=='lock':locked=bool(event.get('active'));assert locked,'Callee did not reach keyguard'
   elif kind=='done':done[role]={k:event[k] for k in ('video_frames','audio_packets')}
   elif kind=='error':raise AssertionError('Paired fixture error')
   elif kind=='exit' and role not in done:raise AssertionError('Device test exited before success: '+str(summaries.get(role)))
  assert len(done)==2 and answers>=2 and pip and locked and network_changed,'Paired media/network/PiP/lock gate incomplete'
  for role,p in processes.items():
   p.wait(timeout=30)
   # sendStatus(done) is not enough: final JUnit completion/cleanup must also pass.
   deadline=time.monotonic()+5
   while role not in summaries and time.monotonic()<deadline:time.sleep(.1)
   assert any('OK (1 test)' in line for line in summaries.get(role,[])),str(summaries.get(role))
  report.update(result='passed',media=done,network_switch='emulated Wi-Fi off / cellular on; not a physical-carrier NAT test')
 finally:
  for role,p in processes.items():
   if p.poll() is None:p.terminate()
   try:adb(role,'shell','input','keyevent','224');adb(role,'shell','wm','dismiss-keyguard');adb(role,'shell','svc','wifi','enable')
   except Exception:pass
  report.setdefault('result','failed');pathlib.Path(a.output).write_text(json.dumps(report,indent=2))
if __name__=='__main__':main()
