-- Card Demo transaction schema.
--
-- Replaces the VSAM clusters of the transaction bounded context, defined by app/jcl/TRANFILE.jcl,
-- TRANTYPE.jcl, TRANCATG.jcl, TCATBALF.jcl and DISCGRP.jcl. One table per legacy record layout:
--   TRANSACT (KEYS(16 0), RECORDSIZE(350 350)) -> transaction                  (CVTRA05Y.cpy)
--   TRANTYPE (KEYS(2 0),  RECORDSIZE(60 60))   -> transaction_type             (CVTRA03Y.cpy)
--   TRANCATG (KEYS(6 0),  RECORDSIZE(60 60))   -> transaction_category         (CVTRA04Y.cpy)
--   TCATBALF (KEYS(17 0), RECORDSIZE(50 50))   -> transaction_category_balance (CVTRA01Y.cpy)
--   DISCGRP  (KEYS(16 0), RECORDSIZE(50 50))   -> disclosure_group             (CVTRA02Y.cpy)
--   DALYREJS (LRECL 430 PS)                    -> daily_transaction_reject     (CBTRN02C)
--
-- TRAN-ID is X(16), not a number: COTRN02C computes the next id by reading the highest key and
-- adding one, and the browse of COTRN00C is in character key order, so the column stays CHAR(16)
-- and the generated ids are zero filled to keep character order and numeric order the same.
-- S9(09)V99 amounts become NUMERIC(11,2) and S9(04)V99 rates NUMERIC(6,2), which keep the exact
-- decimal precision, scale and sign of the source fields; the timestamps stay CHAR(26) because
-- the records hold the DB2 format text the programs compare and rewrite as text.

CREATE TABLE transaction (
    tran_id             CHAR(16)      NOT NULL,
    tran_type_cd        CHAR(2)       NOT NULL,
    tran_cat_cd         INTEGER       NOT NULL,
    tran_source         CHAR(10)      NOT NULL,
    tran_desc           CHAR(100)     NOT NULL,
    tran_amt            NUMERIC(11,2) NOT NULL,
    tran_merchant_id    BIGINT        NOT NULL,
    tran_merchant_name  CHAR(50)      NOT NULL,
    tran_merchant_city  CHAR(50)      NOT NULL,
    tran_merchant_zip   CHAR(10)      NOT NULL,
    tran_card_num       CHAR(16)      NOT NULL,
    tran_orig_ts        CHAR(26)      NOT NULL,
    tran_proc_ts        CHAR(26)      NOT NULL,
    CONSTRAINT pk_transaction PRIMARY KEY (tran_id),
    CONSTRAINT ck_transaction_cat_digits CHECK (tran_cat_cd BETWEEN 0 AND 9999),
    CONSTRAINT ck_transaction_merchant_digits CHECK (tran_merchant_id BETWEEN 0 AND 999999999)
);

COMMENT ON TABLE transaction IS 'TRANSACT KSDS - app/cpy/CVTRA05Y.cpy TRAN-RECORD';

-- The TRANSACT alternate index of app/jcl/TRANFILE.jcl, KEYS(26 304), which is TRAN-PROC-TS.
-- CBTRN03C and the report request screen select on the processing date, which is its first ten
-- characters, so the index carries the card number as well and serves both reads.
CREATE INDEX ix_transaction_proc_ts ON transaction (tran_proc_ts, tran_card_num);
CREATE INDEX ix_transaction_card_num ON transaction (tran_card_num, tran_id);

CREATE TABLE transaction_type (
    tran_type       CHAR(2)  NOT NULL,
    tran_type_desc  CHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_type PRIMARY KEY (tran_type)
);

COMMENT ON TABLE transaction_type IS 'TRANTYPE KSDS - app/cpy/CVTRA03Y.cpy TRAN-TYPE-RECORD';

CREATE TABLE transaction_category (
    tran_type_cd        CHAR(2)  NOT NULL,
    tran_cat_cd         INTEGER  NOT NULL,
    tran_cat_type_desc  CHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_category PRIMARY KEY (tran_type_cd, tran_cat_cd),
    CONSTRAINT ck_transaction_category_digits CHECK (tran_cat_cd BETWEEN 0 AND 9999)
);

COMMENT ON TABLE transaction_category IS 'TRANCATG KSDS - app/cpy/CVTRA04Y.cpy TRAN-CAT-RECORD';

