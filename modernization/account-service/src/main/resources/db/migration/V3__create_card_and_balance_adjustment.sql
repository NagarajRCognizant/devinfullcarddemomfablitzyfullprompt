-- CARDDAT, the file app/jcl/CARDFILE.jcl defines and loads, and the record of applied balance
-- changes that the distributed replacement of the CICS syncpoint needs.
--
--   CARDDAT (KEYS(16 0), RECORDSIZE(150 150)) -> card (app/cpy/CVACT02Y.cpy)
--
-- CARDAIX, the alternate index over CARD-ACCT-ID that COCRDLIC pages through, becomes the index
-- below; it is non unique because an account can hold several cards.

CREATE TABLE card (
    card_num             CHAR(16)  NOT NULL,
    card_acct_id         BIGINT    NOT NULL,
    card_cvv_cd          INTEGER   NOT NULL,
    card_embossed_name   CHAR(50)  NOT NULL,
    card_expiraion_date  CHAR(10)  NOT NULL,
    card_active_status   CHAR(1)   NOT NULL,
    CONSTRAINT pk_card PRIMARY KEY (card_num),
    CONSTRAINT ck_card_acct_digits CHECK (card_acct_id BETWEEN 0 AND 99999999999),
    CONSTRAINT ck_card_cvv_digits CHECK (card_cvv_cd BETWEEN 0 AND 999)
);

COMMENT ON TABLE card IS 'CARDDAT KSDS - app/cpy/CVACT02Y.cpy CARD-RECORD';
COMMENT ON COLUMN card.card_expiraion_date IS 'CARD-EXPIRAION-DATE, spelled as in the copybook';

CREATE INDEX ix_card_acct_id ON card (card_acct_id, card_num);

-- COBIL00C, CBTRN02C and CBACT04C rewrote ACCTDAT in the same unit of recovery as the TRANSACT
-- write. The transaction context now owns TRANSACT, so it asks for the balance change over HTTP
-- and can retry it. The key it sends is stored with the outcome, so a retry of a change that was
-- already applied returns the first result instead of moving the balance a second time.
CREATE TABLE balance_adjustment (
    idempotency_key      VARCHAR(64)   NOT NULL,
    acct_id              BIGINT        NOT NULL,
    adjustment_kind      VARCHAR(24)   NOT NULL,
    amount               NUMERIC(12,2) NOT NULL,
    resulting_balance    NUMERIC(12,2) NOT NULL,
    resulting_cyc_credit NUMERIC(12,2) NOT NULL,
    resulting_cyc_debit  NUMERIC(12,2) NOT NULL,
    applied_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_balance_adjustment PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_balance_adjustment_account FOREIGN KEY (acct_id) REFERENCES account (acct_id),
    CONSTRAINT ck_balance_adjustment_kind
        CHECK (adjustment_kind IN ('POSTING', 'BILL_PAYMENT', 'INTEREST_SETTLEMENT'))
);

COMMENT ON TABLE balance_adjustment IS
    'Idempotency record for the cross service balance change; no legacy equivalent';

CREATE INDEX ix_balance_adjustment_acct ON balance_adjustment (acct_id, applied_at);
