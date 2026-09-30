#!/usr/bin/env python3
"""Generate the Flyway seed migration from the supplied ASCII sample data.

The IDCAMS job app/jcl/ACCTFILE.jcl deletes and defines the ACCTDAT cluster and REPROs
AWS.M2.CARDDEMO.ACCTDATA.PS into it. The local equivalent is a Flyway migration that inserts the
same records, so the seed SQL is generated straight from the sample files rather than written by
hand, which keeps the row values traceable to the source data.

The record layouts come from the copybooks (app/cpy/CVACT01Y.cpy, CVCUS01Y.cpy, CVACT03Y.cpy).
Signed S9(10)V99 fields are stored as twelve display characters whose last character carries the
sign as a zoned decimal overpunch ('{' = +0 ... 'I' = +9, '}' = -0 ... 'R' = -9).

Usage: python3 modernization/tools/generate_seed_sql.py
"""
from __future__ import annotations

import pathlib
from decimal import Decimal

Layout = list[tuple[str, int, str]]

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DATA_DIR = REPO_ROOT / "app" / "data" / "ASCII"
MIGRATIONS = (REPO_ROOT / "modernization" / "account-service" / "src" / "main" / "resources"
              / "db" / "migration")
TARGET = MIGRATIONS / "V2__load_sample_data.sql"
CARD_TARGET = MIGRATIONS / "V4__load_card_sample_data.sql"

POSITIVE_OVERPUNCH = "{ABCDEFGHI"
NEGATIVE_OVERPUNCH = "}JKLMNOPQR"

ACCOUNT_LAYOUT: Layout = [
    ("acct_id", 11, "num"),
    ("active_status", 1, "char"),
    ("curr_bal", 12, "money"),
    ("credit_limit", 12, "money"),
    ("cash_credit_limit", 12, "money"),
    ("open_date", 10, "char"),
    ("expiration_date", 10, "char"),
    ("reissue_date", 10, "char"),
    ("curr_cyc_credit", 12, "money"),
    ("curr_cyc_debit", 12, "money"),
    ("addr_zip", 10, "char"),
    ("group_id", 10, "char"),
]

CUSTOMER_LAYOUT: Layout = [
    ("cust_id", 9, "num"),
    ("first_name", 25, "char"),
    ("middle_name", 25, "char"),
    ("last_name", 25, "char"),
    ("addr_line_1", 50, "char"),
    ("addr_line_2", 50, "char"),
    ("addr_line_3", 50, "char"),
    ("addr_state_cd", 2, "char"),
    ("addr_country_cd", 3, "char"),
    ("addr_zip", 10, "char"),
    ("phone_num_1", 15, "char"),
    ("phone_num_2", 15, "char"),
    ("ssn", 9, "char"),
    ("govt_issued_id", 20, "char"),
    ("dob_yyyy_mm_dd", 10, "char"),
    ("eft_account_id", 10, "char"),
    ("pri_card_holder_ind", 1, "char"),
    ("fico_credit_score", 3, "num"),
]

CARD_LAYOUT: Layout = [
    ("card_num", 16, "char"),
    ("card_acct_id", 11, "num"),
    ("card_cvv_cd", 3, "num"),
    ("card_embossed_name", 50, "char"),
    ("card_expiraion_date", 10, "char"),
    ("card_active_status", 1, "char"),
]

XREF_LAYOUT: Layout = [
    ("xref_card_num", 16, "char"),
    ("xref_cust_id", 9, "num"),
    ("xref_acct_id", 11, "num"),
]


def decode_money(field: str) -> Decimal:
    """Decode a signed zoned decimal S9(10)V99 display field."""
    digits, last = field[:-1], field[-1]
    sign = 1
    if last in POSITIVE_OVERPUNCH:
        digits += str(POSITIVE_OVERPUNCH.index(last))
    elif last in NEGATIVE_OVERPUNCH:
        digits += str(NEGATIVE_OVERPUNCH.index(last))
        sign = -1
    elif last.isdigit():
        digits += last
    else:
        raise ValueError(f"unexpected sign character {last!r} in {field!r}")
    return sign * Decimal(digits) / Decimal(100)


def split_record(record: str, layout: Layout) -> dict[str, str]:
    """Split a fixed length record into its copybook fields."""
    values: dict[str, str] = {}
    offset = 0
    for name, length, _kind in layout:
        values[name] = record[offset:offset + length]
        offset += length
    return values


def sql_literal(raw: str, kind: str) -> str:
    """Render one field as a SQL literal."""
    if kind == "money":
        return str(decode_money(raw))
    if kind == "num":
        stripped = raw.strip() or "0"
        return str(int(stripped))
    return "'" + raw.replace("'", "''") + "'"


def render(table: str, layout: Layout, records: list[str]) -> str:
    """Render an INSERT statement covering every supplied record."""
    columns = ", ".join(name for name, _length, _kind in layout)
    rows = []
    for record in records:
        values = split_record(record, layout)
        rendered = ", ".join(sql_literal(values[name], kind) for name, _length, kind in layout)
        rows.append(f"    ({rendered})")
    return f"INSERT INTO {table} ({columns}) VALUES\n" + ",\n".join(rows) + ";\n"


def read_records(filename: str, length: int) -> list[str]:
    """Read the sample data file, padding each line to the record length."""
    path = DATA_DIR / filename
    records = []
    for line in path.read_text(encoding="latin-1").splitlines():
        if not line.strip():
            continue
        records.append(line.ljust(length))
    return records


def main() -> None:
    """Regenerate the seed migration from the sample data files."""
    account_records = read_records("acctdata.txt", 300)
    customer_records = read_records("custdata.txt", 500)
    xref_records = read_records("cardxref.txt", 50)

    header = f"""-- GENERATED FILE - do not edit by hand.
-- Produced by modernization/tools/generate_seed_sql.py from the sample data supplied with the
-- application: app/data/ASCII/acctdata.txt, custdata.txt, cardxref.txt.
-- This is the local equivalent of the IDCAMS REPRO steps in app/jcl/ACCTFILE.jcl.
-- Rows: {len(account_records)} accounts, {len(customer_records)} customers, {len(xref_records)} card cross references.

"""
    body = "\n".join([
        render("account", ACCOUNT_LAYOUT, account_records),
        render("customer", CUSTOMER_LAYOUT, customer_records),
        render("card_xref", XREF_LAYOUT, xref_records),
    ])
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(header + body, encoding="utf-8")
    print(f"wrote {TARGET.relative_to(REPO_ROOT)}: "
          f"{len(account_records)} accounts, {len(customer_records)} customers, {len(xref_records)} xrefs")

    card_records = read_records("carddata.txt", 150)
    card_header = f"""-- GENERATED FILE - do not edit by hand.
-- Produced by modernization/tools/generate_seed_sql.py from app/data/ASCII/carddata.txt.
-- This is the local equivalent of the IDCAMS REPRO step in app/jcl/CARDFILE.jcl.
-- Rows: {len(card_records)} cards.

"""
    CARD_TARGET.write_text(card_header + render("card", CARD_LAYOUT, card_records), encoding="utf-8")
    print(f"wrote {CARD_TARGET.relative_to(REPO_ROOT)}: {len(card_records)} cards")


if __name__ == "__main__":
    main()
