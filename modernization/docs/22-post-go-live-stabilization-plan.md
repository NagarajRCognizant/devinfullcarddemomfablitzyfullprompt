# Post-Go-Live Stabilization Plan

Playbook §8.12 item 10. Covers the period from the completion of the cutover
(`21-cutover-runbook.md` §3.16) to steady-state operation of the converted services.

## 1. Phases

| Phase | Window | Posture |
|---|---|---|
| Hypercare | day 0 – day 5 | dedicated squad on call during business hours and on rota overnight; mainframe rollback path intact; no non-critical change |
| Heightened support | day 6 – day 20 | normal on-call plus a named modernization engineer; daily triage; defect fixes released on demand |
| Steady state | day 21 onward | standard on-call and release cadence; stabilization exit review completed |

## 2. Monitoring focus by phase

| Signal | Source | Hypercare threshold | Why it matters |
|---|---|---|---|
| Authorization decision mix (approved vs declined, by reason code) | application metrics | any shift beyond the agreed rehearsal baseline | the most direct indicator of a parity defect in the decision engine |
| Consumer group lag on `carddemo.authorization.request.v1` | Kafka | lag returns to zero within one minute of a burst | a sustained lag means the acquirer's replies are late, which the source path never was |
| Dead-letter arrivals on `…request.DLT` | Kafka | zero; any arrival is investigated the same day | malformed or unresolvable requests |
| Unpublished rows in `authorization_reply_outbox` | database | none older than 60 s | a stuck relay means replies are not reaching the acquirer |
| Duplicate request rate (`authorization_request_log` hits) | database / metrics | informational; a spike means the acquirer is retrying | proves idempotency is carrying the load rather than masking a fault |
| Reply latency, request receipt to reply publication | metrics | within the baseline agreed in the performance environment | the acquirer's timeout behaviour is unchanged by the platform move |
| HTTP error rate and p95 latency per route (gateway) | gateway metrics | no route above the pre-cutover baseline | screen usability |
| `CardholderLookupClient` failure and timeout rate | metrics | near zero; every failure correlated with an account-service incident | this boundary replaced a local file read, so it is the new failure mode (UCR-26) |
| Lock waits / deadlocks on `pending_auth_summary` | PostgreSQL | none sustained | per-account pessimistic locking replaced global FIFO (CMD-14) |
| Purge job outcome, totals read and deleted | batch log | completes inside its window; totals consistent with the previous business day | the job now persists the available-credit adjustment (UCR-27) |
| Authentication failures / token rejections | identity + gateway | no unexplained cluster | role mapping from the legacy user type |
| Database growth of `pending_auth_detail` | PostgreSQL | growth flattens once the purge runs | proves the purge is actually removing what IMS removed |

## 3. Daily routine during hypercare

1. Morning: review overnight alerts, consumer lag chart, DLT and outbox counts, purge job result.
2. Reconciliation spot check: for three accounts that transacted overnight, compare the summary
   counters against the sum of their details.
3. Triage new defects into P1–P4 using `19-test-strategy-and-quality-gates.md` §4 and publish the
   open list.
4. Business check-in: authorization decision mix, any acquirer-reported mismatch, any screen issue.
5. End of day: hypercare log entry — what was seen, what was changed, what remains open.

## 4. Defect handling

| Severity | Response | Fix path |
|---|---|---|
| P1 — wrong business outcome (decision, amount, counter, deletion) | immediate; consider scaling the consumer to zero and re-enabling the source path per `20-deployment-and-rollback-checklist.md` §5 | fix with a failing test first, then release; replay the affected offsets — the request log makes the replay safe |
| P2 — contract or screen deviation | same business day | patch release |
| P3 — operational shortfall with a workaround | within the phase | scheduled release; recorded in `17-limitations-and-review-required.md` |
| P4 — cosmetic | backlog | normal cadence |

No defect is closed without a regression test in the layer that should have caught it — this is what
keeps the parity claim in `10-testing-and-parity.md` true over time.

## 5. Known items carried into stabilization

| Item | Nature | Action during stabilization |
|---|---|---|
| UCR-09 | container images were never built locally (Docker Hub rate limiting) | confirm images are built and scanned in the pipeline registry |
| UCR-22 | source clears the cash balance on approval; carried forward AS-IS | watch for a business report that contradicts it; escalate rather than patch |
| UCR-27 | the purge now persists the available-credit adjustment the source computed and discarded | verify the first month's credit availability against expectations with the business owner |
| UCR-30 | declined-total accumulation defect corrected | compare declined totals against the source's last known values and confirm the corrected figures are accepted |
| UCR-31 | one supplied unload record has a blank account key and was not migrated | confirm with the data owner whether the record exists in production and needs a manual key |
| UCR-32 | two summary character fields are unpopulated in the source image | confirm no out-of-scope producer populates them before the fields are considered dead |
| Performance baseline missing | no production volumes were available | establish throughput, lag and batch elapsed baselines in the first two weeks and tune partitions, listener concurrency and chunk size |
| Reverse ETL for rollback | rehearsed procedure not yet implemented | complete or formally retire once the rollback window closes |

## 6. Exit criteria (end of stabilization)

| # | Criterion |
|---|---|
| 6.1 | Zero open P1 and P2 defects for five consecutive business days |
| 6.2 | Decision mix, latency, lag and purge duration within the agreed baselines for ten consecutive business days |
| 6.3 | Zero DLT arrivals unexplained; every arrival has a root cause and either a fix or an accepted acquirer-side cause |
| 6.4 | One full month-end (and one full purge cycle) completed successfully |
| 6.5 | Every REVIEW REQUIRED item in §5 either closed or re-owned with a date |
| 6.6 | Alerting tuned: no alert with a false-positive rate above the agreed level, no signal in §2 without an alert |
| 6.7 | Runbooks updated with what was actually learned, including any new failure mode |
| 6.8 | Mainframe decommission steps in `21-cutover-runbook.md` §7 authorized |
| 6.9 | Handover to standard on-call accepted, with the modernization squad released |
