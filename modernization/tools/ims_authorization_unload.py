#!/usr/bin/env python3
"""Decode the supplied EBCDIC image of IMS database DBPAUTP0.

``app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`` is an IMS
unload of the HIDAM database: a sequence of variable length records, each carrying one segment in
hierarchic order, so a ``PAUTSUM0`` root is followed by its ``PAUTDTL1`` children.

Record layout, established from the supplied file and the two DBDs:

======  ======================================================================
offset  content
======  ======================================================================
0-1     record length, binary
8-9     segment length, binary (100 for ``PAUTSUM0``, 200 for ``PAUTDTL1``)
10-17   segment name, EBCDIC
18-38   IMS prefix (segment code, level, delete byte, pointer area) - not data
39-     segment data as laid out by copybook ``CIPAUSMY`` / ``CIPAUDTY``
======  ======================================================================

The segment data is decoded here rather than being hand transcribed, so that the seed migration
and the reconciliation report share one decoder and the packed-decimal, zoned-decimal, binary and
fixed-width character semantics of the copybooks are applied exactly once.
"""
from __future__ import annotations

import pathlib
from dataclasses import dataclass
from decimal import Decimal

EBCDIC = "cp037"
SEGMENT_NAME_AT = slice(10, 18)
SEGMENT_LENGTH_AT = slice(8, 10)
DATA_AT = 39

SUMMARY_SEGMENT = "PAUTSUM0"
DETAIL_SEGMENT = "PAUTDTL1"


class UnloadFormatError(ValueError):
    """Raised when a record does not match the documented unload layout."""


def unpack_packed(raw: bytes, scale: int = 0) -> Decimal:
    """Decode a COMP-3 (packed decimal) field, applying an implied decimal ``scale``."""
    digits = ""
    for byte in raw[:-1]:
        digits += f"{byte >> 4}{byte & 0x0F}"
    last = raw[-1]
    digits += str(last >> 4)
    sign = last & 0x0F
    if sign not in (0x0C, 0x0D, 0x0F, 0x0E):
        raise UnloadFormatError(f"not a packed decimal field: {raw.hex()}")
    value = Decimal(digits or "0").scaleb(-scale)
    return -value if sign == 0x0D else value


def unpack_binary(raw: bytes) -> int:
    """Decode a COMP (binary) field as a signed big-endian integer."""
    return int.from_bytes(raw, "big", signed=True)


def unpack_text(raw: bytes) -> str:
    """Decode an EBCDIC PIC X(n) field, keeping the trailing blanks the layout defines.

    A field the loader never populated holds LOW-VALUES (``X'00'``) rather than EBCDIC blanks;
    ``PA-AUTH-STATUS`` and ``PA-ACCOUNT-STATUS`` are in that state throughout the supplied image.
    LOW-VALUES becomes a blank here, because PostgreSQL text types cannot hold a NUL code point and
    both values mean "not set" to every program that reads the segment. The rule is recorded in
    ``docs/05-data-mapping.md`` and ``docs/11-unsupported-construct-register.md`` (UCR-32).
    """
    return raw.decode(EBCDIC).replace("\x00", " ")


def unpack_digits(raw: bytes) -> int:
    """Decode an unsigned zoned decimal PIC 9(n) DISPLAY field."""
    text = unpack_text(raw).strip()
    if not text.isdigit():
        raise UnloadFormatError(f"not a numeric display field: {raw.hex()}")
    return int(text)


@dataclass(frozen=True)
class Summary:
    """One ``PAUTSUM0`` segment, the pending authorization summary of an account."""

    acct_id: int
    cust_id: int
    auth_status: str
    account_status: str
    credit_limit: Decimal
    cash_limit: Decimal
    credit_balance: Decimal
    cash_balance: Decimal
    approved_auth_cnt: int
    declined_auth_cnt: int
    approved_auth_amt: Decimal
    declined_auth_amt: Decimal


@dataclass(frozen=True)
class Detail:
    """One ``PAUTDTL1`` segment, a pending authorization beneath its summary."""

    acct_id: int
    auth_date_9c: int
    auth_time_9c: int
    auth_key: str
    auth_orig_date: str
    auth_orig_time: str
    card_num: str
    auth_type: str
    card_expiry_date: str
    message_type: str
    message_source: str
    auth_id_code: str
    auth_resp_code: str
    auth_resp_reason: str
    processing_code: int
    transaction_amt: Decimal
    approved_amt: Decimal
    merchant_category_code: str
    acqr_country_code: str
    pos_entry_mode: str
    merchant_id: str
    merchant_name: str
    merchant_city: str
    merchant_state: str
    merchant_zip: str
    transaction_id: str
    match_status: str
    auth_fraud: str
    fraud_rpt_date: str


