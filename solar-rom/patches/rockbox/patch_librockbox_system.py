#!/usr/bin/env python3
"""Patch librockbox.so: route system("am start …") via /system/xbin/solar-rb-launch.

Rockbox Y1 menu items call libc system() with bare `am start` commands. On Y2 (no
sharedUserId) org.rockbox cannot start Settings/FM/BT intents without root. The Java
Connectivity.execShell su patch does not cover these native system() calls.

Replacements must be EXACTLY the same byte length as the originals (rodata packing).
"""
from __future__ import annotations

import sys
from pathlib import Path

# (old, new) — len(old) == len(new) required
REPLACEMENTS: list[tuple[str, str]] = [
    (
        "am start -a android.settings.SETTINGS",
        "/system/xbin/solar-rb-launch settings",
    ),
    (
        "am start -a android.settings.BLUETOOTH_SETTINGS",
        "/system/xbin/solar-rb-launch bluetooth-settings",
    ),
    (
        "am start -n com.mediatek.FMRadio/.FMRadioActivity",
        "/system/xbin/solar-rb-launch fm-radio-activity-xx",
    ),
    (
        "monkey -p org.rockbox -c android.intent.category.LAUNCHER 1",
        "/system/xbin/solar-rb-launch rockbox-restart-monkey-launch0",
    ),
]


def patch_librockbox(lib_path: Path) -> None:
    data = bytearray(lib_path.read_bytes())
    patched = 0
    for old, new in REPLACEMENTS:
        if len(old) != len(new):
            raise SystemExit(f"length mismatch {len(old)} vs {len(new)}: {old!r} -> {new!r}")
        old_b = old.encode("ascii")
        new_b = new.encode("ascii")
        count = data.count(old_b)
        if count != 1:
            raise SystemExit(f"expected exactly one {old!r} in {lib_path}, found {count}")
        data = data.replace(old_b, new_b, 1)
        patched += 1
        print(f"==> librockbox: {old!r} -> {new!r}")

    # Sanity: no bare am-start menu strings left.
    for old, _ in REPLACEMENTS:
        if old.encode("ascii") in data:
            raise SystemExit(f"librockbox still contains {old!r} after patch")

    lib_path.write_bytes(data)
    print(f"==> Patched {patched} system() command strings in {lib_path}")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} LIBROCKBOX_SO")
    patch_librockbox(Path(sys.argv[1]))


if __name__ == "__main__":
    main()
