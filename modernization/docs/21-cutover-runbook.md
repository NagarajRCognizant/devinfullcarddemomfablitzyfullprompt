# Cutover Runbook Template

Playbook §8.12 item 9. Template for moving the Credit Card Authorizations capability (and the
already-converted account management and user administration capabilities) from the mainframe to the
converted services. Timings are placeholders to be established in the dress rehearsal, not estimates
asserted from the repository.

## 0. Scope of this cutover

| Item | Source | Target |
|---|---|---|
| Authorization request processing | CICS CP00 / `COPAUA0C`, MQ queues | `authorization-service` Kafka consumer/producer |
| Authorization summary / detail | CICS CPVS / CPVD, BMS `COPAU00`/`COPAU01` | React screens + `/api/authorizations` |
| Fraud marking | `COPAUS2C`, DB2 `AUTHFRDS` | fraud toggle API + `auth_fraud` |
| Expired-authorization purge | `CBPAUP0J` / `CBPAUP0C` | `batch-auth-purge` CronJob |
| Authorization data | IMS `DBPAUTP0`, DB2 `AUTHFRDS` | `carddemo_authorization` |

## 1. Roles

| Role | Responsibility |
|---|---|
| Cutover lead | runs the sequence, owns the go/no-go |
| Mainframe operations | quiesces CP00, stops `CBPAUP0J`, takes the final unload |
| Migration engineer | runs the ETL and reconciliation |
| Platform/SRE | deploys, watches probes, lag, dashboards |
| Acquirer/integration owner | switches the request source and reply destination |
| Business validator | executes the screen and decision spot checks |
| Security | confirms tokens, realm configuration and secrets, audit trail |
| Identity administrator | provisions the realm users, verifies the enrolment path, holds the reset procedure for a user who cannot enrol |

## 2. Prerequisites (T−5 … T−1)

| # | Task | Owner | Done |
|---|---|---|---|
| 2.1 | All gates in `19-test-strategy-and-quality-gates.md` §3 green on the release commit | release eng | ☐ |
| 2.2 | Deployment checklist §1 complete (`20-deployment-and-rollback-checklist.md`) | SRE | ☐ |
| 2.3 | Dress rehearsal completed in a pre-production environment, including a timed rollback | cutover lead | ☐ |
| 2.4 | ETL rehearsed on a full-size unload; elapsed time recorded | migration eng | ☐ |
| 2.5 | Reconciliation passed in rehearsal with zero mismatches | migration eng | ☐ |
| 2.6 | Kafka topics created; retention set to exceed the cutover window plus the rollback window | SRE | ☐ |
| 2.7 | Acquirer integration tested against the target in comparison mode | integration owner | ☐ |
| 2.8 | REVIEW REQUIRED items with business impact signed off — in particular UCR-27 (purge now persists the available-credit adjustment), UCR-30 (declined total corrected), UCR-31 (unload record with a blank account key), UCR-22 (cash balance cleared on approval, carried forward AS-IS) | business owner | ☐ |
| 2.9 | Communication plan issued; freeze on authorization-related mainframe changes | cutover lead | ☐ |
| 2.10 | Every operator who has to sign on has a realm account linked to its USRSEC row (identity-service reconciliation reports none unlinked) and knows it will be asked to enrol an authenticator on first sign-on — `30-signing-on-with-mfa-user-guide.md` circulated | identity admin | ☐ |
| 2.11 | Enrolment rehearsed by at least one operator per role, and the reset procedure for a lost authenticator rehearsed | identity admin | ☐ |

## 3. Cutover sequence

