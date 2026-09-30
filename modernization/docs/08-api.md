# API Documentation — CICS Transactions to REST

Three contracts, one per service (OpenAPI 3.1.0), all validated with Redocly
(`evidence/openapi-lint.log`, configuration in `modernization/redocly.yaml`):

| Contract | Service | Swagger UI |
|---|---|---|
| `account-service/src/main/resources/openapi/openapi.yaml` | account management | `http://localhost:8080/swagger-ui.html` |
| `identity-service/src/main/resources/openapi/openapi.yaml` | signed-on identity and user administration | `http://localhost:8081/swagger-ui.html` |
| `authorization-service/src/main/resources/openapi/openapi.yaml` | pending authorizations, details and fraud marking (§6) | `http://localhost:8082/swagger-ui.html` |

All three are reached through the gateway on `http://localhost:8090` in normal use; the direct ports exist
for the service's own Swagger UI and health endpoints. Every operation requires
`Authorization: Bearer <token>`, a token the `carddemo` realm signed after both factors; there is no
unauthenticated operation in the default mode. `POST /api/signon` is mapped only under
`carddemo.security.mode=legacy` and is documented as such in the identity contract.

§1-§5 cover the account and identity contracts; §6 covers the authorization contract and the two
cardholder-lookup operations the authorization context calls.

## 1. Operation map — account and identity

