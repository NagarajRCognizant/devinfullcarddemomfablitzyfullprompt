-- The link between a USRSEC record and the realm account that now signs the user on.
--
-- The record keeps every field of app/cpy/CSUSR01Y.cpy, including SEC-USR-PWD, because the business
-- context reports SEC-USR-TYPE and the batch reports the record as the source file held it; the
-- password in that column is no longer what a sign-on compares, the realm credential is.
--
-- Nullable, because the ten records shipped with the source file exist before any realm account does
-- and the reconciliation fills the column in; unique, because two records cannot share an account.

ALTER TABLE security_user ADD COLUMN keycloak_user_id VARCHAR(36);

ALTER TABLE security_user
    ADD CONSTRAINT uq_security_user_keycloak_user_id UNIQUE (keycloak_user_id);

COMMENT ON COLUMN security_user.keycloak_user_id IS
    'Immutable id of the Keycloak account of this user; null until the account is provisioned';
