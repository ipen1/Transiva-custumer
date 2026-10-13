from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parent
for name,expected in json.loads((root/'backend-sources.json').read_text()).items():
    assert hashlib.sha256((root/'server'/name).read_bytes()).hexdigest()==expected, 'Backend source changed: update deployed source, test copy, and fingerprint together: '+name
for name in ['driver_update_unified_status.php','complete_pickup_with_otp.php']:
    text=(root/'server'/name).read_text()
    assert 'pickup_finish_payment_locked($conn,' in text, 'Finish endpoint bypasses common settlement: '+name
    assert 'sw_settle_pickup($conn,$order)' not in text, 'Waiting-only payout reintroduced'
print('PASS backend source fingerprints and both finish routes')

for name,expected in json.loads((root/'reliability-sources.json').read_text()).items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==expected, 'Chat/wallet fixture source changed: '+name
print('PASS chat and wallet source fingerprints')
