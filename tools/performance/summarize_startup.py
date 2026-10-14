#!/usr/bin/env python3
"""Summarize one physical device's am start -W capture. These timings end at Splash."""
import csv,sys,statistics,math
from pathlib import Path
path=Path(sys.argv[1]);rows=list(csv.DictReader(path.open()));values=[int(r['total_time_ms']) for r in rows if r.get('total_time_ms','').isdigit()]
if not values:raise SystemExit('No valid startup samples')
values.sort();print('Samples:',len(values));print('Startup to Splash median (ms):',statistics.median(values));print('Startup to Splash p95 (ms):',values[max(0,math.ceil(.95*len(values))-1)]);print('Dashboard/service readiness must be measured separately.')
