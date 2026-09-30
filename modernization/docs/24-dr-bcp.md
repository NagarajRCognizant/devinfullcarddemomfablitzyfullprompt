# Disaster Recovery and Business Continuity Plan

Playbook §8.12 item 12. Covers the converted platform: `account-service`, `identity-service`,
`authorization-service`, `gateway`, the `account-extract` and `auth-purge` batch jobs, the React
frontend, the three PostgreSQL databases and the Kafka cluster.

Recovery objectives below are the values this design supports and are stated as targets to be
ratified by the business; the repository contains no production SLA evidence.

## 1. Business impact and recovery objectives

| Capability | Impact of loss | Criticality | RTO target | RPO target |
|---|---|---|---|---|
| Authorization request processing (`authorization-service` consumer) | acquirer receives no reply; card transactions cannot be authorized | tier 1 | 15 min | 0 committed authorizations |
| Authorization summary / detail / fraud screens | operations cannot inspect or mark authorizations; no customer-facing impact | tier 2 | 4 h | 0 |
| Account view / update | servicing blocked | tier 2 | 1 h | 0 |
| Sign-on / identity | all screens blocked | tier 1 (dependency) | 15 min | 0 |
| Expired-authorization purge | available credit drifts; recovers on the next run | tier 3 | next business day | last completed run |
| Account extract | downstream report late | tier 3 | next business day | last completed run |

Zero RPO on authorization data is achievable because the decision, the detail, the request log and
the reply outbox are committed in one local transaction (CMD-10), so a surviving database contains
either the whole authorization or none of it — never a reply without its authorization.

## 2. Architecture for resilience

| Layer | Configuration | Failure tolerated |
|---|---|---|
| Services | ≥ 2 replicas, spread across availability zones via pod anti-affinity; stateless | node or zone loss |
| PostgreSQL | primary with synchronous standby in a second zone plus an asynchronous standby in the secondary region; PITR from WAL archive | instance, zone, region |
| Kafka | replication factor ≥ 3, `min.insync.replicas` 2, brokers across zones | broker or zone loss without message loss |
| Consumer | one consumer group; a rebalance reassigns partitions automatically, and per-card ordering survives because a partition has a single owner | pod loss |
| Gateway / ingress | ≥ 2 replicas behind the load balancer | pod loss |
| Frontend | static bundle served from the CDN/ingress; no session state | pod loss |
| Batch | Kubernetes CronJob with the Spring Batch job repository in PostgreSQL; a failed run is restartable from its last committed chunk (CMD-15) | pod loss mid-run |
| Secrets | external secret store replicated to both regions | region loss |

## 3. Backup

| Asset | Method | Frequency | Retention | Restore validation |
|---|---|---|---|---|
| `carddemo_authorization` | full base backup + continuous WAL archive | daily full, WAL continuous | 35 days | monthly restore rehearsal to a scratch instance, followed by `tools/reconcile_authorization_data.py` where the seeded baseline applies |
| `carddemo`, `carddemo_identity` | same | same | 35 days | monthly |
| Spring Batch job repository | included in the owning database backup | — | — | restart a job after restore |
| Kafka topics | retention configured to exceed the RTO window plus the rollback window; topics are a transport, not the record of truth | — | ≥ 7 days recommended | replay rehearsal |
| Flyway migration scripts and application images | Git + image registry, both replicated | per release | per policy | redeploy from tag |
| Helm values per environment | Git (no secrets) | per change | per policy | `helm template` |
| Reconciliation and parity evidence | `docs/evidence/` in Git | per release | per policy | — |

The database is the record of truth for authorizations; Kafka is not. Recovery therefore restores the
database first and replays messages second.

## 4. Failure scenarios and responses