CREATE TABLE transaction_category_balance (
    trancat_acct_id  BIGINT        NOT NULL,
    trancat_type_cd  CHAR(2)       NOT NULL,
    trancat_cd       INTEGER       NOT NULL,
    tran_cat_bal     NUMERIC(11,2) NOT NULL,
    CONSTRAINT pk_transaction_category_balance
        PRIMARY KEY (trancat_acct_id, trancat_type_cd, trancat_cd),
    CONSTRAINT ck_tcatbal_acct_digits CHECK (trancat_acct_id BETWEEN 0 AND 99999999999),
    CONSTRAINT ck_tcatbal_cat_digits CHECK (trancat_cd BETWEEN 0 AND 9999)
);

COMMENT ON TABLE transaction_category_balance IS
    'TCATBALF KSDS - app/cpy/CVTRA01Y.cpy TRAN-CAT-BAL-RECORD';

CREATE TABLE disclosure_group (
    dis_acct_group_id  CHAR(10)     NOT NULL,
    dis_tran_type_cd   CHAR(2)      NOT NULL,
    dis_tran_cat_cd    INTEGER      NOT NULL,
    dis_int_rate       NUMERIC(6,2) NOT NULL,
    CONSTRAINT pk_disclosure_group
        PRIMARY KEY (dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd),
    CONSTRAINT ck_discgrp_cat_digits CHECK (dis_tran_cat_cd BETWEEN 0 AND 9999)
);

COMMENT ON TABLE disclosure_group IS 'DISCGRP KSDS - app/cpy/CVTRA02Y.cpy DIS-GROUP-RECORD';

-- DALYREJS, the generation data set CBTRN02C writes a rejected daily transaction to: the 350 byte
-- input record followed by the 80 byte validation trailer. The job still writes that file; the
-- table holds the same records so a rejected transaction can be looked up rather than only read
-- off a data set, and the sequence column preserves the order the records were written in.
CREATE TABLE daily_transaction_reject (
    reject_seq                   BIGSERIAL   NOT NULL,
    dalytran_id                  CHAR(16)    NOT NULL,
    reject_tran_data             CHAR(350)   NOT NULL,
    validation_fail_reason       INTEGER     NOT NULL,
    validation_fail_reason_desc  CHAR(76)    NOT NULL,
    rejected_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_daily_transaction_reject PRIMARY KEY (reject_seq)
);

COMMENT ON TABLE daily_transaction_reject IS
    'DALYREJS GDS - CBTRN02C REJECT-RECORD (350 byte data + 80 byte trailer)';

-- COBIL00C had one unit of recovery for its ACCTDAT rewrite and its TRANSACT write. Here the
-- account rewrite is a call to the account service, so the payment is recorded before the call
-- and the outcome afterwards: the key makes the account call idempotent and the row makes a
-- resubmission of the same payment - the double posting risk of a non idempotent screen - visible
-- instead of silent.
CREATE TABLE bill_payment (
    idempotency_key  VARCHAR(64)   NOT NULL,
    acct_id          BIGINT        NOT NULL,
    tran_id          CHAR(16)      NOT NULL,
    amount           NUMERIC(11,2) NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    requested_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_bill_payment PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_bill_payment_transaction FOREIGN KEY (tran_id) REFERENCES transaction (tran_id),
    CONSTRAINT ck_bill_payment_status CHECK (status IN ('POSTED', 'COMPENSATED'))
);

COMMENT ON TABLE bill_payment IS 'Idempotency record for COBIL00C; no legacy equivalent';

-- CORPT00C wrote a job card to the TDQ 'JOBS' so that the internal reader ran TRANREPT. There is
-- no internal reader here: the request is recorded and the transaction report job is launched
-- with the same date range, and the row is what the screen reports back on.
CREATE TABLE report_request (
    request_id     BIGSERIAL   NOT NULL,
    report_name    VARCHAR(20) NOT NULL,
    start_date     CHAR(10)    NOT NULL,
    end_date       CHAR(10)    NOT NULL,
    requested_by   VARCHAR(8)  NOT NULL,
    requested_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_report_request PRIMARY KEY (request_id),
    CONSTRAINT ck_report_request_name CHECK (report_name IN ('Monthly', 'Yearly', 'Custom'))
);

COMMENT ON TABLE report_request IS 'CORPT00C TDQ JOBS submission - app/jcl/TRANREPT.jcl';
