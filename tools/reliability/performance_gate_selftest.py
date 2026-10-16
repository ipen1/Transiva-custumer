#!/usr/bin/env python3
"""Controlled ADB tests for performance collection and workflow setup; no device metrics."""
import contextlib
import importlib.util
import io
import json
import pathlib
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('performance_gate', pathlib.Path(__file__).with_name('collect_release_performance.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class FakeDevice:
    def __init__(self):
        self.path = 'package:/data/app/fixture/base.apk\n'
        self.launcher = 'com.transiva.customer/com.transiva.app.SplashActivity\n'
        self.times = [100, 200, 300, 400, 500]
        self.status = 'ok'
        self.no_time = False
        self.memory = 'TOTAL PSS: 32000\n'
        self.battery = 'level: 90\nCharge counter: 2500000\n'
        self.exits = 'reason=4 (CRASH)\nreason=6 (ANR)\nreason=4 (CRASH)\n'
        self.calls = []
        self.now = 0
        self.failure = None

    def sleep(self, seconds):
        self.now += seconds

    def run(self, command, **kwargs):
        self.calls.append(command)
        if self.failure == 'timeout':
            raise subprocess.TimeoutExpired(command, 45)
        if self.failure == 'offline':
            return types.SimpleNamespace(returncode=1, stdout='', stderr='device offline')
        cmd = command[3:]
        output = ''
        if cmd[:3] == ['shell', 'pm', 'path']:
            output = self.path
        elif 'resolve-activity' in cmd:
            output = self.launcher
        elif cmd[:3] == ['shell', 'am', 'start']:
            number = sum(c[3:6] == ['shell', 'am', 'start'] for c in self.calls) - 1
            output = 'Status: ' + self.status + '\n'
            if not self.no_time:
                output += 'TotalTime: ' + str(self.times[number]) + '\n'
        elif cmd[:3] == ['shell', 'dumpsys', 'battery']:
            output = self.battery
        elif 'meminfo' in cmd:
            output = self.memory
        elif 'exit-info' in cmd:
            output = self.exits
        return types.SimpleNamespace(returncode=0, stdout=output, stderr='')


class PerformanceGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='performance-gate-test-')
        self.addCleanup(self.temp.cleanup)
        self.device = FakeDevice()
        self.args = types.SimpleNamespace(serial='fixture-device', runs=5, observe_seconds=60,
                                         output=str(pathlib.Path(self.temp.name)/'metrics.json'),
                                         max_startup_ms=12000, max_pss_kb=393216)

    def collect(self):
        report = gate.collect(self.args, runner=self.device.run, sleep=self.device.sleep, clock=lambda:self.device.now)
        self.assertEqual(report, json.loads(pathlib.Path(self.args.output).read_text()))
        return report

    def failed(self, stage):
        result = self.collect()
        self.assertEqual('failed', result['result'])
        self.assertEqual(stage, result['failed_stage'])
        self.assertIn('error', result)
        return result

    def test_successful_collection_and_explicit_launcher_intent(self):
        result = self.collect()
        self.assertEqual('passed', result['result'])
        self.assertEqual(300, result['cold_launch_p50_ms'])
        self.assertEqual(500, result['cold_launch_p95_ms'])
        resolve = next(c for c in self.device.calls if 'resolve-activity' in c)
        self.assertEqual(['-a', gate.MAIN, '-c', gate.LAUNCHER, '-p', gate.PACKAGE], resolve[-6:])
        self.assertEqual(5, sum(c[3:6] == ['shell', 'am', 'force-stop'] for c in self.device.calls))

    def test_package_missing_is_actionable_and_reported(self):
        self.device.path = ''
        report = self.failed('package_installation')
        self.assertIn('Reinstall', report['error'])
        self.assertEqual([], report['cold_launch_total_ms'])

    def test_no_launcher_writes_diagnostics(self):
        self.device.launcher = 'No activity found\n'
        result = self.failed('resolve_launcher')
        self.assertEqual(self.device.launcher, result['launcher_resolution'])

    def test_empty_launcher_does_not_throw_index_error(self):
        self.device.launcher = ''
        self.assertEqual('GateError', self.failed('resolve_launcher')['error_type'])

    def test_launcher_can_have_diagnostic_lines(self):
        self.device.launcher = 'Notice\n' + self.device.launcher + 'End of command\n'
        self.assertEqual('passed', self.collect()['result'])

    def test_launcher_short_class(self):
        self.device.launcher = gate.PACKAGE + '/.SplashActivity\n'
        self.assertEqual('passed', self.collect()['result'])

    def test_reject_wrong_package_component(self):
        self.device.launcher = 'com.other/.SplashActivity\n'
        self.failed('resolve_launcher')

    def test_reject_ambiguous_launcher(self):
        self.device.launcher += gate.PACKAGE + '/.OtherActivity\n'
        self.failed('resolve_launcher')

    def test_reject_shell_text_in_component(self):
        self.device.launcher = gate.PACKAGE + '/.SplashActivity;invalid\n'
        self.failed('resolve_launcher')

    def test_adb_failure_persists_stderr(self):
        self.device.failure = 'offline'
        result = self.failed('package_installation')
        self.assertEqual('device offline', result['last_adb_error']['stderr'])

    def test_adb_timeout_has_failure_report(self):
        self.device.failure = 'timeout'
        result = self.failed('package_installation')
        self.assertEqual('TimeoutExpired', result['error_type'])
        self.assertNotIn('fixture-device', result['error'])

    def test_am_start_failure_even_with_total_time(self):
        self.device.status = 'error'
        self.failed('cold_launch')

    def test_total_time_must_exist(self):
        self.device.no_time = True
        self.failed('cold_launch')

    def test_missing_process_memory_fails(self):
        self.device.memory = 'No process found\n'
        result = self.failed('process_memory')
        self.assertNotIn('process_pss_kb', result)
        self.assertIn('No process', result['memory_output'])

    def test_legacy_total_memory_format(self):
        self.device.memory = '   TOTAL 34000 100 200\n'
        self.assertEqual(34000, self.collect()['process_pss_kb'])

    def test_startup_budget_is_not_bypassed(self):
        self.device.times[-1] = 12001
        self.assertEqual(['startup'], self.failed('performance_budgets')['budget_failures'])

    def test_memory_budget_is_not_bypassed(self):
        self.device.memory = 'TOTAL PSS: 393217\n'
        self.assertEqual(['memory'], self.failed('performance_budgets')['budget_failures'])

    def test_budget_boundaries_still_pass(self):
        self.device.times[-1] = 12000
        self.device.memory = 'TOTAL PSS: 393216\n'
        self.assertEqual('passed', self.collect()['result'])

    def test_observation_uses_full_interval(self):
        self.assertEqual(60, self.collect()['observation_seconds'])

    def test_battery_and_historical_exit_scope_preserved(self):
        result = self.collect()
        self.assertEqual({'CRASH': 2, 'ANR': 1}, result['retained_process_exit_reasons'])
        self.assertIn('device-wide', result['battery_scope'])
        self.assertIn('not a crash-free', result['exit_scope'])

    def test_optional_battery_fields_not_fabricated(self):
        self.device.battery = ''
        result = self.collect()
        self.assertEqual({}, result['device_battery_before'])
        self.assertEqual({}, result['device_battery_after'])

    def test_cli_failure_returns_nonzero(self):
        self.device.path = ''
        with patch.object(gate.subprocess, 'run', self.device.run), contextlib.redirect_stdout(io.StringIO()):
            code = gate.main(['--serial', self.args.serial, '--output', self.args.output])
        self.assertEqual(1, code)

    def test_cli_success_returns_zero(self):
        with patch.object(gate.subprocess, 'run', self.device.run), patch.object(gate.time, 'sleep', self.device.sleep), patch.object(gate.time, 'monotonic', lambda:self.device.now), contextlib.redirect_stdout(io.StringIO()):
            code = gate.main(['--serial', self.args.serial, '--output', self.args.output])
        self.assertEqual(0, code)

    def test_workflow_reinstalls_primary_and_accepts_test_apks(self):
        workflow = pathlib.Path(__file__).resolve().parents[2]/'.github/workflows/play-release-smoke.yml'
        text = workflow.read_text().split('script: |', 1)[1]
        connected = text.index('gradle :app:connectedPlayReleaseAndroidTest')
        app_install = text.index('adb -s emulator-5554 install -r app/')
        test_install = text.index('adb -s emulator-5554 install -r -t app/')
        performance = text.index('python3 tools/reliability/collect_release_performance.py')
        paired = text.index('python3 tools/reliability/paired_release_gate.py')
        self.assertTrue(connected < app_install < test_install < performance < paired)
        self.assertIn('adb -s emulator-5556 install -r -t app/', text)


if __name__ == '__main__':
    unittest.main(verbosity=2)