| Source action | Program / paragraph | Operation | Success | Business errors |
|---|---|---|---|---|
| Start CAVW with no account | `0000-MAIN` (`EIBCALEN = 0`) | `GET /api/accounts/view` `promptAccountView` | 200, prompt message | — |
| Enter an account on CAVW | `2200-EDIT-MAP-INPUTS` → `9000-READ-ACCT` | `GET /api/accounts/{accountId}` `viewAccount` | 200, account + customer | 400 invalid filter, 404 not found in the cross-reference, account or customer file, 500 fatal file status |
| Start CAUP for an account | `9000-READ-ACCT` → `9500-STORE-FETCHED-DATA` | `GET /api/accounts/{accountId}/update` `fetchAccountForUpdate` | 200 `DETAILS_FETCHED` with the editable values and the baseline snapshot | 400, 404, 500 as above |
| Press Enter on CAUP with keyed values | `1200-EDIT-MAP-INPUTS`, `1205-COMPARE-OLD-NEW` | `POST /api/accounts/{accountId}/update/validate` `validateAccountUpdate` | 200 `NO_CHANGES` or `VALIDATION_ERROR` or `CHANGES_VALIDATED` | 404, 500 |
| Press F5 to save | `2000-DECIDE-ACTION` → `9600-WRITE-PROCESSING` | `POST /api/accounts/{accountId}/update/confirm` `confirmAccountUpdate` | 200 `CHANGES_COMMITTED` | 200 `RECORD_CHANGED` (concurrency), 200 `VALIDATION_ERROR`, 409 lock conflict, 500 rewrite failure |
| Select a menu option | `COMEN01C` option table | `GET /api/menu` `menu` | 200, the options the signed-on operator type may use | 403 admin-only option |
| Sign on | `COSGN00C` `PROCESS-ENTER-KEY`, `READ-USER-SEC-FILE` | the realm authenticates (password + TOTP); `GET /api/me` `me` then reports the USRSEC row behind the token | 200, the user id, the names, the operator type and the `XCTL` target as `nextScreen` | 401 no or invalid token, 401 `User not found...` when the token names no USRSEC row, 500 any other file status |
| Sign on, legacy mode only | the same paragraphs | `POST /api/signon` `signOn` | 200, a self-issued token and the same fields | 400 missing user id or password, 401 unknown user or wrong password, 500 any other file status |
| Display the admin menu | `COADM01C` `SEND-MENU-SCREEN`, `COADM02Y` | `GET /api/admin-menu` `adminMenu` | 200, the six options | 403 not an administrator |
| Select an admin option | `COADM01C` `PROCESS-ENTER-KEY` | `GET /api/admin-menu/selection?option=` `selectAdminMenuOption` | 200, the program the option starts | 400 `Please enter a valid option number...` for a non-numeric, zero or out-of-range option — one message for all three, as the source has |
| List users, page forward, page back | `COUSR00C` `PROCESS-PAGE-FORWARD`/`-BACKWARD` | `GET /api/users?userId&direction` `listUsers` | 200, ten rows and the position message | 500 browse failure |
| Fetch a user to update or delete | `COUSR02C`/`COUSR03C` `READ-USER-SEC-FILE` | `GET /api/users/{userId}` `getUser` | 200, the stored record | 404 `User ID NOT found...` |
| Add a user | `COUSR01C` `PROCESS-ENTER-KEY`, `WRITE-USER-SEC-FILE` | `POST /api/users` `addUser` | 201, `User <id> has been added ...` | 400 a missing field, 409 `User ID already exist...`, 500 write failure |
| Update a user | `COUSR02C` `UPDATE-USER-SEC-FILE` | `PUT /api/users/{userId}` `updateUser` | 200, `User <id> has been updated ...` | 400 a missing field or `Please modify to update ...`, 404, 500 |
| Delete a user | `COUSR03C` `DELETE-USER-SEC-FILE` | `DELETE /api/users/{userId}` `deleteUser` | 200, `User <id> has been deleted ...` | 404, 500 (with the source's `Unable to Update User...` wording, UCR-16) |

The account contract also carries `GET /api/accounts/search` (`searchAccount`) and
`GET /api/accounts/update` (`fetchAccountForUpdateKeyed`), which are the keyed forms of the two screens
— the operator keying an account filter rather than following a link.

`GET`/`POST` split follows the effect on stored data: only `confirm` writes. `validate` is a `POST`
because the keyed values plus the fetched baseline form a body too large and too structured for a
query string, not because it mutates anything.

## 2. Conversation state without COMMAREA

The source carried `ACUP-*` state and the `ACUP-OLD-*` baseline in COMMAREA between pseudo-
conversational turns. The API makes both explicit:

```
AccountUpdateRequest {
  original: AccountUpdateForm   # the values as they were read  (the ACUP-OLD- group)
  updated:  AccountUpdateForm   # the values the operator typed (the ACUP-NEW- group)
}
AccountUpdateResponse {
  status:       AccountUpdateStatus
  infoMessage:  string            # INFOMSG line
  errorMessage: string            # ERRMSG line - the single message the source would have shown
  original:     AccountUpdateForm # values to redisplay, per 3201/3202/3203
  updated:      AccountUpdateForm
  details:      AccountDetails    # read-only account and customer values
  fieldFlags:   { field: FieldFlag }  # what the BMS attribute bytes carried
}
```

Sending both forms reproduces the two comparisons the source makes: keyed against fetched for "no
change detected" (`1205-COMPARE-OLD-NEW`), and fetched against stored for the record-changed check
(`9700-CHECK-CHANGE-IN-REC`).

`AccountUpdateStatus` values and their source counterparts:

| Status | Source state |
|---|---|
| `DETAILS_FETCHED` | `ACUP-DETAILS-FETCHED` after `9500-STORE-FETCHED-DATA` |
| `NO_CHANGES` | `NO-CHANGES-FOUND` in `1205-COMPARE-OLD-NEW` |
| `VALIDATION_ERROR` | `INPUT-ERROR` after `1200-EDIT-MAP-INPUTS` |
| `CHANGES_VALIDATED` | `ACUP-CHANGES-OK-NOT-CONFIRMED` |
| `CHANGES_COMMITTED` | `ACUP-CHANGES-OKAYED-AND-DONE` |
| `RECORD_CHANGED` | `9700-CHECK-CHANGE-IN-REC` detecting a difference |
| `LOCK_ERROR` | `READ UPDATE` failing with a non-normal response |
| `UPDATE_FAILED` | `REWRITE` failing, after the rollback |

The client sends back the `original` block it received; that is what the concurrency check compares
against the stored record, exactly as the source compared `ACUP-OLD-*` against the freshly read
record.

## 3. Error contract

`ApiExceptionHandler` maps the exception taxonomy onto one `ErrorResponse` shape
(`{ status, message, fieldFlags }`), preserving the source message text verbatim. `fieldFlags` names
the fields the screen would have highlighted and how (`NOT_OK`, `BLANK`, `OK`):

| Exception | HTTP | Example message |
|---|---|---|
| `ScreenValidationException` | 400 | `Account Filter must  be a non-zero 11 digit number` (double space is the source's) |
| `RecordNotFoundException` | 404 | `Account:99999999999 not found in Cross ref file.` |
| `AdminOnlyOptionException` | 403 | admin-only option refused for a regular operator |
| `UpdateFailedException` | 409 / 500 | lock conflict or rewrite failure |
| `StorageAccessException` | 500 | the `WS-FILE-ERROR-MESSAGE` text of the failing read, i.e. the abend path |
| `BusinessRuleException` | 400 | any other rule rejection |

The identity service maps the same taxonomy onto the same shape, plus two of its own:

| Exception | HTTP | Example message |
|---|---|---|
| `ScreenValidationException` | 400 | `Please enter User ID ...`, `Please modify to update ...`, `Please enter a valid option number...` |
| `SignOnFailedException` | 401 | `User not found. Try again ...` or `Wrong Password. Try again ...` |
| `RecordNotFoundException` | 404 | `User ID NOT found...` |
| `DuplicateUserException` | 409 | `User ID already exist...` |
| `UpdateFailedException` | 500 | `Unable to Update User...` — including the wrong wording after a failed delete (UCR-16) |
| `StorageAccessException` | 500 | `Unable to lookup User...` |

A missing token is 401 and a token without `ROLE_ADMIN` on a user endpoint is 403; neither has a
message from the source, because the source made those transactions unreachable rather than refusing
them.

## 4. Authentication and the operator type

The realm performs the sign-on: it asks for the password and a TOTP code and signs an RS256 token
(`25-authentication-and-mfa-architecture.md`). What `COSGN00C` did that the realm cannot is read USRSEC,
so `GET /api/me` does it: `preferred_username` folded to upper case is the eight-character key, the row
gives the names and `SEC-USR-TYPE` (`A` administrator, anything else regular), and `nextScreen` is the
program the source would have transferred to — `COADM01C` for an administrator, `COMEN01C` otherwise.
The password is never sent to a CardDemo component, and nothing compares `SEC-USR-PWD`.

Under `carddemo.security.mode=legacy`, kept for the parity evidence and the rollback path,
`POST /api/signon` applies the source's rules in full — both values folded to upper case, USRSEC read by
the key, the password compared, an unknown user reported differently from a wrong password — and issues
the previous self-issued token.

Every operation requires a token. The operator type is read from the verified token, never
from a request parameter or header, so a caller cannot claim to be an administrator:
`SignedOnUser.userType(authentication)` derives it from the granted authorities and
`MenuAccessService` then applies the admin-only rule of `COMEN01C`. The user-administration endpoints
require `ROLE_ADMIN` outright, because in the source those transactions were only reachable from the
admin menu.

The credential rules themselves are the source's and are **not** production-grade — clear-text
passwords, eight characters, no lock-out, and two messages that reveal whether a user id exists. That
is registered as UCR-14 and must be reviewed before any production use.

## 5. Contract tests

`AccountApiIntegrationTest` drives every account operation through `MockMvc` against the local
PostgreSQL database with a signed token, asserting status codes, message text, `fieldFlags` and the
persisted result — including the stale-snapshot conflict and the ordered account-then-customer write.
`IdentityApiIntegrationTest` does the same for sign-on, the admin menu and the four user operations,
asserting the legacy message text, the token claims, the ten-row pages and that a regular operator's
token is refused on the user endpoints. `evidence/api-smoke.log` captures the same operations against
the running jars.

## 6. The authorization contract

The third contract is `authorization-service/src/main/resources/openapi/openapi.yaml`, Swagger UI on
`http://localhost:8082/swagger-ui.html`, reached through the gateway on `:8090` in normal use. It is
validated by the same Redocly run (`evidence/openapi-lint.log`). Every operation requires
`Authorization: Bearer <token>`.

| Source action | Program / paragraph | Operation | Success | Business errors |
|---|---|---|---|---|
| Start CPVS with no account | `COPAUS0C` `MAIN-PARA` (`EIBCALEN = 0`) | `GET /api/authorizations` `authorizationSummary` (no `accountId`) | 400 `Please enter Account ID...` | — |
| Enter an account on CPVS | `COPAUS0C` `EDIT-ACCT-ID`, `GATHER-ACCOUNT-DETAILS`, `POPULATE-AUTH-LIST` | `GET /api/authorizations?accountId=…` | 200, the account and customer header, the summary totals and up to five rows | 400 non-numeric account, 404 unknown account or card, 500 read failure |
| PF8 on CPVS | `PROCESS-PAGE-FORWARD` | `GET /api/authorizations?accountId=…&afterAuthKey=…` | 200, the next five rows | 200 with `You are already at the bottom of the page...` when none follow |
| PF7 on CPVS | `PROCESS-PAGE-BACKWARD` | the same operation with the previous page's first key | 200, the previous rows | 200 with `You are already at the top of the page...` on the first page |
| `S` in a row on CPVS | `PROCESS-ENTER-KEY` → `XCTL` to `COPAUS1C` | `GET /api/authorizations/{accountId}/{authKey}` `authorizationDetail` | 200, the full detail | 400 `Invalid selection. Valid value is S`, 404 unknown authorization |
| Start CPVD | `COPAUS1C` `MAIN-PARA`, `READ-AUTH-DETAIL` | the same operation | 200 | 400 a non-numeric account or a malformed key, 404, 500 |
| PF8 on CPVD | `GET-NEXT-AUTH-DETAIL` | the detail operation again, with the `nextAuthKey` the previous response carried | 200, the next detail of the account | 200 with `No more authorizations for this account` and `nextAuthKey` absent at the end of the chain |
| PF5 on CPVD | `COPAUS1C` fraud toggle → `COPAUS2C` `INSERT-FRAUD-REPORT` | `POST /api/authorizations/{accountId}/{authKey}/fraud` `toggleAuthorizationFraud` | 200, the detail with the new fraud state and its message | 404 unknown authorization, 500 write failure |
| An acquirer authorization request | `COPAUA0C` (MQ-triggered) | **not REST** — the Kafka topic `carddemo.authorization.request.v1`, reply on `carddemo.authorization.reply.v1` | reply record with `00`/`05` and the approved amount | malformed request → dead-letter topic; cardholder lookup failure → bounded retry then dead-letter |
| The expired-authorization purge | `CBPAUP0J` + `CBPAUP0C` | **not REST** — the `purgeAuthorizationsJob` in `batch-auth-purge` | job `COMPLETED`, totals read and deleted | return code 16 on a read, delete or checkpoint failure |

`COPAUA0C` has no REST surface on purpose: it is asynchronous request/reply in the source and stays
asynchronous in the target (`23-messaging-modernization-mapping.md`). Exposing it as a synchronous
endpoint would change the interaction semantics the acquirer integration depends on.

### 6.1 Cardholder lookup on the account contract

Two operations are added to the *account* contract, not the authorization one, because the account
context owns the data:

| Operation | Source paragraphs it replaces | Used by |
|---|---|---|
| `GET /api/cardholders/by-card/{cardNumber}` `cardholderByCard` | `COPAUA0C` `9600-READ-CARDXREF` and the account and customer reads that follow it | the Kafka request path |
| `GET /api/cardholders/by-account/{accountId}` `cardholderByAccount` | `COPAUS0C` `GATHER-ACCOUNT-DETAILS` | the CPVS summary screen |

Both return the account id, customer id, account active status, credit and cash limits, the current
balance, the customer name parts, address lines, state, zip and phone — the fields the source read
from `CARDXREF`, `ACCTDAT` and `CUSTDAT`, and nothing more. They also return `cardFound`,
`accountFound` and `customerFound`, which are `WS-XREF-READ-FLG`, `WS-ACCT-MASTER-READ-FLG` and
`WS-CUST-MASTER-READ-FLG` of the source: a missing record is a documented business outcome (the
`3100 INVALID CARD` decline, or the zero-metric summary display), so it is signalled as a flag on a
200 rather than as a 404. A transport or server failure is different in kind — it raises
`CardholderLookupException`, which the listener retries and the screen reports as a system error —
because deciding an authorization on absent data would change the outcome.

### 6.2 Paging without the IMS position

CPVS paged by holding the IMS database position and the first and last keys of the displayed page in
working storage. The API is keyset-paged instead: the client sends the last key it displayed as
`afterAuthKey` and the server returns the following rows. The page size stays five, the ordering stays
the source's complement-key ordering (newest first), and the two boundary messages are returned
verbatim, so the operator sees the same behaviour without any server-side cursor.

### 6.3 Contract tests

`AuthorizationApiIntegrationTest` drives every authorization operation through `MockMvc` against the
local PostgreSQL database with a signed token: the numeric account edit, the five-row page, both
boundary messages, the selection edit, the detail fields and their `MM/DD/YY` / `HH:MM:SS` / `MM/YY`
editing, the decline-reason text, next-authorization navigation and the end-of-chain message, and both
directions of the fraud toggle with the persisted `auth_fraud` row. PF8 needs no separate operation:
the detail response carries `nextAuthKey`, so the client re-reads the detail operation with it and
shows the end-of-chain message when the field is absent. `CardholderLookupClientTest`
covers the cross-context call, including the 404-means-decline and error-means-retry distinction.
