#!/usr/bin/env python3
"""Run unchanged wallet endpoints with a deterministic transactional DB/FCM fixture.
Not a MySQL lock/concurrency test. No production configuration is loaded.
Usage: PHP_BIN=/path/php python3 wallet_endpoint_regression.py /path/server
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

source = Path(sys.argv[1]).resolve()
php = os.environ.get("PHP_BIN", "php")
checks = 0
def verify(ok, title):
    global checks
    if not ok:
        raise AssertionError(title)
    checks += 1
    print("PASS", title)

with tempfile.TemporaryDirectory(prefix="transiva-wallet-fixture-") as directory:
    root = Path(directory)
    for name in ("customer_wallet_withdraw.php", "customer_wallet_transfer.php", "customer_wallet_operation_helper.php"):
        shutil.copy2(source / name, root / name)
    shutil.copy2(Path(__file__).with_name("wallet_endpoint_fixture.php"), root / "fixture.php")
    (root / "config.php").write_text("<?php require_once __DIR__.'/fixture.php';")
    for name in ("wallet_helper.php", "p0_security_helper.php", "wallet_transfer_security.php", "fcm_helper.php"):
        (root / name).write_text("<?php // Functions supplied by the isolated fixture.\n")
    state_path, payload_path = root / "state.json", root / "payload.json"
    def seed():
        state_path.write_text(json.dumps({"users": {"1": {"username": "sender", "balance": 100000}, "2": {"username": "receiver", "balance": 20000}}, "receipts": {}, "withdrawals": [], "requests": {}, "ledger": [], "audit": [], "fees": []}))
    def call(action, payload, failure="", engine="", owner=1):
        payload_path.write_text(json.dumps(payload))
        env = dict(os.environ, FIXTURE_STATE=str(state_path), FIXTURE_PAYLOAD=str(payload_path), FIXTURE_FAIL=failure, FIXTURE_ENGINE=engine, FIXTURE_OWNER=str(owner))
        result = subprocess.run([php, "-n", str(root / ("customer_wallet_" + action + ".php"))], cwd=root, env=env, capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stderr)
        return json.loads(result.stdout), json.loads(state_path.read_text())
    withdraw = dict(amount=10000, bank_name="Bank", account_name="Sender", account_number="1234", note="test", idempotency_key="cwd-fixture-1")
    transfer = dict(recipient="receiver", amount=10000, note="test", request_id="transfer-fixture-1", quote_token="valid")
    seed()
    response, state = call("withdraw", withdraw)
    verify(response["success"] and state["users"]["1"]["balance"] == 90000 and len(state["withdrawals"]) == 1, "withdraw commits hold + receipt")
    response, replayed = call("withdraw", withdraw)
    verify(response["success"] and response["replayed"] and state == replayed, "withdraw retry never creates second hold")
    response, unchanged = call("withdraw", dict(withdraw, amount=15000))
    verify(not response["success"] and response["operation_state"] == "unknown" and unchanged == state, "same key different payload rejected without replacing receipt")
    response, separate = call("withdraw", withdraw, owner=2)
    verify(response["success"] and separate["users"]["2"]["balance"] == 10000 and len(separate["receipts"]) == 2, "receipt is scoped to authenticated account")
    for failure in ("receipt", "ledger"):
        seed()
        response, state = call("withdraw", withdraw, failure)
        verify(not response["success"] and response["operation_state"] == "rejected" and state["users"]["1"]["balance"] == 100000 and not state["withdrawals"] and not state["receipts"], "withdraw " + failure + " failure rolls back money and receipt")
    seed()
    response, state = call("withdraw", withdraw, engine="MyISAM")
    verify(not response["success"] and state["users"]["1"]["balance"] == 100000, "nontransactional storage refuses debit")
    seed()
    response, state = call("withdraw", withdraw, "commit_unknown")
    verify(not response["success"] and response["operation_state"] == "unknown" and state["users"]["1"]["balance"] == 90000, "ambiguous commit never declares definitive rejection")
    response, state = call("withdraw", withdraw)
    verify(response["success"] and response["replayed"] and len(state["withdrawals"]) == 1, "ambiguous commit recovered by same intent")
    for failure in ("notification_lookup", "notification_queue", ""):
        seed()
        response, state = call("transfer", transfer, failure)
        verify(response["success"] and response["committed"] and state["users"]["1"]["balance"] == 90000 and state["users"]["2"]["balance"] == 30000 and state["audit"] == ["success"] and (failure or len(state["notifications"]) == 2), "postcommit " + (failure or "outbox enqueue") + " retains success receipt")
        response, repeated = call("transfer", dict(transfer, quote_token="expired"))
        verify(response["success"] and response["replayed"] and state == repeated, "transfer replay ignores expired quote after commit")
    for failure in ("receipt", "transfer_receipt", "ledger"):
        seed()
        response, state = call("transfer", transfer, failure)
        verify(not response["success"] and response["operation_state"] == "rejected" and state["users"]["1"]["balance"] == 100000 and state["users"]["2"]["balance"] == 20000 and not state["receipts"] and not state["requests"], "transfer " + failure + " failure rolls back both balances")
    seed()
    response, state = call("transfer", transfer)
    state["receipts"] = {}  # Prior version stored only wallet_transfer_requests.
    state_path.write_text(json.dumps(state))
    response, state = call("transfer", dict(transfer, quote_token="expired"))
    verify(response["success"] and response["replayed"] and state["users"]["1"]["balance"] == 90000, "legacy transfer receipt imported without second debit")
    response, state = call("transfer", dict(transfer, amount=11000))
    verify(not response["success"] and state["users"]["1"]["balance"] == 90000, "changed transfer amount cannot reuse receipt")
    seed()
    response, state = call("transfer", dict(transfer, quote_token="expired"))
    verify(not response["success"] and response["operation_state"] == "rejected" and not state["receipts"] and state["users"]["1"]["balance"] == 100000, "expired unexecuted quote is safely rejected")
    response, state = call("withdraw", dict(withdraw, operation_owner_id="2"))
    verify(not response["success"] and not state["receipts"] and state["users"]["1"]["balance"] == 100000, "withdraw cannot retarget another account intent")
    response, state = call("transfer", dict(transfer, operation_owner_id="2"))
    verify(not response["success"] and not state["receipts"] and state["users"]["1"]["balance"] == 100000, "transfer cannot retarget another account intent")
print("PASS", checks, "wallet endpoint fixture scenarios")
