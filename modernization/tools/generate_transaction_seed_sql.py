#!/usr/bin/env python3
"""Generate the transaction service seed migration from the supplied ASCII sample data.

The IDCAMS jobs app/jcl/TRANFILE.jcl, TRANTYPE.jcl, TRANCATG.jcl, TCATBALF.jcl and DISCGRP.jcl
define the five clusters of the transaction context and REPRO the shipped sequential files into
them. TRANFILE.jcl loads the transaction master from AWS.M2.CARDDEMO.DALYTRAN.PS.INIT, which is
the supplied dailytran.txt, so the transaction table is seeded from that same file.

The record layouts come from the copybooks (app/cpy/CVTRA05Y.cpy, CVTRA03Y.cpy, CVTRA04Y.cpy,
CVTRA01Y.cpy, CVTRA02Y.cpy). Signed display amounts carry their sign in the last character as a
zoned decimal overpunch, which generate_seed_sql.py already decodes.

Usage: python3 modernization/tools/generate_transaction_seed_sql.py
"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from generate_seed_sql import Layout, read_records, render  # noqa: E402

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
TARGET = (REPO_ROOT / "modernization" / "transaction-service" / "src" / "main" / "resources"
          / "db" / "migration" / "V2__load_transaction_sample_data.sql")

TRANSACTION_LAYOUT: Layout = [
    ("tran_id", 16, "char"),
    ("tran_type_cd", 2, "char"),
    ("tran_cat_cd", 4, "num"),
    ("tran_source", 10, "char"),
    ("tran_desc", 100, "char"),
    ("tran_amt", 11, "money"),
    ("tran_merchant_id", 9, "num"),
    ("tran_merchant_name", 50, "char"),
    ("tran_merchant_city", 50, "char"),
    ("tran_merchant_zip", 10, "char"),
    ("tran_card_num", 16, "char"),
    ("tran_orig_ts", 26, "char"),
    ("tran_proc_ts", 26, "char"),
]

TRAN_TYPE_LAYOUT: Layout = [
    ("tran_type", 2, "char"),
    ("tran_type_desc", 50, "char"),
]

TRAN_CATEGORY_LAYOUT: Layout = [
    ("tran_type_cd", 2, "char"),
    ("tran_cat_cd", 4, "num"),
    ("tran_cat_type_desc", 50, "char"),
]

CATEGORY_BALANCE_LAYOUT: Layout = [
    ("trancat_acct_id", 11, "num"),
    ("trancat_type_cd", 2, "char"),
    ("trancat_cd", 4, "num"),
    ("tran_cat_bal", 11, "money"),
]

DISCLOSURE_GROUP_LAYOUT: Layout = [
    ("dis_acct_group_id", 10, "char"),
    ("dis_tran_type_cd", 2, "char"),
    ("dis_tran_cat_cd", 4, "num"),
    ("dis_int_rate", 6, "money"),
]


def main() -> None:
    """Regenerate the transaction seed migration from the sample data files."""
    transactions = read_records("dailytran.txt", 350)
    types = read_records("trantype.txt", 60)
    categories = read_records("trancatg.txt", 60)
    balances = read_records("tcatbal.txt", 50)
    groups = read_records("discgrp.txt", 50)

    header = f"""-- GENERATED FILE - do not edit by hand.
-- Produced by modernization/tools/generate_transaction_seed_sql.py from the sample data supplied
-- with the application: app/data/ASCII/dailytran.txt, trantype.txt, trancatg.txt, tcatbal.txt and
-- discgrp.txt.
-- This is the local equivalent of the IDCAMS REPRO steps in app/jcl/TRANFILE.jcl, TRANTYPE.jcl,
-- TRANCATG.jcl, TCATBALF.jcl and DISCGRP.jcl.
-- Rows: {len(transactions)} transactions, {len(types)} transaction types, {len(categories)} categories,
-- {len(balances)} category balances, {len(groups)} disclosure groups.

"""
    body = "\n".join([
        render("transaction", TRANSACTION_LAYOUT, transactions),
        render("transaction_type", TRAN_TYPE_LAYOUT, types),
        render("transaction_category", TRAN_CATEGORY_LAYOUT, categories),
        render("transaction_category_balance", CATEGORY_BALANCE_LAYOUT, balances),
        render("disclosure_group", DISCLOSURE_GROUP_LAYOUT, groups),
    ])
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(header + body, encoding="utf-8")
    print(f"wrote {TARGET.relative_to(REPO_ROOT)}: {len(transactions)} transactions, "
          f"{len(types)} types, {len(categories)} categories, {len(balances)} balances, "
          f"{len(groups)} groups")


if __name__ == "__main__":
    main()