def _decode_summary(data: bytes) -> Summary:
    """Decode ``CIPAUSMY``: the 100 byte pending authorization summary segment."""
    return Summary(
        acct_id=int(unpack_packed(data[0:6])),
        cust_id=unpack_digits(data[6:15]),
        auth_status=unpack_text(data[15:16]),
        account_status=unpack_text(data[16:26]),
        credit_limit=unpack_packed(data[26:32], 2),
        cash_limit=unpack_packed(data[32:38], 2),
        credit_balance=unpack_packed(data[38:44], 2),
        cash_balance=unpack_packed(data[44:50], 2),
        approved_auth_cnt=unpack_binary(data[50:52]),
        declined_auth_cnt=unpack_binary(data[52:54]),
        approved_auth_amt=unpack_packed(data[54:60], 2),
        declined_auth_amt=unpack_packed(data[60:66], 2),
    )


def _decode_detail(acct_id: int, data: bytes) -> Detail:
    """Decode ``CIPAUDTY``: the 200 byte pending authorization detail segment."""
    auth_date_9c = int(unpack_packed(data[0:3]))
    auth_time_9c = int(unpack_packed(data[3:8]))
    return Detail(
        acct_id=acct_id,
        auth_date_9c=auth_date_9c,
        auth_time_9c=auth_time_9c,
        auth_key=f"{auth_date_9c:05d}{auth_time_9c:09d}",
        auth_orig_date=unpack_text(data[8:14]),
        auth_orig_time=unpack_text(data[14:20]),
        card_num=unpack_text(data[20:36]),
        auth_type=unpack_text(data[36:40]),
        card_expiry_date=unpack_text(data[40:44]),
        message_type=unpack_text(data[44:50]),
        message_source=unpack_text(data[50:56]),
        auth_id_code=unpack_text(data[56:62]),
        auth_resp_code=unpack_text(data[62:64]),
        auth_resp_reason=unpack_text(data[64:68]),
        processing_code=unpack_digits(data[68:74]),
        transaction_amt=unpack_packed(data[74:81], 2),
        approved_amt=unpack_packed(data[81:88], 2),
        merchant_category_code=unpack_text(data[88:92]),
        acqr_country_code=unpack_text(data[92:95]),
        pos_entry_mode=unpack_text(data[95:97]),
        merchant_id=unpack_text(data[97:112]),
        merchant_name=unpack_text(data[112:134]),
        merchant_city=unpack_text(data[134:147]),
        merchant_state=unpack_text(data[147:149]),
        merchant_zip=unpack_text(data[149:158]),
        transaction_id=unpack_text(data[158:173]),
        match_status=unpack_text(data[173:174]),
        auth_fraud=unpack_text(data[174:175]),
        fraud_rpt_date=unpack_text(data[175:183]),
    )


def decode_unload(path: pathlib.Path) -> tuple[list[Summary], list[Detail], list[str]]:
    """Decode the unload image into its summary and detail segments, in hierarchic order.

    Returns the summaries, the details and a list of anomalies: segments the supplied image
    carries that do not satisfy their own copybook layout. They are reported rather than
    normalised, because a blank key in a HIDAM root is a defect of the supplied sample data and
    not something the conversion may invent a value for (UCR-30).
    """
    raw = path.read_bytes()
    summaries: list[Summary] = []
    details: list[Detail] = []
    anomalies: list[str] = []
    offset = 0
    current_acct: int | None = None
    while offset < len(raw):
        record_length = int.from_bytes(raw[offset:offset + 2], "big")
        if record_length <= DATA_AT:
            raise UnloadFormatError(f"record at {offset} is too short: {record_length}")
        record = raw[offset:offset + record_length]
        record_start = offset
        offset += record_length
        name = unpack_text(record[SEGMENT_NAME_AT]).strip()
        if name not in (SUMMARY_SEGMENT, DETAIL_SEGMENT):
            # The first record of the unload is the DBD control record, which names the segments
            # rather than carrying one. It holds no data and is skipped.
            continue
        segment_length = int.from_bytes(record[SEGMENT_LENGTH_AT], "big")
        data = record[DATA_AT:DATA_AT + segment_length]
        if len(data) < segment_length:
            raise UnloadFormatError(f"truncated {name} segment at {offset}")
        if name == SUMMARY_SEGMENT:
            try:
                summary = _decode_summary(data)
            except UnloadFormatError as error:
                anomalies.append(f"{SUMMARY_SEGMENT} record at offset {record_start}: {error}")
                current_acct = None
                continue
            summaries.append(summary)
            current_acct = summary.acct_id
        elif name == DETAIL_SEGMENT:
            if current_acct is None:
                anomalies.append(f"{DETAIL_SEGMENT} record at offset {record_start} has no root")
                continue
            details.append(_decode_detail(current_acct, data))
    return summaries, details, anomalies


def default_unload_path(repo_root: pathlib.Path) -> pathlib.Path:
    """Return the supplied unload image inside ``repo_root``."""
    return (repo_root / "app" / "app-authorization-ims-db2-mq" / "data" / "EBCDIC"
            / "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat")
