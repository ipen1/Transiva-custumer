#!/usr/bin/env python3
"""Validate host orchestration and fail-closed JUnit handling with simulated ADB. Not device/media evidence."""
import importlib.util,pathlib,queue,base64,json,tempfile,sys,time,types,threading
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('paired_host',pathlib.Path(__file__).with_name('paired_release_gate.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
real_sleep=time.sleep
class Process:
 def __init__(self,good):self.lines=queue.Queue();self.code=None;self.good=good;self.stdout=self.stream();self.exited=threading.Event()
 def emit(self,event):self.lines.put('INSTRUMENTATION_STATUS: pair_event='+base64.b64encode(json.dumps(event).encode()).decode()+'\n')
 def stream(self):
  while True:
   line=self.lines.get()
   if line is None:self.code=0;self.exited.set();return
   yield line
 def poll(self):return self.code
 def wait(self,timeout):assert self.exited.wait(timeout);real_sleep(.01);return self.code
 def terminate(self):self.lines.put(None)
class Adb:
 def __init__(self,good):self.good=good;self.processes={};self.offer=0;self.clock=0
 def popen(self,args,**kwargs):
  role='caller' if args[args.index('-s')+1]=='one' else 'callee';p=Process(self.good);self.processes[role]=p;p.emit({'type':'ready'});return p
 def run(self,args,**kwargs):
  role='caller' if args[args.index('-s')+1]=='one' else 'callee';out=''
  if 'getprop' in args:out='36\n'
  if 'meminfo' in args:out='TOTAL PSS: 32000\n'
  if 'broadcast' in args:
   v=json.loads(base64.b64decode(args[args.index('payload')+1]));kind=v['type'];p=self.processes[role]
   if kind in ('go','restart'):
    self.offer+=1;p.emit({'type':'offer','sdp':'offer-'+str(self.offer)})
   elif kind=='offer':p.emit({'type':'answer','sdp':'answer-'+str(self.offer)})
   elif kind=='answer' and self.offer==1:
    for peer in self.processes.values():peer.emit({'type':'media'})
   elif kind.startswith('network_'):p.emit({'type':kind,'transport':'wifi' if kind=='network_before' else 'cellular'})
   elif kind in ('pip','lock_state'):p.emit({'type':'pip' if kind=='pip' else 'lock','active':True})
   elif kind=='finish':
    p.emit({'type':'done','audio_packets':10,'video_frames':100});p.lines.put('OK (1 test)\n' if self.good else 'FAILURES!!!\n');p.lines.put(None)
  return types.SimpleNamespace(returncode=0,stdout=out,stderr='')
 def now(self):self.clock+=2;return self.clock
for good in (True,False):
 fixture=Adb(good)
 with tempfile.TemporaryDirectory(prefix='paired-host-test-') as directory:
  args=['paired','--caller','one','--callee','two','--turn','turn:fixture:3478','--turn-password','fixture','--output',directory+'/report.json']
  failed=False
  with patch.object(sys,'argv',args),patch.object(module.subprocess,'run',fixture.run),patch.object(module.subprocess,'Popen',fixture.popen),patch.object(module.time,'monotonic',fixture.now),patch.object(module.time,'sleep',lambda seconds:real_sleep(.001)):
   try:module.main()
   except AssertionError:failed=True
  assert failed!=good,'Gate accepted incomplete JUnit result' if not good else 'Valid bridge failed'
  print('PASS paired host '+('bridges two peers, network, PiP and lock' if good else 'rejects done events without final JUnit success'))
