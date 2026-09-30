#!/usr/bin/env python3
"""Reconcile the loaded authorization rows against the supplied EBCDIC IMS image.

Section 8.5 requires the migration pipeline to end in reconciliation rather than in a load. Three
checks are run against the `carddemo_authorization` database:

1. every decoded `PAUTSUM0` root and `PAUTDTL1` child reached the database, with matching counts
   and matching parent/child fan-out per account;
2. the summary money fields, counters and status characters of every root match the decoded
   segment, including the trailing-blank semantics of the `PIC X(n)` fields;
3. the detail key fields, the complemented date/time key components and the money fields of every
   child match the decoded segment, and the stored key equals the nines complement the source
   program derives.

Usage: python3 reconcile_authorization_data.py <repo-root> [--container <postgres-container>]

Pass ``--container -`` when PostgreSQL runs natively rather than in a container; ``psql`` on the
path is then used directly, against ``--host`` (default ``localhost``). The password is taken from
the environment (``PGPASSWORD``) so that no credential is held in this file.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
from decimal import Decimal

from ims_authorization_unload import Detail, Summary, decode_unload, default_unload_path

DATABASE = "carddemo_authorization"


def query(container: str, sql: str, host: str = "localhost") -> list[dict[str, str]]:
    """Run a read-only query against the authorization database and return its JSON rows."""
    wrapped = f"select coalesce(json_agg(reconciled), '[]'::json) from ({sql}) reconciled"
    psql = ["psql", "-U", "carddemo", "-d", DATABASE, "-t", "-A", "-c", wrapped]
    if container == "-":
        psql[1:1] = ["-h", host]
    command = psql if container == "-" else ["docker", "exec", container, *psql]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    # `json_agg` renders a numeric column as a JSON number; parsing it through `float` would
    # introduce the binary rounding that COMP-3 money must never acquire, so every JSON number is
    # read straight into `Decimal`.
    return json.loads(completed.stdout.strip(), parse_float=Decimal, parse_int=Decimal)


def compare(label: str, expected: object, actual: object, failures: list[str]) -> None:
    """Record a failure when a reconciled field does not match the decoded segment."""
    if expected != actual:
        failures.append(f"{label}: source={expected!r} database={actual!r}")


def reconcile_summaries(rows: dict[int, dict[str, str]], summaries: list[Summary]) -> list[str]:
    """Compare every decoded root against its stored row."""
    failures: list[str] = []
    for summary in summaries:
        row = rows.get(summary.acct_id)
        if row is None:
            failures.append(f"summary {summary.acct_id}: missing from database")
            continue
        key = f"summary {summary.acct_id}"
        compare(f"{key} cust_id", summary.cust_id, int(row["cust_id"]), failures)
        compare(f"{key} auth_status", summary.auth_status, row["auth_status"], failures)
        compare(f"{key} account_status", summary.account_status, row["account_status"], failures)
        compare(f"{key} credit_limit", summary.credit_limit, Decimal(row["credit_limit"]),
                failures)
        compare(f"{key} cash_limit", summary.cash_limit, Decimal(row["cash_limit"]), failures)
        compare(f"{key} credit_balance", summary.credit_balance, Decimal(row["credit_balance"]),
                failures)
        compare(f"{key} cash_balance", summary.cash_balance, Decimal(row["cash_balance"]),
                failures)
        compare(f"{key} approved_auth_cnt", summary.approved_auth_cnt,
                int(row["approved_auth_cnt"]), failures)
        compare(f"{key} declined_auth_cnt", summary.declined_auth_cnt,
                int(row["declined_auth_cnt"]), failures)
        compare(f"{key} approved_auth_amt", summary.approved_auth_amt,
                Decimal(row["approved_auth_amt"]), failures)
        compare(f"{key} declined_auth_amt", summary.declined_auth_amt,
                Decimal(row["declined_auth_amt"]), failures)
    return failures


def reconcile_details(rows: dict[tuple[int, str], dict[str, str]],
                      details: list[Detail]) -> list[str]:
    """Compare every decoded child against its stored row."""
    failures: list[str] = []
    for detail in details:
        row = rows.get((detail.acct_id, detail.auth_key))
        if row is None:
            failures.append(f"detail {detail.acct_id}/{detail.auth_key}: missing from database")
            continue
        key = f"detail {detail.acct_id}/{detail.auth_key}"
        compare(f"{key} auth_date_9c", detail.auth_date_9c, int(row["auth_date_9c"]), failures)
        compare(f"{key} auth_time_9c", detail.auth_time_9c, int(row["auth_time_9c"]), failures)
        compare(f"{key} card_num", detail.card_num, row["card_num"], failures)
        compare(f"{key} auth_orig_date", detail.auth_orig_date, row["auth_orig_date"], failures)
        compare(f"{key} auth_orig_time", detail.auth_orig_time, row["auth_orig_time"], failures)
        compare(f"{key} auth_resp_code", detail.auth_resp_code, row["auth_resp_code"], failures)
        compare(f"{key} auth_resp_reason", detail.auth_resp_reason, row["auth_resp_reason"],
                failures)
        compare(f"{key} transaction_amt", detail.transaction_amt,
                Decimal(row["transaction_amt"]), failures)
        compare(f"{key} approved_amt", detail.approved_amt, Decimal(row["approved_amt"]),
                failures)
        compare(f"{key} merchant_name", detail.merchant_name, row["merchant_name"], failures)
        compare(f"{key} merchant_city", detail.merchant_city, row["merchant_city"], failures)
        compare(f"{key} match_status", detail.match_status, row["match_status"], failures)
        compare(f"{key} auth_fraud", detail.auth_fraud, row["auth_fraud"], failures)
        compare(f"{key} fraud_rpt_date", detail.fraud_rpt_date, row["fraud_rpt_date"], failures)
        expected_key = f"{detail.auth_date_9c:05d}{detail.auth_time_9c:09d}"
        compare(f"{key} complemented key", expected_key, row["auth_key"], failures)
    return failures


def main() -> int:
    """Run the three reconciliation checks and report one line per finding."""
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_root", type=pathlib.Path)
    parser.add_argument("--container", default="-",
                        help="PostgreSQL container name, or '-' for a native server on the path")
    parser.add_argument("--host", default="localhost",
                        help="host of a native PostgreSQL server")
    args = parser.parse_args()

    summaries, details, anomalies = decode_unload(default_unload_path(args.repo_root))
    print("== EBCDIC (cp037) decode of AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat ==")
    print(f"PAUTSUM0 roots decoded : {len(summaries)}")
    print(f"PAUTDTL1 children      : {len(details)}")
    for anomaly in anomalies:
        print(f"known supplied-data defect (UCR-31): {anomaly}")

    print()
    print("== record counts loaded into PostgreSQL ==")
    counts = query(args.container,
                   "select 'pending_auth_summary' as store, count(*) from pending_auth_summary "
                   "union all select 'pending_auth_detail', count(*) from pending_auth_detail",
                   args.host)
    loaded = {row["store"]: int(row["count"]) for row in counts}
    failures: list[str] = []
    for store, expected in (("pending_auth_summary", len(summaries)),
                            ("pending_auth_detail", len(details))):
        status = "OK" if loaded.get(store) == expected else "MISMATCH"
        if status != "OK":
            failures.append(f"{store}: source={expected} database={loaded.get(store)}")
        print(f"{store:21s} source={expected:4d} database={loaded.get(store, 0):4d} {status}")

    print()
    print("== parent/child fan-out per account ==")
    fanout = {int(row["acct_id"]): int(row["children"]) for row in
              query(args.container, "select acct_id, count(*) as children "
                                    "from pending_auth_detail group by acct_id", args.host)}
    expected_fanout: dict[int, int] = {}
    for detail in details:
        expected_fanout[detail.acct_id] = expected_fanout.get(detail.acct_id, 0) + 1
    mismatched = {acct for acct in set(fanout) | set(expected_fanout)
                  if fanout.get(acct, 0) != expected_fanout.get(acct, 0)}
    for acct in sorted(mismatched):
        failures.append(f"fan-out {acct}: source={expected_fanout.get(acct, 0)} "
                        f"database={fanout.get(acct, 0)}")
    print(f"accounts with children source={len(expected_fanout)} database={len(fanout)} "
          f"differing={len(mismatched)} {'OK' if not mismatched else 'MISMATCH'}")

    print()
    print("== field level reconciliation ==")
    summary_rows = {int(row["acct_id"]): row for row in
                    query(args.container, "select * from pending_auth_summary", args.host)}
    detail_rows = {(int(row["acct_id"]), row["auth_key"]): row for row in
                   query(args.container, "select * from pending_auth_detail", args.host)}
    summary_failures = reconcile_summaries(summary_rows, summaries)
    detail_failures = reconcile_details(detail_rows, details)
    print(f"summary fields compared={len(summaries) * 11:5d} "
          f"failures={len(summary_failures)} "
          f"{'OK' if not summary_failures else 'MISMATCH'}")
    print(f"detail  fields compared={len(details) * 15:5d} "
          f"failures={len(detail_failures)} "
          f"{'OK' if not detail_failures else 'MISMATCH'}")
    failures.extend(summary_failures)
    failures.extend(detail_failures)

    print()
    for failure in failures:
        print(f"FAILURE {failure}")
    print("RECONCILIATION PASSED" if not failures else f"RECONCILIATION FAILED ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
