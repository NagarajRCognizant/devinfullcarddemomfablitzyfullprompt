# Execution Plan and Ticket Decomposition

Two epics have been executed on this branch: **CDM-0** (account management and user administration)
and **CDM-100** (credit card authorizations).

Epic **CDM-0 — Modernise Credit Card Account Management (COBOL/CICS/VSAM → local Java)**
Scope: CICS transactions CAVW (COACTVWC) and CAUP (COACTUPC), batch job READACCT (CBACT01C), the
ACCT/CUSTOMER/CARDXREF stores and their IDCAMS definitions, and the menu access rules that gate the
two transactions. Outcome: a locally buildable, startable and testable Spring Boot + React
application with functional parity evidence. Not in scope: cloud deployment, production identity,
the other nine menu capabilities.

All tickets below were executed in this run; the status column is the end state.

| ID | Title | Description | Depends on | Acceptance criteria | Deliverables | Status |
|---|---|---|---|---|---|---|
| CDM-1 | Source discovery and inventory | Enumerate `app/`, resolve the transitive dependency closure of the four seeds, classify every copybook. | — | Every artifact inventoried and classified; closure documented | `02-source-inventory-and-coverage.md`, `03-discovery-and-dependency-map.md` | Done |
| CDM-2 | Source coverage validation | Report analysed/excluded/missing artifacts and coverage by type. | CDM-1 | Coverage % per type; every exclusion carries impact | §3-4 of `02-…` | Done |
| CDM-3 | Business rule extraction — COACTVWC | Extract BR-nn with paragraph citations and risk flags. | CDM-1 | Every decision, edit, message and file-status path captured | `BRD-COACTVWC.md` (23 rules) | Done |
| CDM-4 | Business rule extraction — COACTUPC | As CDM-3, including all field edits, change detection, concurrency and write sequencing. | CDM-1 | 56 rules with citations | `BRD-COACTUPC.md` | Done |
| CDM-5 | Business rule extraction — CBACT01C + READACCT | Batch rules, record layouts, hard-coded values, file-status handling. | CDM-1 | 16 rules; all three layouts documented | `BRD-CBACT01C.md` | Done |
| CDM-6 | Business rule extraction — CSUTLDTC + CSUTLDPY | Date service contract and the date edit paragraphs. | CDM-1 | 6 service rules + BR-35..40 in COACTUPC | `BRD-CSUTLDTC.md` | Done |
| CDM-7 | Business rule extraction — menu access rules | Operator-type gating of the two in-scope options. | CDM-1 | Access rules extracted, unreachable branch recorded | `BRD-COMEN01C.md` | Done |
| CDM-8 | Consolidated rule catalogue and capability map | Cross-program catalogue and capability → FR → BR → paragraph map. | CDM-3..7 | Single catalogue, no orphan rules | `BRE-Report-CardDemo-AccountMgmt.md` | Done |
| CDM-9 | Target architecture and module decomposition | Account and identity bounded contexts behind a gateway, layering, module boundaries, transaction boundaries. | CDM-8 | Documented decomposition with rationale | `04-architecture.md` | Done |
| CDM-10 | Shared COBOL semantics module | Fixed-width text, zoned/packed decimal, numeric edits, date formatting. | CDM-9 | `com.carddemo.cobol` with unit tests | `CobolText`, `CobolNumeric`, `ZonedDecimalCodec`, `CobolRecordBuilder`, `CobdatftDateFormatter` | Done |
| CDM-11 | Domain model and reference data | Account/customer/xref domain records, lookup tables, message catalogue. | CDM-10 | Tables reproduce CSLKPCDY verbatim | `LookupTables`, `ScreenMessages` | Done |
| CDM-12 | PostgreSQL schema and seed load | Flyway V1 schema (ACCTFILE/CUSTFILE/XREFFILE equivalents), V2 seed from the ASCII sample data. | CDM-11 | Clean-database migration succeeds; 50/50/50 rows | `V1__…sql`, `V2__…sql`, `evidence/migration.log` | Done |
| CDM-13 | Persistence layer | Entities, repositories, pessimistic locking for read-for-update. | CDM-12 | Schema validation passes; lock queries in place | `persistence/**` | Done |
| CDM-14 | Account view service | XREF → ACCOUNT → CUSTOMER sequencing, messages, not-found and fatal paths. | CDM-13 | BR-01..23 of COACTVWC implemented | `AccountViewService`, `AccountReadService` | Done |
| CDM-15 | Account update validation | Field edits in source order, first-message-wins, per-field highlighting. | CDM-13 | BR-18..40 of COACTUPC implemented | `AccountUpdateValidator`, `FieldEditor`, `DateEditor` | Done |
| CDM-16 | Account update lifecycle | fetch → validate → confirm, change detection, locking, concurrency check, ordered writes, rollback. | CDM-15 | BR-41..53 implemented | `AccountUpdateService`, `AccountChangeDetector` | Done |
| CDM-17 | Operator-type access rule | Preserve the operator-type access rule, with the type taken from the verified sign-on token. | CDM-11 | Admin-only option refused for regular users | `MenuAccessService`, `MenuController` | Done |
| CDM-18 | OpenAPI 3.1 contract and REST layer | Contract-first operations for the two screens plus the menu. | CDM-14..17 | Spec validates; controllers match it | `openapi/openapi.yaml`, `api/**`, `evidence/openapi-lint.log` | Done |
| CDM-19 | Batch conversion of READACCT | Spring Batch job producing the 107 FB, 110 FB and 84 VB extracts. | CDM-13 | Byte-level layout tests pass; job COMPLETED | `batch/**`, `evidence/batch-readacct.log` | Done |
| CDM-20 | React UI — Account View | Field layout, validation messages, read-only fields. | CDM-18 | Screen reproduces COACTVW fields | `AccountViewScreen.tsx` | Done |
| CDM-21 | React UI — Account Update | Editable/read-only fields, highlighting, confirmation flow. | CDM-18 | Screen reproduces COACTUP fields and the F5 confirmation | `AccountUpdateScreen.tsx` | Done |
| CDM-22 | Test suite and coverage | Unit, service, repository, API, batch and data-conversion tests with JaCoCo gates. | CDM-14..21 | ≥80% line and branch on migrated logic | 172 backend tests, 10 frontend tests | Done |
| CDM-23 | Local build and execution evidence | Package, migrate, start, smoke, batch, frontend build. | CDM-22 | Evidence captured for each step | `evidence/*.log` | Done |
| CDM-24 | Docker and Docker Compose assets | Local packaging for PostgreSQL + app. | CDM-23 | Assets present; build attempted with evidence | `Dockerfile`, `docker-compose.yml`, `evidence/docker-build.log` | Blocked — registry rate limit (UCR-09) |
| CDM-25 | Data reconciliation | ASCII vs EBCDIC vs database, field by field. | CDM-12 | Zero unexpected differences | `tools/reconcile_sample_data.py`, `evidence/sample-data-reconciliation.log` | Done |
| CDM-26 | Consistency-model delta register | Record every changed atomicity/ordering/isolation/durability guarantee. | CDM-16, CDM-19 | Each delta accepted, compensated or REVIEW REQUIRED | `12-consistency-model-delta-register.md` | Done |
| CDM-27 | Unsupported construct register | Record every unsupported/ambiguous/missing construct. | CDM-1..21 | No construct silently ignored | `11-unsupported-construct-register.md` | Done |
| CDM-28 | Artifact disposition report | Exactly one disposition per artifact. | CDM-2 | 159/159 artifacts dispositioned | `13-artifact-disposition.md` | Done |
| CDM-29 | Traceability matrix | Ticket → BR → source location → target → test → result. | CDM-22 | Every rule traced to a test | `14-rtm.md` | Done |
| CDM-30 | Documentation set and README | Architecture, API, data, screens, batch, mapping, testing, local guide, limitations. | CDM-9..29 | Playbook §8 artifacts present | `docs/**`, `modernization/README.md` | Done |
| CDM-31 | Auto-fix log | Every failure, root cause, fix and re-run. | CDM-22, CDM-23 | Iterations recorded with outcomes | `16-auto-fix-log.md` | Done |
| CDM-32 | Delivery | Dedicated branch, commit, push, pull request. | CDM-30 | PR open against the default branch, not merged | branch `devin/1789363889-carddemo-account-java-modernization` | Done |
| CDM-33 | Manual-review backlog | Carry every REVIEW REQUIRED item forward. | CDM-27 | Each item has impact, action and confidence | `17-limitations-and-review-required.md` | Done |

