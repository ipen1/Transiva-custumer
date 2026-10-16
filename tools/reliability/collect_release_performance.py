#!/usr/bin/env python3
"""Read-only device metrics plus repeated cold starts on a dedicated test installation. No reset of global battery stats."""
import argparse,subprocess,time,json,pathlib,re,statistics,math

def percentile(values,p):
 return sorted(values)[max(0,math.ceil(len(values)*p)-1)] if values else None

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--serial',required=True);ap.add_argument('--runs',type=int,default=5);ap.add_argument('--observe-seconds',type=int,default=60);ap.add_argument('--output',default='release-performance.json');ap.add_argument('--max-startup-ms',type=int,default=12000);ap.add_argument('--max-pss-kb',type=int,default=393216);a=ap.parse_args();assert 3<=a.runs<=20;assert 10<=a.observe_seconds<=600
 package='com.transiva.customer'
 def adb(*args):return subprocess.run(['adb','-s',a.serial,*args],capture_output=True,text=True,timeout=45,check=True).stdout
 def battery():
  text=adb('shell','dumpsys','battery');return {key:int(m.group(1)) for key,pattern in [('level',r'level: (\d+)'),('charge_counter_uah',r'Charge counter: (\d+)')] if (m:=re.search(pattern,text))}
 launch=adb('shell','cmd','package','resolve-activity','--brief',package).strip().splitlines()[-1];assert '/' in launch
 samples=[]
 for n in range(a.runs):
  adb('shell','am','force-stop',package);text=adb('shell','am','start','-W','-n',launch)
  match=re.search(r'TotalTime:\s*(\d+)',text);assert match,'am start did not report TotalTime';samples.append(int(match.group(1)));time.sleep(2)
 before=battery();began=time.monotonic();time.sleep(a.observe_seconds);after=battery()
 memory=adb('shell','dumpsys','meminfo',package);pss=re.search(r'TOTAL PSS:\s*(\d+)',memory)
 if not pss:pss=re.search(r'^\s*TOTAL\s+(\d+)',memory,re.M)
 assert pss,'Process PSS unavailable'
 exits=adb('shell','dumpsys','activity','exit-info',package);reasons={}
 for number,label in re.findall(r'reason=(\d+)\s*\(([^)]+)\)',exits):reasons[label]=reasons.get(label,0)+1
 report={'device_role':'dedicated_test_device','scope':'dedicated test install, logged out; launcher timing is NOT fully-drawn/login-ready timing','cold_launch_total_ms':samples,'cold_launch_p50_ms':statistics.median(samples),'cold_launch_p95_ms':percentile(samples,.95),'process_pss_kb':int(pss.group(1)),'observation_seconds':round(time.monotonic()-began,2),'device_battery_before':before,'device_battery_after':after,'battery_scope':'device-wide snapshot; emulator/charging/background work prevents attribution to this app','retained_process_exit_reasons':reasons,'exit_scope':'historical retained exits, not a crash-free/ANR rate','budgets':{'startup_p95_ms':a.max_startup_ms,'pss_kb':a.max_pss_kb,'status':'initial CI guardrails; calibrate with real-device baseline'}}
 failures=[]
 if report['cold_launch_p95_ms']>a.max_startup_ms:failures.append('startup')
 if report['process_pss_kb']>a.max_pss_kb:failures.append('memory')
 report['budget_failures']=failures;pathlib.Path(a.output).write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2));assert not failures,'Performance budget exceeded: '+str(failures)
if __name__=='__main__':main()
