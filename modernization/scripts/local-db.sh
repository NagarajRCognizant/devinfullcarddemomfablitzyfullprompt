#!/usr/bin/env bash
# Creates the local PostgreSQL role and the eight databases the services and their tests use.
#
# The account service owns the account, customer and card cross reference tables (the converted
# ACCTDAT, CUSTDAT and CARDXREF clusters) and the identity service owns the converted USRSEC
# cluster, and the authorization service owns the converted PAUTSUM0/PAUTDTL1 hierarchy and the
# AUTHFRDS table, and the transaction service owns the converted TRANSACT, TRANTYPE, TRANCATG,
# TCATBALF and DISCGRP clusters, so each service has its own database and none reads another's
# tables.
#
# Every credential here is a throwaway local development value; deployments supply their own.
set -euo pipefail

DB_USER="${CARDDEMO_DB_USER:-carddemo}"
DB_PASSWORD="${CARDDEMO_DB_PASSWORD:-carddemo}"

psql_as_superuser() {
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "$1"
}

psql_as_superuser "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
  END IF;
END \$\$;"

for database in carddemo carddemo_test carddemo_identity carddemo_identity_test \
                carddemo_authorization carddemo_authorization_test \
                carddemo_transaction carddemo_transaction_test; do
  if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname = '${database}'" | grep -q 1; then
    psql_as_superuser "CREATE DATABASE ${database} OWNER ${DB_USER}"
  fi
done

echo "Ready: carddemo, carddemo_test, carddemo_identity, carddemo_identity_test," \
     "carddemo_authorization, carddemo_authorization_test, carddemo_transaction," \
     "carddemo_transaction_test"