## Epic CDM-100 — Modernise Credit Card Authorizations (COBOL/CICS/IMS DB/DB2/MQ → Java)

Scope: CICS transactions CP00 (`COPAUA0C`), CPVS (`COPAUS0C`) and CPVD (`COPAUS1C`), the DB2 fraud
writer `COPAUS2C`, batch job `CBPAUP0J` (`CBPAUP0C`), the IMS HIDAM database `DBPAUTP0` with its
index `DBPAUTX0` and segments `PAUTSUM0`/`PAUTDTL1`/`PAUTINDX`, the DB2 table `AUTHFRDS` with index
`XAUTHFRD`, the MQ request and reply queues, the BMS maps `COPAU00`/`COPAU01`, the load and unload
utilities `PAUDBLOD`/`PAUDBUNL`/`DBUNLDGS` and the supplied EBCDIC unload data. Outcome: a third
bounded context — `authorization-domain`, `authorization-service` and `batch-auth-purge` — owning
`carddemo_authorization`, driven by Kafka for the acquirer interface and by two React screens for the
inquiry transactions, delivered on the same branch and pull request as the account and identity work.
Not in scope: the capabilities still on the mainframe, a real MQ or multi-broker Kafka deployment,
production identity.

All tickets below were executed in this run; the status column is the end state.

