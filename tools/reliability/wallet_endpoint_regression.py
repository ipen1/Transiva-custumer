#!/usr/bin/env python3
"""Compatibility entry point: v2.5.32 final-state scenarios supersede v2.5.31 assertions."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('wallet_final_regression.py')), run_name='__main__')
