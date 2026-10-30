"""Ensure CI is testing the exact voucher source snapshot supplied for deployment."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parent

def verify(root=ROOT):
    manifest=json.loads((root/'voucher-sources.json').read_text())
    if not manifest:raise RuntimeError('Voucher source manifest is empty')
    for name,expected in manifest.items():
        if Path(name).name!=name or not name.endswith('.php'):
            raise RuntimeError('Invalid snapshot filename')
        file=root/'voucher-server'/name
        if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest()!=expected:
            raise RuntimeError('Voucher snapshot changed without synchronization: '+name)
    for name in ['transiva_pay_helper.php','order_reliability_helper.php']:
        if (root/'server'/name).read_bytes()!=(root/'voucher-server'/name).read_bytes():
            raise RuntimeError('Financial test copies disagree: '+name)
    return len(manifest)

if __name__=='__main__':print('PASS voucher snapshot fingerprints:',verify())