| # | T | Task | Verification | Rollback point |
|---|---|---|---|---|
| 3.1 | T−60m | Announce start; confirm no `CBPAUP0J` run is in flight | scheduler shows the job held | — |
| 3.2 | T−45m | Hold `CBPAUP0J` in the scheduler | job held | resume the job |
| 3.3 | T−30m | Stop the acquirer feed into `AWS.M2.CARDDEMO.PAUTH.REQUEST`; let CP00 drain | request queue depth 0, reply queue depth 0 | restart the feed |
| 3.4 | T−20m | Disable the CP00 trigger; set CICS transactions CP00/CPVS/CPVD out of service | `CEMT INQ TRAN` shows disabled | enable |
| 3.5 | T−15m | Take the final IMS unload (`UNLDPADB.JCL`) and the DB2 `AUTHFRDS` extract; record record counts | counts recorded | — |
| 3.6 | T−10m | Back up the target database; note the restore point | backup complete | — |
| 3.7 | T−5m | Go/no-go | all above ticked | abort, re-enable mainframe |
| 3.8 | T+0 | Apply Flyway migrations to `carddemo_authorization` | `flyway_schema_history` shows the expected versions | restore point |
| 3.9 | T+5m | Run the ETL: decode the unload, generate the load, apply it, load the fraud extract | row counts match §3.5 counts minus documented rejects | truncate and re-run |
| 3.10 | T+20m | Run `tools/reconcile_authorization_data.py`; attach the output to the change record | `RECONCILIATION PASSED`, zero mismatches | truncate and re-run |
| 3.11 | T+30m | Deploy services in the order of `20-deployment-and-rollback-checklist.md` §2 | health checks pass | `helm rollback` |
| 3.12 | T+40m | Run the post-deployment verification list (§3 of the same document) | all checks pass | `helm rollback` |
| 3.12a | T+45m | Sign-on spot checks: one administrator and one regular user sign on with password and a one-time code and land on the menu their USRSEC type dictates; a user without an authenticator is sent to enrolment | both menus reached; the enrolling user reaches no screen until it enrols | `helm rollback`; break-glass legacy mode only per `20-deployment-and-rollback-checklist.md` §5 |
| 3.13 | T+50m | Business spot checks: three seeded accounts on the summary screen, one detail, one fraud toggle and its reversal, one approved and one declined synthetic authorization | results match the expected values agreed in rehearsal | rollback |
| 3.14 | T+60m | Point the acquirer at `carddemo.authorization.request.v1` and set the reply destination | a live request receives a correlated reply | revert the acquirer; re-enable CP00 |
| 3.15 | T+75m | Enable the `auth-purge` CronJob (first run observed manually) | totals read/deleted reported; summary counters consistent | suspend the CronJob |
| 3.16 | T+90m | Go/no-go on hypercare entry; announce completion | — | full rollback (§5) |

## 4. Dual-run (optional, recommended)

Run for one full business day before §3.14:

1. Mirror each acquirer request to both MQ and Kafka.
2. The mainframe reply remains authoritative; the target's reply goes to a comparison topic.
3. Diff on card number + transaction id: response code, reason code, approved amount.
4. Investigate every difference. Expected, pre-agreed differences are only those registered as
   remediations (UCR-27, UCR-30) — anything else is a defect and blocks cutover.
5. Compare the end-of-day summary counters and the purge's totals read/deleted between platforms.

## 5. Full rollback (post-cutover)

| # | Task |
|---|---|
| 5.1 | Point the acquirer back at `AWS.M2.CARDDEMO.PAUTH.REQUEST`; stop the target consumer (scale to zero) |
| 5.2 | Re-enable CP00/CPVS/CPVD and the CP00 trigger |
| 5.3 | Extract authorizations created in the target during the cutover window (`pending_auth_summary`, `pending_auth_detail`, `auth_fraud` by timestamp) and load them into IMS/DB2 with the reverse ETL |
| 5.4 | Resume `CBPAUP0J`; suspend the `auth-purge` CronJob |
| 5.5 | Reconcile counts between platforms and record the outcome |
| 5.6 | Announce rollback; schedule a retrospective before a second attempt |

Rollback beyond the point where the target has accepted live authorizations requires step 5.3 —
authorizations created in PostgreSQL are business data, not deployment state. The reverse ETL must be
written and rehearsed before cutover; it is currently a REVIEW REQUIRED item in
`17-limitations-and-review-required.md`.

## 6. Replay and recovery procedures

| Scenario | Procedure |
|---|---|
| Requests processed with a defect | fix, then reset the consumer group offset to the first affected offset for the affected partitions; the request log makes the replay idempotent, so already-correct requests are answered from the stored reply |
| Replies not delivered | rows stay unpublished in `authorization_reply_outbox`; restart the relay — no re-decision occurs |
| Poison messages | inspect `carddemo.authorization.request.DLT`, correct the payload with the acquirer, re-produce to the request topic |
| Purge deleted too much | restore from the pre-run backup; the job is idempotent on re-run for the same business date |

## 7. Decommission (after hypercare)

1. Keep CP00/CPVS/CPVD disabled but installed for 30 days.
2. Retain the MQ queue definitions, empty, for the same period.
3. Keep `CBPAUP0J` held, not deleted.
4. Archive the final unload and the reconciliation evidence per the retention policy.
5. Only then remove the CSD entries, the DBDs/PSBs, the DB2 table and the JCL, recording the
   disposition change in `13-artifact-disposition.md`.
