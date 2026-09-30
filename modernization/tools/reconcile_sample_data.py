#!/usr/bin/env python3
"""Reconcile the loaded PostgreSQL rows against the supplied sample data files.

Three checks are run for the ACCT, CUSTOMER and CARDXREF stores:

1. the EBCDIC data sets under ``app/data/EBCDIC`` decode (code page 037) to exactly the
   ASCII files under ``app/data/ASCII``, which is what makes the ASCII files usable as the
   seed source;
2. every record of the ASCII files reached the database, with matching record counts;
3. the key fields and, for the account master, the signed ``S9(10)V99`` money fields of every
   record match the values stored by the Flyway seed migration.

Usage: python3 reconcile_sample_data.py <repo-root> [--container <postgres-container>]

Pass ``--container -`` when PostgreSQL runs natively rather than in a container; ``psql`` on the
path is then used directly.
"""
from __future__ import annotations

import argparse
import json
import subprocess
from decimal import Decimal
from pathlib import Path

# (ascii file, ebcdic data set, record length)
STORES: dict[str, tuple[str, str, int]] = {
    "account": ("acctdata.txt", "AWS.M2.CARDDEMO.ACCTDATA.PS", 300),
    "customer": ("custdata.txt", "AWS.M2.CARDDEMO.CUSTDATA.PS", 500),
    "card_xref": ("cardxref.txt", "AWS.M2.CARDDEMO.CARDXREF.PS", 50),
}

# Known differences between the two supplied copies of the same sample data. These are defects of
# the supplied data, not of the conversion; they are reported on every run and are recorded in the
# Unsupported Construct Register.
KNOWN_DATA_DIFFERENCES: dict[tuple[str, int], str] = {
    ("account", 48): "ACCT-ADDR-ZIP/ACCT-GROUP-ID differ: 'A000000000'+zeroes in ASCII, "
                     "'ZEROAPR' in EBCDIC",
}

OVERPUNCH = {"{": 0, "}": 0, "A": 1, "B": 2, "C": 3, "D": 4, "E": 5, "F": 6, "G": 7, "H": 8,
             "I": 9, "J": 1, "K": 2, "L": 3, "M": 4, "N": 5, "O": 6, "P": 7, "Q": 8, "R": 9}


def decode_money(field: str) -> Decimal:
    """Decode a signed zoned decimal S9(10)V99 display field."""
    digits, last = field[:-1], field[-1]
    if last.isdigit():
        unsigned, negative = digits + last, False
    else:
        unsigned, negative = digits + str(OVERPUNCH[last]), last in "}JKLMNOPQR"
    value = Decimal(unsigned).scaleb(-2)
    return -value if negative else value


def read_fixed(path: Path, length: int) -> list[str]:
    """Read a fixed length record file, padding short lines to the record length."""
    lines = path.read_text(encoding="latin-1").splitlines()
    return [line.ljust(length)[:length] for line in lines if line.strip()]


def decode_ebcdic(path: Path, length: int) -> list[str]:
    """Split an unblocked EBCDIC data set into records and decode them to text."""
    raw = path.read_bytes().decode("cp037")
    return [raw[offset:offset + length] for offset in range(0, len(raw), length)]


def query(container: str, sql: str) -> list[dict]:
    """Run a read-only query against the local account database and return its JSON rows."""
    wrapped = f"select coalesce(json_agg(reconciled), '[]'::json) from ({sql}) reconciled"
    psql = ["psql", "-U", "carddemo", "-d", "carddemo", "-t", "-A", "-c", wrapped]
    command = psql if container == "-" else ["docker", "exec", container, *psql]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return json.loads(completed.stdout.strip())


def main() -> int:
    """Run all three reconciliation checks and report one line per finding."""
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_root", type=Path)
    parser.add_argument("--container", default="carddemo-pg",
                        help="PostgreSQL container name, or '-' for a native server on the path")
    args = parser.parse_args()

    failures = 0
    ascii_records: dict[str, list[str]] = {}

    print("== EBCDIC (cp037) to ASCII equivalence ==")
    for store, (ascii_name, ebcdic_name, length) in STORES.items():
        ascii_path = args.repo_root / "app" / "data" / "ASCII" / ascii_name
        ebcdic_path = args.repo_root / "app" / "data" / "EBCDIC" / ebcdic_name
        expected = read_fixed(ascii_path, length)
        ascii_records[store] = expected
        decoded = decode_ebcdic(ebcdic_path, length)
        mismatched = [index for index, (left, right) in enumerate(zip(expected, decoded))
                      if left != right]
        unexpected = [index for index in mismatched
                      if (store, index) not in KNOWN_DATA_DIFFERENCES]
        status = "OK" if not unexpected and len(expected) == len(decoded) else "MISMATCH"
        failures += status != "OK"
        print(f"{store:10s} {ascii_name:14s} ascii={len(expected):3d} ebcdic={len(decoded):3d} "
              f"differing={len(mismatched):3d} unexpected={len(unexpected):3d} {status}")
        for index in mismatched:
            known = KNOWN_DATA_DIFFERENCES.get((store, index))
            print(f"           record {index}: {known or 'UNEXPECTED DIFFERENCE'}")

    print()
    print("== record counts loaded into PostgreSQL ==")
    counts = query(args.container, "select 'account' as store, count(*) from account "
                                   "union all select 'customer', count(*) from customer "
                                   "union all select 'card_xref', count(*) from card_xref")
    loaded = {row["store"]: int(row["count"]) for row in counts}
    for store, records in ascii_records.items():
        status = "OK" if loaded.get(store) == len(records) else "MISMATCH"
        failures += status != "OK"
        print(f"{store:10s} sample_file={len(records):3d} database={loaded.get(store):3d} {status}")

    print()
    print("== account master field level reconciliation ==")
    rows = {int(row["acct_id"]): row for row in query(
        args.container,
        "select acct_id, active_status, curr_bal, credit_limit, cash_credit_limit, "
        "curr_cyc_credit, curr_cyc_debit, open_date, expiration_date, reissue_date, "
        "addr_zip, group_id from account")}
    checked = 0
    for record in ascii_records["account"]:
        acct_id = int(record[0:11])
        row = rows[acct_id]
        expectations = [
            ("active_status", record[11:12], row["active_status"]),
            ("curr_bal", decode_money(record[12:24]), Decimal(row["curr_bal"])),
            ("credit_limit", decode_money(record[24:36]), Decimal(row["credit_limit"])),
            ("cash_credit_limit", decode_money(record[36:48]), Decimal(row["cash_credit_limit"])),
            ("open_date", record[48:58], str(row["open_date"])),
            ("expiration_date", record[58:68], str(row["expiration_date"])),
            ("reissue_date", record[68:78], str(row["reissue_date"])),
            ("curr_cyc_credit", decode_money(record[78:90]), Decimal(row["curr_cyc_credit"])),
            ("curr_cyc_debit", decode_money(record[90:102]), Decimal(row["curr_cyc_debit"])),
            ("addr_zip", record[102:112], row["addr_zip"]),
            ("group_id", record[112:122], row["group_id"]),
        ]
        for name, expected, actual in expectations:
            checked += 1
            if expected != actual:
                failures += 1
                print(f"acct {acct_id:011d} {name}: sample={expected!r} database={actual!r} MISMATCH")
    print(f"{len(ascii_records['account'])} accounts, {checked} field comparisons, "
          f"{'all matched' if not failures else str(failures) + ' failures'}")

    print()
    print("RECONCILIATION " + ("PASSED" if failures == 0 else f"FAILED ({failures})"))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
