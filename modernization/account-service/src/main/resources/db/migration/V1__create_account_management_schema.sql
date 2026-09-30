-- Card Demo account management schema.
--
-- Replaces the VSAM KSDS clusters defined by app/jcl/ACCTFILE.jcl and listed in
-- app/catlg/LISTCAT.txt. One table per legacy record layout, so a record maps one to one onto a
-- row and the field lengths of the copybooks are enforced by the column definitions:
--   ACCTDAT  (KEYS(11 0), RECORDSIZE(300 300))  -> account   (app/cpy/CVACT01Y.cpy)
--   CUSTDAT  (KEYS(9 0),  RECORDSIZE(500 500))  -> customer  (app/cpy/CVCUS01Y.cpy)
--   CARDXREF (KEYS(16 0), RECORDSIZE(50 50))    -> card_xref (app/cpy/CVACT03Y.cpy)
--
-- Signed packed/display amounts (S9(10)V99) become NUMERIC(12,2), which keeps the exact decimal
-- precision, scale and sign of the source fields. The legacy dates are held as CHAR(10) because
-- the records store them as text and the programs read, compare and rewrite them as text,
-- including blank and zero filled values that a DATE column could not hold. The 9(9) and 9(11)
-- keys become BIGINT with a digit range check, which keeps every value the source keys allow.

CREATE TABLE account (
    acct_id             BIGINT        NOT NULL,
    active_status       CHAR(1)       NOT NULL,
    curr_bal            NUMERIC(12,2) NOT NULL,
    credit_limit        NUMERIC(12,2) NOT NULL,
    cash_credit_limit   NUMERIC(12,2) NOT NULL,
    open_date           CHAR(10)      NOT NULL,
    expiration_date     CHAR(10)      NOT NULL,
    reissue_date        CHAR(10)      NOT NULL,
    curr_cyc_credit     NUMERIC(12,2) NOT NULL,
    curr_cyc_debit      NUMERIC(12,2) NOT NULL,
    addr_zip            CHAR(10)      NOT NULL,
    group_id            CHAR(10)      NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (acct_id),
    CONSTRAINT ck_account_id_digits CHECK (acct_id BETWEEN 0 AND 99999999999)
);

COMMENT ON TABLE account IS 'ACCTDAT KSDS - app/cpy/CVACT01Y.cpy ACCOUNT-RECORD';
COMMENT ON COLUMN account.addr_zip IS
    'ACCT-ADDR-ZIP: present in the record layout but never read or written by COACTVWC/COACTUPC/CBACT01C';

CREATE TABLE customer (
    cust_id              BIGINT      NOT NULL,
    first_name           CHAR(25)    NOT NULL,
    middle_name          CHAR(25)    NOT NULL,
    last_name            CHAR(25)    NOT NULL,
    addr_line_1          CHAR(50)    NOT NULL,
    addr_line_2          CHAR(50)    NOT NULL,
    addr_line_3          CHAR(50)    NOT NULL,
    addr_state_cd        CHAR(2)     NOT NULL,
    addr_country_cd      CHAR(3)     NOT NULL,
    addr_zip             CHAR(10)    NOT NULL,
    phone_num_1          CHAR(15)    NOT NULL,
    phone_num_2          CHAR(15)    NOT NULL,
    ssn                  CHAR(9)     NOT NULL,
    govt_issued_id       CHAR(20)    NOT NULL,
    dob_yyyy_mm_dd       CHAR(10)    NOT NULL,
    eft_account_id       CHAR(10)    NOT NULL,
    pri_card_holder_ind  CHAR(1)     NOT NULL,
    fico_credit_score    INTEGER     NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (cust_id),
    CONSTRAINT ck_customer_id_digits CHECK (cust_id BETWEEN 0 AND 999999999),
    CONSTRAINT ck_customer_fico_digits CHECK (fico_credit_score BETWEEN 0 AND 999)
);

COMMENT ON TABLE customer IS 'CUSTDAT KSDS - app/cpy/CVCUS01Y.cpy CUSTOMER-RECORD';
COMMENT ON COLUMN customer.addr_line_3 IS 'CUST-ADDR-LINE-3 holds the city on both maps';

CREATE TABLE card_xref (
    xref_card_num  CHAR(16)   NOT NULL,
    xref_cust_id   BIGINT     NOT NULL,
    xref_acct_id   BIGINT     NOT NULL,
    CONSTRAINT pk_card_xref PRIMARY KEY (xref_card_num),
    CONSTRAINT ck_card_xref_cust_digits CHECK (xref_cust_id BETWEEN 0 AND 999999999),
    CONSTRAINT ck_card_xref_acct_digits CHECK (xref_acct_id BETWEEN 0 AND 99999999999)
);

COMMENT ON TABLE card_xref IS 'CARDXREF KSDS - app/cpy/CVACT03Y.cpy CARD-XREF-RECORD';

-- CXACAIX, the alternate index over CARDXREF keyed on XREF-ACCT-ID that both online programs
-- use to get from an account number to the owning customer. Non unique, because an account can
-- have several cards, and the programs take the first record of the key.
CREATE INDEX ix_card_xref_acct_id ON card_xref (xref_acct_id, xref_card_num);
