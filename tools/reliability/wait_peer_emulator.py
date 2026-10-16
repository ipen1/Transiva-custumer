#!/usr/bin/env python3
import subprocess,sys,time
serial=sys.argv[1];deadline=time.monotonic()+180
while time.monotonic()<deadline:
 p=subprocess.run(['adb','-s',serial,'shell','getprop','sys.boot_completed'],text=True,capture_output=True,timeout=10)
 if p.returncode==0 and p.stdout.strip()=='1':
  subprocess.run(['adb','-s',serial,'shell','input','keyevent','224'],check=True)
  subprocess.run(['adb','-s',serial,'shell','wm','dismiss-keyguard'],check=True)
  break
 time.sleep(2)
else:raise SystemExit('Second API 36 emulator did not boot')
