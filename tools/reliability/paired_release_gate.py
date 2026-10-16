#!/usr/bin/env python3
"""Two disposable API 36 emulators/devices, real release SDK media, ADB-only signaling. No production accounts."""
import argparse,base64,json,subprocess,threading,queue,time,secrets,pathlib,re

PROTOCOL_VERSION=2

def run(args,timeout=30):
 p=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
 if p.returncode:raise RuntimeError('Command failed: '+args[0]+' '+p.stderr[-500:])
 return p.stdout

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--caller',required=True);ap.add_argument('--callee',required=True);ap.add_argument('--turn',required=True);ap.add_argument('--turn-password',required=True);ap.add_argument('--output',default='paired-release.json');ap.add_argument('--diagnostics-dir',default='device-diagnostics/paired');a=ap.parse_args()
 assert a.caller!=a.callee,'Two distinct devices required'
 nonce=secrets.token_hex(12);events=queue.Queue();processes={};summaries={};readers={};devices={'caller':a.caller,'callee':a.callee}
 diagnostics=pathlib.Path(a.diagnostics_dir);diagnostics.mkdir(parents=True,exist_ok=True)
 def scrub(text):
  for value in (a.turn_password,nonce,a.caller,a.callee):
   if value:text=text.replace(value,'<redacted>')
  return text
 action='com.transiva.test.PAIR_'+nonce
 def adb(role,*args):return run(['adb','-s',devices[role],*args])
 def send(role,value):
  payload=base64.b64encode(json.dumps(value).encode()).decode()
  adb(role,'shell','am','broadcast','--receiver-foreground','-a',action,'-p','com.transiva.customer','--es','nonce',nonce,'--es','payload',payload)
 def reader(role,proc):
  transcript=[];size=0
  try:
   for line in proc.stdout:
    if line.startswith('INSTRUMENTATION_STATUS: pair_event='):
     try:events.put((role,json.loads(base64.b64decode(line.split('=',1)[1]))))
     except Exception as error:events.put((role,{'type':'error','message':str(error)}))
    else:
     clean=scrub(line);transcript.append(clean);size+=len(clean)
     while size>65536 and len(transcript)>1:size-=len(transcript.pop(0))
  except Exception as error:
   transcript.append('Reader error: '+scrub(str(error))+'\n')
  finally:
   summaries[role]=[line.strip() for line in transcript]
   (diagnostics/(role+'-instrumentation.txt')).write_text(''.join(transcript))
   events.put((role,{'type':'exit'}))
 report={'device_roles':['caller','callee'],'protocol_version':PROTOCOL_VERSION,'scope':'release SDK fixture; ADB signaling; TURN on CI host; no production backend','events':[]};ready=set();media=set();done={};answers=0;switched=False;pip=False;locked=False;phase_at=None;started=time.monotonic();network_deadline=None;network_changed=False;release_sent=False
 try:
  for role,serial in devices.items():
   sdk=int(adb(role,'shell','getprop','ro.build.version.sdk').strip());assert sdk>=36,'API 36+ required'
   adb(role,'shell','input','keyevent','224');adb(role,'shell','wm','dismiss-keyguard');adb(role,'shell','svc','wifi','enable')
   adb(role,'shell','pm','grant','com.transiva.customer','android.permission.RECORD_AUDIO')
   args=['adb','-s',serial,'shell','am','instrument','-w','-r','-e','class','com.transiva.app.ReleasePairedCallTest','-e','pair_nonce',nonce,'-e','pair_role',role,'-e','pair_turn',a.turn,'-e','pair_turn_password',a.turn_password,'com.transiva.customer.test/androidx.test.runner.AndroidJUnitRunner']
   processes[role]=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
   readers[role]=threading.Thread(target=reader,args=(role,processes[role]),daemon=True);readers[role].start()
  limit=time.monotonic()+210
  while time.monotonic()<limit and len(done)<2:
   # Continuous ICE/status traffic must not postpone the observation deadline.
   if phase_at and time.monotonic()-phase_at>12 and pip and locked:
    send('caller',{'type':'finish'});send('callee',{'type':'finish'});phase_at=None
   try:role,event=events.get(timeout=1)
   except queue.Empty:continue
   kind=event['type'];report['events'].append({'role':role,'type':kind,'elapsed_s':round(time.monotonic()-started,2)})
   if kind=='ready':
    assert event.get('protocol_version')==PROTOCOL_VERSION,'Paired test APK protocol mismatch on '+role+': rebuild/install ReleasePairedCallTest protocol 2'
    ready.add(role)
    if len(ready)==2:send('caller',{'type':'go'})
   elif kind in ('offer','answer','candidate'):
    send('callee' if role=='caller' else 'caller',event)
    if kind=='answer':
     answers+=1
     if answers==2:
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
   elif kind=='done':
    assert role not in done,'Duplicate verification acknowledgement: '+role
    done[role]={k:event[k] for k in ('video_frames','audio_packets')}
    assert done[role]['video_frames']>3 and done[role]['audio_packets']>0,'Invalid media verification: '+role
   elif kind=='error':raise AssertionError('Paired fixture error on '+role+': '+scrub(str(event.get('message','unknown'))))
   elif kind=='exit':
    raise AssertionError('Device '+role+' exited before mutual release: '+'\n'.join(summaries.get(role,[]))[-4000:])
  assert len(done)==2 and answers>=2 and pip and locked and network_changed,'Paired media/network/PiP/lock gate incomplete'
  # Both devices must finish their media/stat assertions before either closes
  # its PeerConnection. done alone is still not final JUnit success.
  for role in devices:send(role,{'type':'release'})
  release_sent=True
  for role,p in processes.items():
   assert p.wait(timeout=30)==0,'Instrumentation process failed: '+role
   # sendStatus(done) is not enough: final JUnit completion/cleanup must also pass.
   deadline=time.monotonic()+5
   while role not in summaries and time.monotonic()<deadline:time.sleep(.1)
   assert any('OK (1 test)' in line for line in summaries.get(role,[])),role+': '+'\n'.join(summaries.get(role,[]))[-4000:]
  report.update(result='passed',media=done,network_switch='emulated Wi-Fi off / cellular on; not a physical-carrier NAT test')
 except Exception as error:
  report['failure']={'type':type(error).__name__,'message':scrub(str(error))};raise
 finally:
  for role,p in processes.items():
   if p.poll() is None:p.terminate()
   try:adb(role,'shell','input','keyevent','224');adb(role,'shell','wm','dismiss-keyguard');adb(role,'shell','svc','wifi','enable')
   except Exception:pass
  for thread in readers.values():thread.join(timeout=5)
  report.update(verified_peers=sorted(done),release_sent=release_sent)
  report.setdefault('media',done);report.setdefault('result','failed')
  output=pathlib.Path(a.output);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2))
if __name__=='__main__':main()
