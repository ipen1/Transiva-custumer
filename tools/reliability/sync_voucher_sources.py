"""Compare or explicitly refresh the credential-free CI snapshot from an extracted server folder."""
from pathlib import Path
import argparse,json,hashlib,shutil
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--source',required=True,type=Path,help='Extracted production server folder, never hosting credentials')
p.add_argument('--update',action='store_true',help='Copy server files and update both fingerprint manifests')
a=p.parse_args()
manifest=json.loads((ROOT/'voucher-sources.json').read_text());different=[]
for name in manifest:
    if Path(name).name!=name or not name.endswith('.php'):raise SystemExit('Invalid snapshot filename')
    src=a.source/name
    if not src.is_file():raise SystemExit('Missing source file: '+name)
    digest=hashlib.sha256(src.read_bytes()).hexdigest()
    if digest!=manifest[name]:different.append(name)
# Validate the full input before any copying.
if a.update:
    for name in manifest:
        shutil.copyfile(a.source/name,ROOT/'voucher-server'/name)
        manifest[name]=hashlib.sha256((a.source/name).read_bytes()).hexdigest()
    old=json.loads((ROOT/'backend-sources.json').read_text())
    for name in ['transiva_pay_helper.php','order_reliability_helper.php']:
        shutil.copyfile(a.source/name,ROOT/'server'/name);old[name]=manifest[name]
    (ROOT/'voucher-sources.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (ROOT/'backend-sources.json').write_text(json.dumps(old,indent=2)+'\n')
    print('Updated voucher sources. Run the full CI before deploying.')
elif different:
    raise SystemExit('Sources differ: '+', '.join(different))
else:print('PASS deployed source matches all '+str(len(manifest))+' voucher snapshots')
