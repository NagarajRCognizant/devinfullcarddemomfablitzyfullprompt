-- Authorization bounded context schema.
--
-- IMS database DBPAUTP0 is a HIDAM hierarchy: root segment PAUTSUM0 keyed by account id, child
-- segment PAUTDTL1 keyed by the nines complement of the authorization date and time, with index
-- database DBPAUTX0 supplying the primary index over the root key ACCNTID. The hierarchy becomes a
-- parent table and a child table with a foreign key; the index database needs no counterpart,
-- because PostgreSQL maintains the primary-key b-tree itself. The complemented key is kept as
-- stored character data, because ascending order on it is the newest-first order the summary screen
-- pages through and CBPAUP0C reverses it to recover the date.
--
-- DB2 table AUTHFRDS becomes auth_fraud in this database rather than a shared one: the fraud report
-- is written and read only by this context.
--
-- PIC S9(9)V99 COMP-3 style money fields map to numeric(11,2) / numeric(12,2), PIC X(n) to char(n)
-- so trailing-blank semantics survive, and PIC S9(4) COMP counters to smallint. The full mapping
-- table is in modernization/docs/05-data-mapping.md.

CREATE TABLE pending_auth_summary (
    acct_id           bigint         NOT NULL,
    cust_id           bigint         NOT NULL,
    auth_status       char(1),
    account_status    char(10),
    credit_limit      numeric(11, 2) NOT NULL DEFAULT 0,
    cash_limit        numeric(11, 2) NOT NULL DEFAULT 0,
    credit_balance    numeric(11, 2) NOT NULL DEFAULT 0,
    cash_balance      numeric(11, 2) NOT NULL DEFAULT 0,
    approved_auth_cnt smallint       NOT NULL DEFAULT 0,
    declined_auth_cnt smallint       NOT NULL DEFAULT 0,
    approved_auth_amt numeric(11, 2) NOT NULL DEFAULT 0,
    declined_auth_amt numeric(11, 2) NOT NULL DEFAULT 0,
    version           bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_pending_auth_summary PRIMARY KEY (acct_id)
);

COMMENT ON TABLE pending_auth_summary IS 'IMS segment PAUTSUM0 (copybook CIPAUSMY), root of HIDAM database DBPAUTP0';

CREATE TABLE pending_auth_detail (
    acct_id                bigint         NOT NULL,
    auth_key               char(14)       NOT NULL,
    auth_date_9c           integer        NOT NULL,
    auth_time_9c           bigint         NOT NULL,
    auth_orig_date         char(6),
    auth_orig_time         char(6),
    card_num               char(16)       NOT NULL,
    auth_type              char(4),
    card_expiry_date       char(4),
    message_type           char(6),
    message_source         char(6),
    auth_id_code           char(6),
    auth_resp_code         char(2),
    auth_resp_reason       char(4),
    processing_code        char(6),
    transaction_amt        numeric(12, 2) NOT NULL DEFAULT 0,
    approved_amt           numeric(12, 2) NOT NULL DEFAULT 0,
    merchant_category_code char(4),
    acqr_country_code      char(3),
    pos_entry_mode         char(2),
    merchant_id            char(15),
    merchant_name          char(22),
    merchant_city          char(13),
    merchant_state         char(2),
    merchant_zip           char(9),
    transaction_id         char(15),
    match_status           char(1),
    auth_fraud             char(1),
    fraud_rpt_date         char(8),
    CONSTRAINT pk_pending_auth_detail PRIMARY KEY (acct_id, auth_key),
    CONSTRAINT fk_pending_auth_detail_summary FOREIGN KEY (acct_id)
        REFERENCES pending_auth_summary (acct_id)
);

COMMENT ON TABLE pending_auth_detail IS 'IMS segment PAUTDTL1 (copybook CIPAUDTY), child of PAUTSUM0';

-- No source counterpart: the fraud table is keyed by card number and authorization timestamp, and
-- the migration reconciliation queries join details to fraud reports on that pair.
CREATE INDEX ix_pending_auth_detail_card ON pending_auth_detail (card_num, auth_key);

CREATE TABLE auth_fraud (
    card_num               char(16)       NOT NULL,
    auth_ts                timestamp      NOT NULL,
    auth_type              char(4),
    card_expiry_date       char(4),
    message_type           char(6),
    message_source         char(6),
    auth_id_code           char(6),
    auth_resp_code         char(2),
    auth_resp_reason       char(4),
    processing_code        char(6),
    transaction_amt        numeric(12, 2),
    approved_amt           numeric(12, 2),
    merchant_catagory_code char(4),
    acqr_country_code      char(3),
    pos_entry_mode         smallint,
    merchant_id            char(15),
    merchant_name          varchar(22),
    merchant_city          char(13),
    merchant_state         char(2),
    merchant_zip           char(9),
    transaction_id         char(15),
    match_status           char(1),
    auth_fraud             char(1),
    fraud_rpt_date         date,
    acct_id                numeric(11, 0),
    cust_id                numeric(9, 0),
    CONSTRAINT pk_auth_fraud PRIMARY KEY (card_num, auth_ts)
);

COMMENT ON TABLE auth_fraud IS 'DB2 table AUTHFRDS (DCLGEN in dcl/), primary key replaces index XAUTHFRD';

-- Idempotency store. The source relied on the MQ get inside the CICS syncpoint for once-only
-- processing; Kafka delivers at least once, so a decided request is recorded with the reply it
-- produced and a repeated delivery replays that reply instead of authorizing twice.
CREATE TABLE authorization_request_log (
    request_id     varchar(64)  NOT NULL,
    card_num       char(16)     NOT NULL,
    correlation_id varchar(64),
    acct_id        bigint,
    auth_key       char(14),
    reply_payload  varchar(120) NOT NULL,
    processed_at   timestamp    NOT NULL,
    CONSTRAINT pk_authorization_request_log PRIMARY KEY (request_id)
);

CREATE INDEX ix_authorization_request_log_card ON authorization_request_log (card_num, processed_at);

-- Transactional outbox for the authorization reply. Written in the same transaction as the
-- authorization, published to the reply topic afterwards, so an answer is never sent for an
-- authorization that was not stored.
CREATE TABLE authorization_reply_outbox (
    id             bigserial    NOT NULL,
    request_id     varchar(64)  NOT NULL,
    reply_topic    varchar(255) NOT NULL,
    message_key    varchar(32)  NOT NULL,
    correlation_id varchar(64),
    payload        varchar(120) NOT NULL,
    created_at     timestamp    NOT NULL,
    published_at   timestamp,
    attempts       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_authorization_reply_outbox PRIMARY KEY (id)
);

CREATE INDEX ix_authorization_reply_outbox_pending
    ON authorization_reply_outbox (id) WHERE published_at IS NULL;