| ID | Title | Description | Depends on | Acceptance criteria | Deliverables | Status |
|---|---|---|---|---|---|---|
| CDM-100 | Authorization source discovery and inventory | Enumerate `app/app-authorization-ims-db2-mq/`, resolve the closure of the five program seeds, classify every copybook, DBD, PSB, DDL, DCLGEN, JCL, BMS map and data file. | CDM-1 | Every artifact inventoried, classified and dispositioned | §5 of `02-source-inventory-and-coverage.md`, §8 of `13-artifact-disposition.md` | Done |
| CDM-101 | Business rule extraction — COPAUA0C | Extract BR-nn with paragraph citations and risk flags for the MQ-triggered decision program. | CDM-100 | Every decision, calculation, literal, file-status and abend path captured | `BRD-COPAUA0C.md` (38 rules) | Done |
| CDM-102 | Business rule extraction — COPAUS0C | Summary screen: account edit, gather, paging, selection. | CDM-100 | 30 rules with citations | `BRD-COPAUS0C.md` | Done |
| CDM-103 | Business rule extraction — COPAUS1C | Detail screen: display edits, decline-reason lookup, fraud toggle, chain navigation. | CDM-100 | 21 rules with citations | `BRD-COPAUS1C.md` | Done |
| CDM-104 | Business rule extraction — COPAUS2C | DB2 fraud report insert and its SQLCODE handling. | CDM-100 | 9 rules with citations | `BRD-COPAUS2C.md` | Done |
| CDM-105 | Business rule extraction — CBPAUP0C + CBPAUP0J | Purge parameters, expiry arithmetic, count and amount adjustment, checkpointing, return codes. | CDM-100 | 24 rules with citations | `BRD-CBPAUP0C.md` | Done |
| CDM-106 | Business rule extraction — load and unload utilities | `PAUDBLOD`, `PAUDBUNL`, `DBUNLDGS` record handling and skip behaviour. | CDM-100 | 13 rules with citations | `BRD-PAUDBLOD-PAUDBUNL-DBUNLDGS.md` | Done |
| CDM-107 | Consolidated authorization rule catalogue | Cross-program catalogue and capability → FR → BR → paragraph map for the authorization closure. | CDM-101..106 | 135 rules, no orphans | `BRE-Report-CARDDEMO-AUTHORIZATION.md` | Done |
| CDM-108 | Authorization bounded context and module decomposition | Third context, its database ownership, the cross-context cardholder read, module split between domain, service and batch. | CDM-107 | Documented decomposition with rationale | §7 of `04-architecture.md` | Done |
| CDM-109 | IMS/DB2 to PostgreSQL data model | Parent/child mapping of `PAUTSUM0`/`PAUTDTL1`, `AUTHFRDS` to `auth_fraud`, type mapping tables, complement-key ordering, request log and reply outbox. | CDM-108 | Clean-database migration succeeds | `authorization-service/.../db/migration/V1__…sql`, §6-§9 of `05-data-mapping.md` | Done |
| CDM-110 | EBCDIC unload decode and seed migration | Decode the supplied `PAUTSUM0`/`PAUTDTL1` unload (packed decimal, zoned, fixed-width, LOW-VALUES) and generate the seed migration. | CDM-109 | 21 roots and 202 children loaded; anomalies reported not skipped silently | `tools/ims_authorization_unload.py`, `V2__…sql`, UCR-31, UCR-32 | Done |
| CDM-111 | Authorization data reconciliation | Compare the decoded source records with the loaded database field by field. | CDM-110 | Zero unexplained differences | `tools/reconcile_authorization_data.py`, `evidence/authorization-data-reconciliation.txt` | Done |
| CDM-112 | Authorization decision engine | The source decision table as a pure function: approval default, available credit, fallbacks, decline reasons, response codes, key complements, match status. | CDM-109 | BR-01..38 of COPAUA0C implemented where they are decision logic | `AuthorizationDecisionEngine`, `DeclineReason`, `AuthorizationKey` | Done |
| CDM-113 | Kafka request/reply implementation | Consumer, processor and reply publisher with card-number partitioning, correlation and reply-to headers, bounded retry with backoff, dead-letter routing, duplicate detection and a transactional outbox. | CDM-112 | Proven by an integration test against an embedded broker | `messaging/**`, `23-messaging-modernization-mapping.md` | Done |
| CDM-114 | Authorization summary and detail services | Five-row keyset paging, boundary messages, selection edit, display editing, decline-reason text, chain navigation. | CDM-109 | BR catalogues of COPAUS0C and COPAUS1C implemented | `AuthorizationInquiryService` | Done |
| CDM-115 | Fraud marking service | The CPVD toggle and the `COPAUS2C` fraud report insert in one local transaction. | CDM-114 | Both directions persisted with the source messages | `FraudMarkingService`, `auth_fraud` | Done |
| CDM-116 | Authorization OpenAPI contract and REST layer | Contract-first operations for the two screens and the fraud toggle. | CDM-114, CDM-115 | Spec validates; controllers match it | `authorization-service/.../openapi/openapi.yaml`, `api/**` | Done |
| CDM-117 | Cardholder lookup across the context boundary | Add the account-service operations that replace the direct `CARDXREF`/`ACCTDAT`/`CUSTDAT` reads, and the client that calls them. | CDM-108 | Read flags preserved; transport failure retryable, not a decline | `CardholderLookupController`, `CardholderLookupService`, `CardholderLookupClient` | Done |
| CDM-118 | Expired-authorization purge batch | `CBPAUP0C` as a restartable Spring Batch job: parameter defaults, expiry arithmetic, count and amount adjustment, keyset restart, return codes, report totals. | CDM-109 | Job COMPLETED with the source's totals | `batch-auth-purge/**`, `evidence/authorization-purge-batch-run.txt` | Done |
| CDM-119 | React UI — authorization summary and detail | The two BMS maps as screens, with paging, selection and fraud marking replacing PF7/PF8/PF5. | CDM-116 | Screens reproduce the map fields and messages | `AuthorizationSummaryScreen.tsx`, `AuthorizationDetailScreen.tsx` | Done |
| CDM-120 | Gateway, Compose, Helm, Dockerfiles and CI extension | Route and enforce JWT for the new service, add Kafka and the new database to Compose, add the two workloads to the chart, build and gate the new modules in CI. | CDM-116, CDM-118 | Same gates as the existing modules | `gateway/**`, `docker-compose.yml`, `deploy/helm/**`, `.github/workflows/modernization-ci.yml` | Done |
| CDM-121 | Authorization test suite and coverage | Unit, service, API, messaging, concurrency and batch tests with the same JaCoCo gates. | CDM-112..119 | ≥80% line and branch on migrated logic | 102 authorization backend tests, 10 authorization frontend tests | Done |
| CDM-122 | Consistency-model deltas for the authorization decomposition | Record every atomicity/ordering guarantee lost with the IMS+DB2+MQ two-phase commit and resolve each. | CDM-113, CDM-118 | Each delta accepted-with-rationale or compensated-with-test | CMD-10..CMD-15 in `12-consistency-model-delta-register.md` | Done |
| CDM-123 | Authorization unsupported-construct entries | Record every unsupported, ambiguous or defective construct in the authorization closure. | CDM-100..119 | No construct silently ignored | UCR-17..UCR-32 | Done |
| CDM-124 | Document extension | Extend inventory, disposition, architecture, data mapping, screens, API, batch, mapping guide, testing, RTM, limitations and the README rather than forking them. | CDM-100..123 | Every existing document covers all three contexts | `docs/**`, `modernization/README.md` | Done |
| CDM-125 | Section 8.12 closure | Add the artifacts still missing on the branch. | CDM-124 | Seven documents added | `18-…` … `24-…` | Done |
| CDM-126 | Delivery on the existing branch and pull request | Commit the authorization work on top of the account and identity work and update pull request #21. | CDM-124, CDM-125 | One branch, one pull request, not merged | branch `devin/1789627888-carddemo-account-user-microservices`, PR #21 | Done |

Ticket ids start at CDM-100 so the account and identity tickets CDM-1..CDM-33 keep the numbers they
were delivered under. The one-branch, one-pull-request deviation recorded above applies unchanged: this
scope was also required to land on a single branch and a single pull request, and was added to the
ones the account and identity work already used.

## Deviation from the migration process guideline

The organisation's migration guideline suggests one branch and pull request per migration layer.
This engagement explicitly required a single end-to-end run delivering one dedicated modernisation
branch and one pull request, so the layer-per-branch convention is not applied here. Layer ordering
was still respected inside the branch: shared COBOL semantics → domain → schema → persistence →
services → API → UI → batch → tests, each compiling before the next.