| # | Scenario | Detection | Response | Data impact |
|---|---|---|---|---|
| 4.1 | Single service pod fails | readiness probe, alert | Kubernetes restarts/reschedules; capacity from the remaining replicas | none |
| 4.2 | Zone loss | multiple probes, node conditions | replicas and the database failover to the surviving zone; consumer group rebalances | none; RPO 0 |
| 4.3 | PostgreSQL primary loss | health check, replication alert | promote the synchronous standby; services reconnect on the same DNS name | none committed |
| 4.4 | Database corruption or bad data change | reconciliation failure, integrity alert | PITR to just before the event; replay authorization requests from the request topic — the request log makes replay idempotent | bounded by the recovery point |
| 4.5 | Kafka broker loss | broker metrics | in-sync replicas serve; replace the broker | none |
| 4.6 | Kafka cluster loss | produce/consume failures | authorization *decisions* stop; screens and account servicing continue; rebuild or fail over the cluster, then resume — unreplied requests are re-sent by the acquirer and de-duplicated by the request log | none committed; replies in the outbox are published when the cluster returns |
| 4.7 | Account-service unavailable | `CardholderLookupClient` errors | requests fail retryably and land on the DLT after bounded retries; replay the DLT once account-service recovers | none; no authorization is decided on missing data |
| 4.8 | Reply relay stuck | unpublished outbox rows aging | restart the relay; no re-decision occurs | none |
| 4.9 | Region loss | platform alerting | invoke §5 | bounded by asynchronous replication lag |
| 4.10 | Bad release | error rate, decision-mix shift | `helm rollback` per `20-deployment-and-rollback-checklist.md` §5 | none (additive migrations) |
| 4.11 | Ransomware / malicious change | security alerting | isolate, restore from immutable backup, rotate all secrets, re-verify with reconciliation | bounded by the clean recovery point |
| 4.12 | Loss of the modernized platform before decommission | platform alerting | during the retention window in `21-cutover-runbook.md` §7 the mainframe path can be re-enabled per §5 of the deployment checklist, including the reverse ETL | bounded by the reverse ETL |

## 5. Regional failover procedure

1. Declare the disaster; record the time (the basis of the achieved RPO).
2. Stop the primary-region consumers if they are still reachable, to prevent split-brain writes.
3. Promote the secondary-region PostgreSQL standbys for all three databases.
4. Point the services' datasource DNS at the promoted instances.
5. Bring up the services in the order of `20-deployment-and-rollback-checklist.md` §2.
6. Verify with §3 of that document, then run the authorization spot checks.
7. Fail the Kafka cluster over, or point the consumers at the secondary cluster; confirm the consumer
   group's committed offsets — re-processing an already-decided request is safe, losing one is not.
8. Ask the acquirer to re-send any request that received no reply inside the window; duplicates are
   absorbed by the request log.
9. Publish any outstanding outbox rows and confirm the table drains.
10. Run the reconciliation query set for the recovery window: for every account touched, the summary
    counters must equal the aggregate of its details.
11. Resume the CronJobs last.
12. Communicate recovery; record the achieved RTO/RPO against §1.

## 6. Failback

1. Rebuild the primary region and re-establish replication from the now-primary secondary.
2. Choose a quiet window; quiesce the acquirer feed.
3. Switch replication direction and promote the primary region.
4. Repeat §5 steps 5–11 in the primary region.
5. Confirm zero unpublished outbox rows and zero consumer lag before reopening the feed.

## 7. Continuity of operations (non-technical)

| Aspect | Provision |
|---|---|
| Roles | the cutover roles in `21-cutover-runbook.md` §1 remain the DR roles, with a named deputy each |
| Communication | primary and secondary channels, both independent of the affected platform |
| Escalation | incident commander declares the disaster; business owner authorizes degraded operation |
| Degraded mode | authorization screens read-only while the database is in recovery; fraud marking deferred and queued manually with an audit note |
| Manual fallback | during the retention window, the mainframe path (§4.12); afterwards, the acquirer's own stand-in processing rules apply and must be confirmed with them |
| Documentation | this plan, the cutover runbook and the deployment checklist are stored in Git and exported to a location reachable when the platform is not |

## 8. Testing the plan

| Test | Frequency | Pass condition |
|---|---|---|
| Backup restore rehearsal | monthly | restore completes inside RTO; reconciliation passes |
| Database failover drill | quarterly | promotion inside RTO; no committed authorization lost |
| Kafka broker loss drill | quarterly | no message loss; lag recovers |
| Consumer replay drill | quarterly | replayed requests are answered from the request log without a second decision |
| Full regional failover exercise | annually | RTO/RPO in §1 met and recorded |
| Rollback / reverse-ETL rehearsal | before cutover, then annually while the mainframe remains | authorizations created in the target are loadable back into IMS/DB2 |

## 9. REVIEW REQUIRED

| Item | Why |
|---|---|
| RTO/RPO values in §1 | derived from criticality analysis of the source system's role, not from a supplied SLA; the business must ratify them |
| Acquirer stand-in behaviour | the repository contains no acquirer contract; the degraded-mode assumption in §7 must be confirmed |
| Kafka retention period | must be set from the measured request rate and the ratified RTO, neither of which is available in the repository (see also UCR-12) |
| Secondary-region capacity | sizing depends on the performance baseline that is still to be established (`19-test-strategy-and-quality-gates.md` §6) |
