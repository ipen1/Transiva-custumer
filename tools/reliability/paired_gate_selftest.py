#!/usr/bin/env python3
"""Exercise host/peer lifecycle interleavings with simulated ADB, not device media."""
import base64
import importlib.util
import json
import pathlib
import queue
import sys
import tempfile
import threading
import time
import types
import unittest
import subprocess
import shutil
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('paired_host',pathlib.Path(__file__).with_name('paired_release_gate.py'))
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
real_sleep=time.sleep


class Process:
    def __init__(self):
        self.lines=queue.Queue();self.code=None;self.exit_code=0
        self.stdout=self.stream();self.exited=threading.Event()
    def emit(self,event):
        self.lines.put('INSTRUMENTATION_STATUS: pair_event='+base64.b64encode(json.dumps(event).encode()).decode()+'\n')
    def stream(self):
        while True:
            line=self.lines.get()
            if line is None:
                self.code=self.exit_code;self.exited.set();return
            yield line
    def complete(self,good=True,code=0,message='cleanup failed'):
        self.exit_code=code
        if good:self.lines.put('OK (1 test)\n')
        else:
            self.lines.put('INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: '+message+'\n')
            self.lines.put('    at com.transiva.app.ReleasePairedCallTest.check(ReleasePairedCallTest.java:123)\n')
            self.lines.put('FAILURES!!!\n')
        self.lines.put(None)
    def poll(self):return self.code
    def wait(self,timeout):
        if not self.exited.wait(min(timeout,2)):
            raise AssertionError('Fixture peer still held: host never sent mutual release')
        real_sleep(.005);return self.code
    def terminate(self):self.lines.put(None)


class Adb:
    def __init__(self,mode='normal'):
        self.mode=mode;self.processes={};self.offer=0;self.clock=0
        self.verified=set();self.releases=[];self.closed=set();self.commands=[]
        self.password='ci-test-secret';self.nonce=''
        self.lock_checks=0;self.settings=[];self.lock_confirmed_at=None;self.finish_times=[]
    def now(self):self.clock+=.25;return self.clock
    def popen(self,args,**kwargs):
        role='caller' if args[args.index('-s')+1]=='one-device' else 'callee'
        self.nonce=args[args.index('pair_nonce')+1]
        p=Process();self.processes[role]=p
        version=1 if self.mode=='old_apk' and role=='callee' else 2
        p.emit({'type':'ready','protocol_version':version})
        return p
    def verify(self,role):
        p=self.processes[role]
        if self.mode=='audio_failure' and role=='callee':
            p.complete(False,message='No inbound audio packets '+self.password+' '+self.nonce);return
        if self.mode=='legacy_teardown' and 'caller' in self.closed and role=='callee':
            p.complete(False,message='Peer disconnected before callee verification');return
        self.verified.add(role);p.emit({'type':'done','audio_packets':10,'video_frames':100})
        if self.mode=='legacy_teardown' and role=='caller':
            self.closed.add(role);p.complete()
    def run(self,args,**kwargs):
        role='caller' if args[args.index('-s')+1]=='one-device' else 'callee';out=''
        if 'getprop' in args:out='36\n'
        if 'settings' in args:
            self.settings.append(args[args.index('settings')+1:])
            if 'get' in args:out='null\n' if self.mode=='unset_lock_timeout' else '5000\n'
        if 'meminfo' in args:out='TOTAL PSS: 32000\n'
        if 'broadcast' in args:
            value=json.loads(base64.b64decode(args[args.index('payload')+1]));kind=value['type']
            self.commands.append((role,kind));p=self.processes[role]
            if kind in ('go','restart'):
                self.offer+=1;p.emit({'type':'offer','sdp':'fixture-offer-'+str(self.offer)})
            elif kind=='offer':p.emit({'type':'answer','sdp':'fixture-answer-'+str(self.offer)})
            elif kind=='answer' and self.offer==1:
                for peer in self.processes.values():peer.emit({'type':'media'})
            elif kind.startswith('network_'):
                p.emit({'type':kind,'transport':'wifi' if kind=='network_before' else 'cellular'})
            elif kind=='pip':p.emit({'type':'pip','active':True})
            elif kind=='lock_state':
                self.lock_checks+=1
                active=self.mode!='never_locked' and (self.mode!='delayed_lock' or self.lock_checks>=3)
                if active and self.lock_confirmed_at is None:self.lock_confirmed_at=self.clock
                if self.mode!='silent_lock':p.emit({'type':'lock','active':active})
            elif kind=='finish':
                self.finish_times.append(self.clock)
                if self.mode=='slow_callee' and role=='callee':
                    timer=threading.Timer(.03,self.verify,args=(role,));timer.daemon=True;timer.start()
                else:self.verify(role)
            elif kind=='release':
                if self.verified!={'caller','callee'}:
                    raise AssertionError('Host closed a peer before both verifications')
                self.releases.append(role);self.closed.add(role)
                p.complete(good=not(self.mode=='bad_junit' and role=='callee'),
                           code=1 if self.mode=='bad_exit' and role=='callee' else 0)
        return types.SimpleNamespace(returncode=0,stdout=out,stderr='')


