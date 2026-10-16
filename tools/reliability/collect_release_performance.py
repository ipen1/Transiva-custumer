#!/usr/bin/env python3
"""Device metrics on a disposable release installation; never resets battery stats.

The workflow reinstalls both APKs after connectedAndroidTest, whose cleanup can
remove them. Launcher resolution uses an explicit MAIN/LAUNCHER intent. Every
measurement failure writes a diagnostic report and still fails the CI gate.
"""
import argparse
import json
import math
import pathlib
import re
import statistics
import subprocess
import time

PACKAGE = 'com.transiva.customer'
MAIN = 'android.intent.action.MAIN'
LAUNCHER = 'android.intent.category.LAUNCHER'


class GateError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise GateError(message)


def percentile(values, p):
    return sorted(values)[max(0, math.ceil(len(values)*p)-1)] if values else None


def launcher_component(text):
    # Namespace com.transiva.app differs from applicationId com.transiva.customer.
    # Accept fully qualified classes or .Class syntax, only in our own package.
    pattern = re.compile(re.escape(PACKAGE) + r'/\.?[A-Za-z_$][A-Za-z0-9_.$]*')
    components = {line.strip() for line in text.splitlines() if pattern.fullmatch(line.strip())}
    require(len(components) == 1,
            'Launcher not resolved for the installed release APK. Expected one MAIN/LAUNCHER component; see launcher_resolution in the report.')
    return next(iter(components))


def collect(args, runner=None, sleep=None, clock=None):
    runner = runner or subprocess.run
    sleep = sleep or time.sleep
    clock = clock or time.monotonic
    stage = 'package_installation'
    report = {
        'result': 'failed',
        'device_role': 'dedicated_test_device',
        'scope': 'dedicated test install, logged out; launcher timing is NOT fully-drawn/login-ready timing',
        'cold_launch_total_ms': [],
        'budget_failures': [],
        'budgets': {'startup_p95_ms': args.max_startup_ms, 'pss_kb': args.max_pss_kb,
                    'status': 'initial CI guardrails; calibrate with real-device baseline'},
        'battery_scope': 'device-wide snapshot; emulator/charging/background work prevents attribution to this app',
        'exit_scope': 'historical retained exits, not a crash-free/ANR rate',
    }

    def adb(*command):
        result = runner(['adb', '-s', args.serial, *command], capture_output=True, text=True, timeout=45)
        if result.returncode:
            report['last_adb_error'] = {'operation': list(command), 'returncode': result.returncode,
                                      'stdout': (result.stdout or '')[-1600:], 'stderr': (result.stderr or '')[-1600:]}
            raise GateError('ADB command failed during ' + stage + '; see last_adb_error in the report.')
        return result.stdout or ''

    def battery():
        text = adb('shell', 'dumpsys', 'battery')
        return {key: int(match.group(1)) for key, pattern in
                [('level', r'level:\s*(\d+)'), ('charge_counter_uah', r'Charge counter:\s*(\d+)')]
                if (match := re.search(pattern, text))}

    try:
        paths = adb('shell', 'pm', 'path', PACKAGE)
        require(any(line.strip().startswith('package:') for line in paths.splitlines()),
                'Release APK is not installed. Reinstall app and test APKs after connectedPlayReleaseAndroidTest before performance/paired tests.')
        stage = 'resolve_launcher'
        resolved = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '--user', '0',
                       '-a', MAIN, '-c', LAUNCHER, '-p', PACKAGE)
        report['launcher_resolution'] = resolved[-1600:]
        launch = launcher_component(resolved)
        report['launcher_component'] = launch
        stage = 'wake_device'
        adb('shell', 'input', 'keyevent', '224')
        adb('shell', 'wm', 'dismiss-keyguard')
        stage = 'cold_launch'
        samples = report['cold_launch_total_ms']
        for n in range(args.runs):
            report['launch_attempt'] = n + 1
            adb('shell', 'am', 'force-stop', PACKAGE)
            text = adb('shell', 'am', 'start', '-W', '-n', launch)
            report['last_launch_output'] = text[-1600:]
            require(re.search(r'^\s*Status:\s*ok\s*$', text, re.M) is not None,
                    'am start did not report Status: ok; see last_launch_output.')
            match = re.search(r'TotalTime:\s*(\d+)', text)
            require(match is not None, 'am start did not report TotalTime; see last_launch_output.')
            samples.append(int(match.group(1)))
            sleep(2)
        report['cold_launch_p50_ms'] = statistics.median(samples)
        report['cold_launch_p95_ms'] = percentile(samples, .95)
        stage = 'observation'
        report['device_battery_before'] = battery()
        began = clock()
        sleep(args.observe_seconds)
        report['device_battery_after'] = battery()
        report['observation_seconds'] = round(clock()-began, 2)
        stage = 'process_memory'
        memory = adb('shell', 'dumpsys', 'meminfo', PACKAGE)
        pss = re.search(r'TOTAL PSS:\s*(\d+)', memory)
        if not pss:
            pss = re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
        if not pss:
            report['memory_output'] = memory[-1600:]
        require(pss is not None, 'Process PSS unavailable; the app may have exited. See memory_output.')
        report['process_pss_kb'] = int(pss.group(1))
        stage = 'exit_history'
        exits = adb('shell', 'dumpsys', 'activity', 'exit-info', PACKAGE)
        reasons = {}
        for number, label in re.findall(r'reason=(\d+)\s*\(([^)]+)\)', exits):
            reasons[label] = reasons.get(label, 0) + 1
        report['retained_process_exit_reasons'] = reasons
        stage = 'performance_budgets'
        if report['cold_launch_p95_ms'] > args.max_startup_ms:
            report['budget_failures'].append('startup')
        if report['process_pss_kb'] > args.max_pss_kb:
            report['budget_failures'].append('memory')
        require(not report['budget_failures'], 'Performance budget exceeded: ' + ', '.join(report['budget_failures']))
        report['result'] = 'passed'
    except Exception as error:
        report.update(failed_stage=stage, error_type=type(error).__name__,
                      error=str(error).replace(args.serial, '<test-device>'))
    finally:
        output = pathlib.Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + '\n')
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--runs', type=int, default=5)
    parser.add_argument('--observe-seconds', type=int, default=60)
    parser.add_argument('--output', default='release-performance.json')
    parser.add_argument('--max-startup-ms', type=int, default=12000)
    parser.add_argument('--max-pss-kb', type=int, default=393216)
    args = parser.parse_args(argv)
    if not 3 <= args.runs <= 20 or not 10 <= args.observe_seconds <= 600:
        parser.error('runs must be 3..20 and observe-seconds must be 10..600')
    if args.max_startup_ms <= 0 or args.max_pss_kb <= 0:
        parser.error('performance budgets must be positive')
    report = collect(args)
    print(json.dumps(report, indent=2))
    return 0 if report['result'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
