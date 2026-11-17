#!/usr/bin/env python3
"""Report deprecated bar setters and cutout writes in the minified Play AAB."""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

CLASS = re.compile(r"^\s*Class descriptor\s*:\s*'L([^']+);'")
MAPPING = re.compile(r"^(\S+) -> (\S+):$")
BAR_SETTER = re.compile(
    r"\|[0-9a-f]+:\s+invoke-\S+.*\bLandroid/view/Window;\."
    r"(setStatusBarColor|setNavigationBarColor|setNavigationBarDividerColor|"
    r"setStatusBarContrastEnforced):\S+\s+// method@"
)
CUTOUT_WRITE = re.compile(
    r"\|[0-9a-f]+:\s+iput\b.*"
    r"Landroid/view/WindowManager\$LayoutParams;\.(layoutInDisplayCutoutMode):I\s+// field@"
)
DEX_PATH = re.compile(r"[^/]+/dex/classes(?:\d+)?\.dex")


def find_dexdump():
    if command := shutil.which("dexdump"):
        return command
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        build_tools = Path(sdk) / "build-tools"
        candidates = [
            file for file in build_tools.glob("*/dexdump")
            if re.fullmatch(r"\d+(?:\.\d+)+", file.parent.name)
        ]
        if candidates:
            return max(candidates, key=lambda file: tuple(map(int, file.parent.name.split("."))))
    raise RuntimeError("Install Android SDK Build Tools with dexdump to inspect the Play bundle.")


def read_classes(mapping):
    names = {}
    with mapping.open(encoding="utf-8") as lines:
        for line in lines:
            match = MAPPING.fullmatch(line.rstrip())
            if match:
                original, obfuscated = match.groups()
                names.setdefault(obfuscated, set()).add(original)
    if not names:
        raise RuntimeError(f"No R8 class mappings found in {mapping}")
    return names


def scan_dump(lines, names):
    callers = set()
    owner = None
    for line in lines:
        match = CLASS.match(line)
        if match:
            owner = match[1].replace("/", ".")
            continue
        call = BAR_SETTER.search(line) or CUTOUT_WRITE.search(line)
        if owner and call:
            callers.update((name, call[1]) for name in names.get(owner, {owner}))
    if owner is None:
        raise RuntimeError("No class descriptors found in dexdump output.")
    return callers


def inspect(bundle, mapping):
    names = read_classes(mapping)
    dexdump = find_dexdump()
    callers = set()
    with zipfile.ZipFile(bundle) as archive, tempfile.TemporaryDirectory() as work:
        dex_entries = [name for name in archive.namelist() if DEX_PATH.fullmatch(name)]
        if not dex_entries:
            raise RuntimeError("No DEX entries found in the Play bundle.")
        dex = Path(work) / "classes.dex"
        for entry in dex_entries:
            with archive.open(entry) as source, dex.open("wb") as target:
                shutil.copyfileobj(source, target)
            with subprocess.Popen(
                [dexdump, "-d", dex], stdout=subprocess.PIPE,
                text=True, encoding="utf-8", errors="replace",
            ) as process:
                callers.update(scan_dump(process.stdout, names))
                if process.wait():
                    raise RuntimeError(f"dexdump failed for {entry}")
    return callers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("mapping", type=Path)
    args = parser.parse_args()
    callers = inspect(args.bundle, args.mapping)
    app_caller_found = False
    for name, api in sorted(callers):
        app_class = name.startswith("com.transiva.app.")
        app_caller_found |= app_class
        print(f"{'APP CLASS' if app_class else 'SDK CLASS'} {name} -> {api}")
    if not callers:
        print("No deprecated bar setters or cutout writes found in the bundle DEX.")
    return int(app_caller_found)


if __name__ == "__main__":
    raise SystemExit(main())