def run_fixture(fixture,directory,diagnostics_arg=True):
    report=pathlib.Path(directory)/'report.json';logs=pathlib.Path(directory)/'diagnostics'
    args=['paired','--caller','one-device','--callee','two-device','--turn','turn:fixture:3478',
          '--turn-password',fixture.password,'--output',str(report)]
    if diagnostics_arg:args+=['--diagnostics-dir',str(logs)]
    error=None
    class FastQueue(queue.Queue):
        def get(self,block=True,timeout=None):return super().get(block, min(timeout,.002) if timeout is not None else None)
    with patch.object(gate,'queue',types.SimpleNamespace(Queue=FastQueue,Empty=queue.Empty)),patch.object(sys,'argv',args),patch.object(gate.subprocess,'run',fixture.run),patch.object(gate.subprocess,'Popen',fixture.popen),patch.object(gate.time,'monotonic',fixture.now),patch.object(gate.time,'sleep',lambda seconds:real_sleep(.001)):
        try:gate.main()
        except (AssertionError,RuntimeError) as caught:error=caught
    return json.loads(report.read_text()),logs,error


class PairedGateTest(unittest.TestCase):
    def run_case(self,mode):
        temp=tempfile.TemporaryDirectory(prefix='paired-host-test-');self.addCleanup(temp.cleanup)
        fixture=Adb(mode);report,logs,error=run_fixture(fixture,temp.name)
        return fixture,report,logs,error
    def test_normal_mutual_release_and_final_junit(self):
        f,r,logs,error=self.run_case('normal')
        self.assertIsNone(error);self.assertEqual('passed',r['result'])
        self.assertEqual(['callee','caller'],r['verified_peers']);self.assertTrue(r['release_sent'])
        self.assertEqual(['caller','callee'],f.releases)
        self.assertEqual('cellular',r['network_after'])
        self.assertIn('OK (1 test)',(logs/'callee-instrumentation.txt').read_text())
    def test_delayed_keyguard_retries_and_observes_media_after_lock(self):
        f,r,logs,error=self.run_case('delayed_lock')
        self.assertIsNone(error);self.assertEqual('passed',r['result'])
        self.assertGreaterEqual(f.lock_checks,3);self.assertTrue(r['keyguard_observed'])
        self.assertTrue(all(t-f.lock_confirmed_at>12 for t in f.finish_times))
        self.assertIn(['put','secure','lock_screen_lock_after_timeout','0'],f.settings)
        self.assertEqual(['put','secure','lock_screen_lock_after_timeout','5000'],f.settings[-1])
    def test_unset_keyguard_timeout_restored_by_deleting_override(self):
        f,r,logs,error=self.run_case('unset_lock_timeout')
        self.assertIsNone(error)
        self.assertEqual(['delete','secure','lock_screen_lock_after_timeout'],f.settings[-1])
    def test_keyguard_never_active_fails_without_finishing_media(self):
        f,r,logs,error=self.run_case('never_locked')
        self.assertIsNotNone(error);self.assertIn('keyguard within 15 seconds',r['failure']['message'])
        self.assertEqual([],f.finish_times);self.assertEqual([],f.releases)
        self.assertTrue((logs/'callee-window-policy.txt').exists())
        self.assertEqual(['put','secure','lock_screen_lock_after_timeout','5000'],f.settings[-1])
    def test_missing_keyguard_reply_has_bounded_failure(self):
        f,r,logs,error=self.run_case('silent_lock')
        self.assertIsNotNone(error);self.assertIn('keyguard within 15 seconds',r['failure']['message'])
        self.assertEqual([],f.releases)
    def test_slow_callee_is_not_disconnected_by_caller(self):
        f,r,logs,error=self.run_case('slow_callee')
        self.assertIsNone(error);self.assertEqual('passed',r['result'])
        self.assertEqual(2,len(f.releases))
    def test_done_does_not_replace_final_junit(self):
        f,r,logs,error=self.run_case('bad_junit')
        self.assertIsNotNone(error);self.assertEqual('failed',r['result'])
        self.assertIn('cleanup failed',r['failure']['message'])
        self.assertIn('ReleasePairedCallTest.java:123',(logs/'callee-instrumentation.txt').read_text())
    def test_nonzero_process_exit_rejected_despite_ok_text(self):
        f,r,logs,error=self.run_case('bad_exit')
        self.assertIsNotNone(error);self.assertEqual('failed',r['result'])
    def test_legacy_teardown_race_is_rejected(self):
        f,r,logs,error=self.run_case('legacy_teardown')
        self.assertIsNotNone(error);self.assertEqual('failed',r['result'])
        self.assertFalse(r['release_sent']);self.assertEqual([],f.releases)
    def test_old_test_apk_detected_before_media(self):
        f,r,logs,error=self.run_case('old_apk')
        self.assertIsNotNone(error);self.assertIn('protocol mismatch',r['failure']['message'])
        self.assertEqual([],f.commands)
    def test_actual_peer_assertion_saved_and_secrets_redacted(self):
        f,r,logs,error=self.run_case('audio_failure')
        self.assertIsNotNone(error);self.assertEqual('failed',r['result'])
        text=(logs/'callee-instrumentation.txt').read_text()
        self.assertIn('No inbound audio packets',text)
        self.assertIn('No inbound audio packets',r['failure']['message'])
        for content in [text,json.dumps(r)]:
            self.assertNotIn(f.password,content);self.assertNotIn(f.nonce,content)
        self.assertNotIn('pair_event=',text)
    def test_real_capture_readiness_transients_timeout_permissions_and_cleanup(self):
        # Compile the actual Android fixture readiness method against a controlled AudioRecord.
        source=pathlib.Path(__file__).resolve().parents[2]/'app/src/androidTest/java/com/transiva/app/ReleasePairedCallTest.java'
        text=source.read_text();begin=text.index('    static int awaitAudioReady(');end=text.index('    private long inboundAudioPackets()',begin)
        probe=text[begin:end]
        adapters={
            'android/Manifest.java':'package android;public class Manifest{public static class permission{public static final String RECORD_AUDIO="audio";}}',
            'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager{public static final int PERMISSION_GRANTED=0;}',
            'android/content/Context.java':'package android.content;public class Context{public boolean denied;public int checkSelfPermission(String p){return denied?-1:0;}}',
            'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now;public static long elapsedRealtime(){return now;}public static void sleep(long ms){now+=ms;}}',
            'android/media/AudioFormat.java':'package android.media;public class AudioFormat{public static final int CHANNEL_IN_MONO=16,ENCODING_PCM_16BIT=2;}',
            'android/media/MediaRecorder.java':'package android.media;public class MediaRecorder{public static class AudioSource{public static final int VOICE_COMMUNICATION=7;}}',
            'android/media/AudioRecord.java':r"""package android.media;public class AudioRecord{
                public static final int STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3,READ_NON_BLOCKING=1;
                public static int created,released,started,stopped,mode,minimum=512;private boolean recording;
                public static int getMinBufferSize(int rate,int channel,int format){if(rate!=48000)throw new AssertionError();return minimum;}
                public AudioRecord(int src,int rate,int channel,int format,int size){created++;if(rate!=48000||size<1920)throw new AssertionError();if(mode==1&&created<=2)throw new IllegalArgumentException("AudioFlinger unknown UID");}
                public int getState(){return mode==2?0:STATE_INITIALIZED;}
                public void startRecording(){started++;recording=true;}
                public int getRecordingState(){return recording?RECORDSTATE_RECORDING:1;}
                public int read(byte[] b,int offset,int n,int flags){if(mode==3)return 0;if(mode==4&&created<=2)return -3;return n;}
                public void stop(){stopped++;recording=false;}public void release(){released++;}
                public static void reset(int m){mode=m;created=released=started=stopped=0;minimum=512;android.os.SystemClock.now=0;}
            }""",
        }
        harness=r"""import android.content.Context;import android.os.SystemClock;import android.media.*;
        public class CaptureReadyProbe{METHOD
            static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
            public static void main(String[] args){Context context=new Context();
                AudioRecord.reset(0);check(awaitAudioReady(context)==1,"healthy capture");check(AudioRecord.released==1&&AudioRecord.stopped==1,"success cleanup");
                AudioRecord.reset(1);check(awaitAudioReady(context)==3,"fresh-install unknown UID retries");check(AudioRecord.released==1,"failed constructors must not release null");
                AudioRecord.reset(4);check(awaitAudioReady(context)==3,"negative PCM read retries");check(AudioRecord.released==3&&AudioRecord.stopped==3,"all retry recorders released");
                for(int mode:new int[]{2,3}){AudioRecord.reset(mode);try{awaitAudioReady(context);throw new RuntimeException("persistent failure passed");}catch(AssertionError expected){check(expected.getMessage().contains("30s"),"timeout diagnostics");}check(SystemClock.now>=30000&&SystemClock.now<=30250,"bounded retry");check(AudioRecord.created==AudioRecord.released,"timeout releases all probes");}
                AudioRecord.reset(0);context.denied=true;try{awaitAudioReady(context);throw new RuntimeException("permission bypass");}catch(SecurityException expected){}check(AudioRecord.created==0,"denied permission never opens capture");context.denied=false;
                AudioRecord.reset(0);AudioRecord.minimum=-2;try{awaitAudioReady(context);throw new RuntimeException("unsupported format passed");}catch(AssertionError expected){check(expected.getMessage().contains("unsupported"),"unsupported format diagnosis");}check(AudioRecord.created==0,"invalid format not retried");
                System.out.println("PASS real audio readiness: healthy PCM, transient unknown UID, negative reads, bounded uninitialized/no-PCM failures, denied permission, unsupported format, cleanup");
            }
        }""".replace('METHOD',probe)
        with tempfile.TemporaryDirectory(prefix='audio-readiness-test-') as directory:
            root=pathlib.Path(directory)
            for name,content in adapters.items():
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
            (root/'CaptureReadyProbe.java').write_text(harness)
            compiler=['javac'] if shutil.which('javac') else ['java','--module','jdk.compiler/com.sun.tools.javac.Main']
            subprocess.run(compiler+['-d',str(root/'classes'),*map(str,root.rglob('*.java'))],check=True)
            subprocess.run(['java','-cp',str(root/'classes'),'CaptureReadyProbe'],check=True,timeout=15)

    def test_android_fixture_holds_peer_after_done(self):
        source=pathlib.Path(__file__).resolve().parents[2]/'app/src/androidTest/java/com/transiva/app/ReleasePairedCallTest.java'
        text=source.read_text();done=text.index('event("done",')
        release=text.index('release.await(30,TimeUnit.SECONDS)',done)
        cleanup=text.index('}finally{',done)
        self.assertTrue(done<release<cleanup)
        self.assertIn('"release".equals(type)',text)
        self.assertIn('put("protocol_version",2)',text)
    def test_workflow_keeps_both_device_logs(self):
        path=pathlib.Path(__file__).resolve().parents[2]/'.github/workflows/play-release-smoke.yml'
        text=path.read_text()
        self.assertIn('device-diagnostics/primary-logcat.txt',text)
        self.assertIn('device-diagnostics/peer-logcat.txt',text)
        self.assertIn('device-diagnostics/',text)


if __name__=='__main__':unittest.main(verbosity=2)
