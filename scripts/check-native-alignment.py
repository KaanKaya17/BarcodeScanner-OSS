"""Check 64-bit Android ELF LOAD alignment in an APK or Android App Bundle."""

import struct
import sys
from pathlib import Path
from zipfile import ZipFile

PAGE_SIZE = 16 * 1024
ABIS = {"arm64-v8a", "x86_64", "riscv64"}


def load_alignments(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("expected a little-endian 64-bit ELF file")
    table = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    for index in range(count):
        entry = table + index * entry_size
        segment_type = struct.unpack_from("<I", data, entry)[0]
        if segment_type == 1:  # PT_LOAD
            yield struct.unpack_from("<Q", data, entry + 48)[0]


def check(path):
    passed = True
    with ZipFile(path) as package:
        for entry in package.namelist():
            parts = entry.split("/")
            if len(parts) < 3 or parts[-3] != "lib" or parts[-2] not in ABIS or not entry.endswith(".so"):
                continue
            try:
                alignments = list(load_alignments(package.read(entry)))
                aligned = bool(alignments) and all(value >= PAGE_SIZE for value in alignments)
                sizes = ", ".join(str(value) for value in alignments) or "no load segments"
            except (ValueError, struct.error) as error:
                aligned = False
                sizes = str(error)
            print(f"{path.name} · {entry} · {sizes} · {'OK' if aligned else 'FAIL'}")
            passed &= aligned
    return passed


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("Usage: check-native-alignment.py APK_OR_AAB [APK_OR_AAB ...]")
    results = [check(Path(argument)) for argument in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
