"""Voucher CI gate. All writes target only the dedicated disposable MariaDB database."""
from pathlib import Path
import os,sys,subprocess,json,re,argparse,tempfile,shutil
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'vouchers'))
from common import php_command
from verify_voucher_sources import verify

def self_test():
    with tempfile.TemporaryDirectory(prefix='voucher-source-check-') as name:
        root=Path(name)
        shutil.copyfile(ROOT/'voucher-sources.json',root/'voucher-sources.json')
        shutil.copytree(ROOT/'voucher-server',root/'voucher-server')
        (root/'server').mkdir()
        for n in ['transiva_pay_helper.php','order_reliability_helper.php']:shutil.copyfile(ROOT/'server'/n,root/'server'/n)
        verify(root)
        engine=root/'voucher-server'/'ride_voucher_helper.php';engine.write_bytes(engine.read_bytes()+b'\n// unsynchronized test mutation\n')
        try:verify(root)
        except RuntimeError:pass
        else:raise RuntimeError('Fingerprint verification accepted a changed engine')
    env=os.environ.copy();env['TEST_DB_NAME']='production_database_forbidden'
    probe='require '+json.dumps(str(ROOT/'vouchers/db.php'))+'; voucher_ci_db();'
    result=subprocess.run(php_command()+['-r',probe],env=env,capture_output=True,text=True,timeout=15)
    if result.returncode==0 or 'Only transiva_voucher_ci_test is allowed' not in result.stdout+result.stderr:
        raise RuntimeError('Database isolation guard did not reject a production database name')
    print('PASS fingerprint mutation detection and production database isolation')

def main():
    p=argparse.ArgumentParser();p.add_argument('--self-test',action='store_true');a=p.parse_args()
    print('PASS voucher source hashes:',verify(),flush=True)
    self_test()
    if a.self_test:return
    if os.environ.get('TEST_DB_NAME','transiva_voucher_ci_test')!='transiva_voucher_ci_test':
        raise RuntimeError('Only the disposable voucher test database is allowed')
    commands=[(php_command()+[str(ROOT/'vouchers/engine_test.php')],39,'engine'),
              ([sys.executable,str(ROOT/'vouchers/http_test.py')],14,'http')]
    counts={}
    for cmd,minimum,key in commands:
        r=subprocess.run(cmd,capture_output=True,text=True,timeout=180)
        print(r.stdout,end='',flush=True)
        if r.stderr:print(r.stderr,file=sys.stderr,end='')
        if r.returncode:raise RuntimeError(key+' regression failed: '+str(r.returncode))
        match=re.search(r'RESULT: (\d+) (?:checks|HTTP endpoint checks) passed',r.stdout)
        if not match or int(match[1])<minimum:raise RuntimeError(key+' test count missing or unexpectedly low')
        counts[key]=int(match[1])
    report={'success':True,'checks':counts,'source_files':verify(),
            'database':'transiva_voucher_ci_test','http_services':'fixture auth, routing, dispatch and points; production voucher/payment code'}
    Path('voucher-ci-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PASS voucher CI gate:',sum(counts.values()),'database/HTTP checks',flush=True)

if __name__=='__main__':main()
