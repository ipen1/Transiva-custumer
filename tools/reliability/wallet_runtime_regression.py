#!/usr/bin/env python3
"""Compile actual journal/session/monitor source with local Android fixtures, then run.
Set JSON_JAR to an org.json JAR. No real Android device/network is used.
"""
import os
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parent
production = root.parents[1] / 'app/src/main/java/com/transiva/app'
names = ['CustomerWalletOperationJournal', 'CustomerServerConnectionMonitor', 'CustomerSessionGuard', 'CustomerReadSafetyRules', 'NetworkSafetyRules', 'BoundedResponseReader']
json_jar = os.environ['JSON_JAR']
with tempfile.TemporaryDirectory(prefix='transiva-wallet-runtime-') as directory:
    subprocess.run([os.environ.get('JAVAC_BIN', 'javac'), '-cp', json_jar, '-d', directory,
                    *map(str, (root / 'wallet-fixtures').rglob('*.java')),
                    *[str(production / (name + '.java')) for name in names]], check=True)
    subprocess.run([os.environ.get('JAVA_BIN', 'java'), '-cp', directory + os.pathsep + json_jar, 'com.transiva.app.WalletRuntimeCheck'], check=True)
