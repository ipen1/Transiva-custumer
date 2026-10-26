#!/usr/bin/env python3
"""Compile production classes with controlled Android/transport fixtures. No live server calls."""
from pathlib import Path
import subprocess,tempfile,json,os,shutil
root=Path(__file__).resolve().parent;src=root.parents[1]/'app/src/main/java/com/transiva/app'
for suite,classes,main in [('http',['TransivaHttpRepository','CustomerApiClient','NetworkSafetyRules','CustomerTransportSession'],'HttpSessionProbe'),('poller',['CustomerChatNotificationPoller','CustomerChatNoticeState','CustomerChatPushGuard','CustomerChatPreviewFormatter','NetworkSafetyRules','CustomerTransportSession'],'PollerProbe')]:
 with tempfile.TemporaryDirectory(prefix='transiva-session-') as d:
  p=Path(d)
  for name,text in json.loads((root/'session-fixtures'/f'{suite}.json').read_text()).items():
   f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
  files=list(p.rglob('*.java'))+[src/(name+'.java') for name in classes]
  compiler=[os.environ['JAVAC_BIN']] if os.environ.get('JAVAC_BIN') else (['javac'] if shutil.which('javac') else ['java','--module','jdk.compiler/com.sun.tools.javac.Main'])
  subprocess.run(compiler+['-cp',os.environ['JSON_JAR'],'-d',str(p/'classes'),*map(str,files)],check=True)
  subprocess.run(['java','-cp',str(p/'classes')+os.pathsep+os.environ['JSON_JAR'],'com.transiva.app.'+main],check=True)
