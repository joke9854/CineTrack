#!/usr/bin/env python3
"""Validate CineTrack's beta version format and monotonic release progression."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


VERSION_NAME = re.compile(r'versionName\s*=\s*"([^"]+)"')
VERSION_CODE = re.compile(r"versionCode\s*=\s*(\d+)")
SHORT_VERSION = re.compile(r"0\.(\d{2})")
PATCH_VERSION = re.compile(r"0\.99\.(\d{2})")


def read_gradle(path: Path) -> tuple[str, int]:
    contents = path.read_text(encoding="utf-8")
    name_match = VERSION_NAME.search(contents)
    code_match = VERSION_CODE.search(contents)
    if not name_match or not code_match:
        raise ValueError(f"Could not find versionName/versionCode in {path}")
    return name_match.group(1), int(code_match.group(1))


def version_key(name: str) -> tuple[int, int]:
    short = SHORT_VERSION.fullmatch(name)
    if short:
        minor = int(short.group(1))
        if minor <= 99:
            return minor, 0
    patch = PATCH_VERSION.fullmatch(name)
    if patch:
        number = int(patch.group(1))
        if number > 0:
            return 99, number
    raise ValueError(
        f"Unsupported beta version '{name}'. Use 0.xx through 0.99, then 0.99.xx."
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current-file", type=Path, default=Path("app/build.gradle.kts"))
    parser.add_argument("--previous-file", type=Path)
    args = parser.parse_args()

    try:
        current_name, current_code = read_gradle(args.current_file)
        current_key = version_key(current_name)
        if args.previous_file and args.previous_file.exists():
            previous_name, previous_code = read_gradle(args.previous_file)
            previous_key = version_key(previous_name)
            if current_key <= previous_key:
                raise ValueError(
                    f"Version must increase: previous {previous_name}, current {current_name}"
                )
            if current_code <= previous_code:
                raise ValueError(
                    f"versionCode must increase: previous {previous_code}, current {current_code}"
                )
        print(f"PASS: beta version {current_name} (versionCode {current_code}) follows the 0.xx/0.99.xx policy.")
        return 0
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

