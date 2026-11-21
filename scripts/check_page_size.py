#!/usr/bin/env python3
"""Check 64-bit native libraries in APKs for Android 15 16 KB page alignment.

Usage: python3 scripts/check_page_size.py <apk> [<apk> ...]

Only lib/arm64-v8a/*.so is checked. ELF PT_LOAD segments must have p_align
>= 0x4000 and a multiple of 0x4000.
"""

import struct
import sys
import zipfile


PT_LOAD = 1
PAGE_16K = 0x4000


def load_aligns(data: bytes):
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2:  # ELFCLASS64
        return None
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize = struct.unpack_from("<H", data, 54)[0]
    e_phnum = struct.unpack_from("<H", data, 56)[0]
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        if off + 56 > len(data):
            raise ValueError("truncated program header")
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        p_align = struct.unpack_from("<Q", data, off + 48)[0]
        aligns.append(p_align)
    return aligns


def check(apk_path: str):
    failures = []
    checked = 0
    with zipfile.ZipFile(apk_path) as apk:
        names = [n for n in apk.namelist() if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
        if not names:
            return checked, failures
        for name in sorted(names):
            aligns = load_aligns(apk.read(name))
            if aligns is None:
                continue
            checked += 1
            bad = [a for a in aligns if a < PAGE_16K or a % PAGE_16K]
            if bad:
                failures.append((name, bad))
    return checked, failures


def main():
    if len(sys.argv) < 2:
        print("usage: check_page_size.py <apk> ...", file=sys.stderr)
        return 2
    failed = 0
    for path in sys.argv[1:]:
        try:
            checked, failures = check(path)
        except Exception as exc:
            print(f"{path}: ERROR {exc}", file=sys.stderr)
            failed = 1
            continue
        if failures:
            failed = 1
            print(f"{path}: FAIL ({checked} libs)")
            for name, aligns in failures:
                print(f"  {name}: p_align={[hex(a) for a in aligns]}")
        else:
            print(f"{path}: OK ({checked} arm64 libs, all PT_LOAD p_align >= 0x4000)")
    return failed


if __name__ == "__main__":
    raise SystemExit(main())
