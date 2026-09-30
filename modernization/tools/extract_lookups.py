#!/usr/bin/env python3
"""Extract the reference-data tables from app/cpy/CSLKPCDY.cpy into resource files.

The COBOL copybook declares the tables as 88-level VALUES lists. Keeping the
extraction scripted means the generated resources stay traceable to the source
copybook instead of being retyped by hand.

Usage: python3 extract_lookups.py <repo-root> <output-dir>
"""
import re
import sys
from pathlib import Path

CONDITIONS = {
    "VALID-PHONE-AREA-CODE": "phone-area-codes.txt",
    "VALID-GENERAL-PURP-CODE": "phone-area-codes-general-purpose.txt",
    "VALID-EASY-RECOG-AREA-CODE": "phone-area-codes-easily-recognizable.txt",
    "VALID-US-STATE-CODE": "us-state-codes.txt",
    "VALID-US-STATE-ZIP-CD2-COMBO": "us-state-zip2-combos.txt",
}

LITERAL = re.compile(r"'([^']*)'")


def strip_source(line: str) -> str:
    """Return the code area of a fixed form COBOL line, dropping comment lines."""
    line = line.replace("\t", " " * 8)
    if len(line) > 6 and line[6] in ("*", "/"):
        return ""
    body = line[6:72] if len(line) > 6 else ""
    return body


def extract(copybook: Path) -> dict[str, list[str]]:
    """Collect the literal values of every 88-level condition named in CONDITIONS."""
    tables: dict[str, list[str]] = {}
    current = None
    for raw in copybook.read_text(encoding="utf-8", errors="replace").splitlines():
        body = strip_source(raw)
        if not body.strip():
            continue
        marker = re.search(r"88\s+([A-Z0-9-]+)\s+VALUES?", body)
        if marker:
            name = marker.group(1)
            current = name if name in CONDITIONS else None
            if current:
                tables.setdefault(current, [])
        if current is None:
            continue
        tables[current].extend(LITERAL.findall(body))
        if body.rstrip().endswith("."):
            current = None
    return tables


def main() -> int:
    """Write one resource file per extracted lookup table."""
    repo_root = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    copybook = repo_root / "app" / "cpy" / "CSLKPCDY.cpy"
    tables = extract(copybook)
    for condition, filename in CONDITIONS.items():
        values = tables.get(condition, [])
        if not values:
            print(f"WARNING: no values extracted for {condition}")
        target = out_dir / filename
        header = (
            f"# Generated from app/cpy/CSLKPCDY.cpy 88-level {condition}\n"
            f"# Do not edit by hand - re-run modernization/tools/extract_lookups.py\n"
        )
        target.write_text(header + "\n".join(values) + "\n", encoding="utf-8")
        print(f"{filename}: {len(values)} values")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
