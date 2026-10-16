#!/usr/bin/env python3
"""Bounded read-only load sampling for an explicitly supplied STAGING endpoint. Never targets production."""
import argparse,urllib.request,urllib.parse,concurrent.futures,time,json,os,math,pathlib

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--url',required=True);ap.add_argument('--ack-read-only',action='store_true');ap.add_argument('--requests',type=int,default=30);ap.add_argument('--concurrency',type=int,default=2);ap.add_argument('--output',default='staging-load.json');a=ap.parse_args();url=urllib.parse.urlparse(a.url)
 assert a.ack_read_only and url.scheme=='https' and url.hostname and not url.username and not url.query,'Explicit HTTPS staging health/read-only URL required (no credentials/query)'
 assert url.hostname.rstrip('.').lower() not in ('transiva.my.id','www.transiva.my.id'),'Production is deliberately excluded'
 assert 1<=a.requests<=200 and 1<=a.concurrency<=4
 def once(_):
  started=time.monotonic();code=0;size=0
  try:
   headers={'Accept':'application/json','User-Agent':'Transiva-Staging-Metrics'}
   token=os.environ.get('STAGING_READ_TOKEN','')
   if token:headers['Authorization']='Bearer '+token
   class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
   with urllib.request.build_opener(NoRedirect).open(urllib.request.Request(a.url,headers=headers,method='GET'),timeout=10) as response:code=response.status;size=len(response.read(1024*1024+1));assert size<=1024*1024
  except Exception:pass
  return {'ms':round((time.monotonic()-started)*1000,2),'ok':200<=code<300,'bytes':size}
 began=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as pool:rows=list(pool.map(once,range(a.requests)))
 elapsed=time.monotonic()-began;times=sorted(r['ms'] for r in rows);failures=sum(not r['ok'] for r in rows)
 report={'scope':'staging read-only sample, not production capacity or a concurrent-user limit','requests':a.requests,'concurrency':a.concurrency,'elapsed_s':round(elapsed,2),'requests_per_second':round(a.requests/elapsed,2),'p50_ms':times[math.ceil(len(times)*.5)-1],'p95_ms':times[math.ceil(len(times)*.95)-1],'failed_requests':failures,'error_rate':failures/a.requests}
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
if __name__=='__main__':main()
