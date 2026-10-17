#!/usr/bin/env python3
"""Merge JARs into a single executable uber-jar (Main-Class, no Class-Path)."""
import sys
import zipfile
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 4:
        print(
            f"usage: {sys.argv[0]} <output.jar> <main-class> <jar>...",
            file=sys.stderr,
        )
        return 2

    out = Path(sys.argv[1])
    main_class = sys.argv[2]
    inputs = [Path(p) for p in sys.argv[3:]]

    # Later inputs win on path collisions (CLI over lib for shared paths).
    entries: dict[str, bytes] = {}
    for jar in inputs:
        with zipfile.ZipFile(jar) as zf:
            for info in zf.infolist():
                name = info.filename
                if name.endswith("/") or name == "META-INF/MANIFEST.MF":
                    continue
                if name.startswith("META-INF/") and name.upper().endswith(
                    (".SF", ".DSA", ".RSA")
                ):
                    continue
                entries[name] = zf.read(info)

    manifest = (
        "Manifest-Version: 1.0\r\n"
        f"Main-Class: {main_class}\r\n"
        "\r\n"
    ).encode("utf-8")

    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w") as zf:
        zf.writestr("META-INF/MANIFEST.MF", manifest)
        for name, data in sorted(entries.items()):
            zf.writestr(name, data)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
