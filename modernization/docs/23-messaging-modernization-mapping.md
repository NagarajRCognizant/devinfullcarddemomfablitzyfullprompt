# Messaging Modernisation Mapping and Target Integration Design

Scope: the only messaging in the supplied source is the authorization request/reply pair consumed by
`COPAUA0C` (CICS transaction CP00). The account management and user administration capabilities have
no messaging. This document is the source-to-target messaging traceability mapping required by
playbook §8.8 and §8.12 item 11.

## 1. Source messaging inventory

| Source object | Evidence | Role | Semantics the source relies on |
|---|---|---|---|
| Queue `AWS.M2.CARDDEMO.PAUTH.REQUEST` | `app/app-authorization-ims-db2-mq/README.md`, `COPAUA0C` `EXEC CICS … MQGET`-equivalent `MQ` calls in `1000-GET-MESSAGE` | inbound authorization requests from the acquirer simulator | destructive read inside the CICS unit of work; one message at a time; no ordering requirement beyond arrival order |
| Queue `AWS.M2.CARDDEMO.PAUTH.REPLY` | same README, `COPAUA0C` `8900-SEND-REPLY` | authorization reply | reply written to the queue named by `MQMD-REPLYTOQ`, correlated by `MQMD-CORRELID` |
| `MQTM` trigger / CICS trigger monitor | `csd/CRDDEMO2.csd` transaction CP00 | starts CP00 when the request queue is triggered | first-in-queue trigger type; CP00 then drains up to a program-imposed limit |
| Request payload | `cpy/CCPAURQY.cpy` | 122-byte fixed-width request | position-dependent fixed-width fields, EBCDIC |
| Reply payload | `cpy/CCPAURLY.cpy` | 56-byte fixed-width reply | as above |
| Error log record | `cpy/CCPAUERY.cpy` | error record written by `9500-LOG-ERROR` | subsystem/severity coded diagnostics |

The exact `MQTM` trigger attributes (trigger type, trigger interval, process definition, queue depth
thresholds) are not in the repository. The gap is recorded as UCR-17; the trigger mechanism has no
target counterpart because a Kafka consumer group is permanently subscribed rather than started on
demand.

## 2. Interaction-pattern analysis (performed before any conversion)

| Question | Source answer | Consequence for the target |
|---|---|---|
| Is this a queue (work distribution) or a log (event stream)? | work distribution: a request is consumed once and produces one reply | a topic with a consumer group reproduces competing-consumer semantics; the topic name carries the domain event and a version rather than the queue name |
| Request/reply or fire-and-forget? | request/reply, over two queues, correlated by `MQMD-CORRELID` | the correlation id and the requested reply destination are carried as Kafka headers, not invented |
| What ordering does the source guarantee? | arrival order on one queue, consumed by a single CP00 instance at a time | per-card ordering is the only ordering with business meaning (two authorizations on one card must see each other's balance effect), so the card number is the partition key |
| Delivery guarantee? | MQ persistent message inside a CICS syncpoint — effectively once | Kafka gives at-least-once; the request log gives idempotency, so the *observable* effect stays once (CMD-12) |
| Is the reply part of the same unit of work? | yes: `SYNCPOINT` covers the get, the IMS and DB2 updates and the reply put | one local PostgreSQL transaction plus a transactional outbox (CMD-10, CMD-11) |
| Poison-message handling? | `9500-LOG-ERROR` writes an error record and the message is abandoned | bounded retry, then a dead-letter topic; the error record becomes structured logging plus the DLT record |

This is why the conversion is not a mechanical queue-to-topic rename: the two queues become one
request topic keyed by card number, one reply topic, and one dead-letter topic, and the transactional
behaviour of the source is replaced by an explicitly compensated design rather than assumed.

## 3. Source-to-target traceability

| Source | Target | Implementation |
|---|---|---|
| `AWS.M2.CARDDEMO.PAUTH.REQUEST` | topic `carddemo.authorization.request.v1`, 3 partitions, key = card number | `AuthorizationTopicsProperties`, `KafkaConfig` |
| `AWS.M2.CARDDEMO.PAUTH.REPLY` | topic `carddemo.authorization.reply.v1` (default), or the topic named in the request's `replyTo` header | `AuthorizationReplyPublisher` |
| `MQMD-CORRELID` | Kafka header `correlationId`, echoed on the reply | `AuthorizationRequestListener`, `AuthorizationReplyPublisher` |
| `MQMD-REPLYTOQ` | Kafka header `replyTo`, stored on the outbox row | `AuthorizationReplyOutboxEntity.replyTopic` |
| `MQMD-MSGID` | Kafka header `requestId` (falls back to a deterministic hash of the payload when absent) | `AuthorizationRequestListener`, `AuthorizationRequestLogEntity` |
| `MQTM` trigger + CICS trigger monitor | permanently subscribed consumer group `carddemo-authorization` | `spring.kafka.consumer.group-id`; retired construct, UCR-17 |
| `WS-REQSTS-PROCESS-LIMIT` message drain limit | listener concurrency and `max.poll.records`; no drain limit is needed because the consumer never terminates | `application.yml` |
| `SYNCPOINT` around get/updates/put | one `@Transactional` method plus outbox publication after commit | `AuthorizationRequestProcessor.process`, CMD-10/CMD-11 |
| `9500-LOG-ERROR` / `CCPAUERY` fields | structured log event plus a record on `carddemo.authorization.request.DLT` carrying the original key, payload and exception headers | `KafkaConfig.authorizationErrorHandler` |
| Fixed-width `CCPAURQY` layout | `AuthorizationRequestMessage.parse` (positional, blank-preserving) | `AuthorizationMessageLayoutTest` |
| Fixed-width `CCPAURLY` layout | `AuthorizationReplyMessage.format` | same test |

## 4. Topic design

| Topic | Partitions | Key | Retention intent | Producers | Consumers |
|---|---|---|---|---|---|
| `carddemo.authorization.request.v1` | 3 (environment-tunable) | card number | operational, short (hours) | acquirer gateway / simulator | `authorization-service` consumer group `carddemo-authorization` |
| `carddemo.authorization.reply.v1` | 3 | card number | operational, short | `authorization-service` outbox relay | the requesting system |
| `carddemo.authorization.request.DLT` | 3 | original key | long (days), manual triage | error handler | operations tooling |
| `carddemo.fraud.marked.v1` | 3 | card number | long (audit) | `FraudMarkingService` | downstream fraud/analytics consumers |

Partitioning rationale: the card number is the only field present on every request that the business
requires to be ordered with itself. Using the account id would serialise unrelated cards of one
account; using no key would let two authorizations on one card be processed concurrently, which the
single-threaded source never did. Partition count 3 is a documented default, not a measured value —
production volumes are absent from the repository (E-16, UCR-12).

Dead-letter routing keeps the original partition (`new TopicPartition(deadLetterTopic,
record.partition())`), so a card's failures stay ordered with each other and a replay after a fix
preserves that order.

## 5. Delivery guarantees, retries and idempotency

- Producer: `acks=all`, `enable.idempotence=true` — no duplicate reply from a producer retry.
- Consumer: `enable-auto-commit=false`, `ack-mode=record`; the offset advances only after the
  processing transaction commits, so a crash re-delivers rather than loses.
- Retry: `DefaultErrorHandler` with `FixedBackOff(500 ms, 3)`. `MalformedAuthorizationRequestException`
  is non-retryable (a bad layout will never parse), `CardholderLookupException` is retryable (the
  account service may be briefly unavailable). Retry is bounded, matching the source's refusal to
  spin on a failing message.
- Idempotency: every request is logged in `authorization_request_log` keyed by request id inside the
  same transaction as the authorization. A duplicate delivery finds the log row and republishes the
  stored reply instead of authorizing twice — proven by
  `AuthorizationRequestProcessorIntegrationTest.aDuplicateDeliveryReplaysTheStoredReplyWithoutAuthorizingTwice`.
- Replay: because the reply is reconstructed from the request log, a deliberate replay of the request
  topic is safe and returns the original decision rather than re-deciding against a changed balance.
- Ordering under retry: a retried record blocks its own partition only, so per-card order survives
  retry; cross-card order was never guaranteed by the source either.

## 6. Reply publication (transactional outbox)

The source put the reply on the queue *inside* the same syncpoint as the database updates. Kafka is
not a participant in the PostgreSQL transaction, so the reply is written to
`authorization_reply_outbox` in that transaction and relayed afterwards by
`AuthorizationReplyPublisher`, which polls unpublished rows in batches of 100, sends with the card
number as key and the correlation id as a header, and marks the row published only after the send
succeeds. A failed send leaves the row unpublished and increments its attempt counter, so the reply
is never lost and never sent before the authorization it describes is durable. Recorded as CMD-11 and
proven by `AuthorizationReplyPublisherTest`.

## 7. Operational controls

| Control | Target |
|---|---|
| Consumer lag | `kafka_consumergroup_lag` per group/topic, alert at sustained lag > 1000 records |
| DLT arrivals | alert on any record produced to `carddemo.authorization.request.DLT` |
| Outbox backlog | gauge on unpublished `authorization_reply_outbox` rows, alert > 100 or age > 60 s |
| Retry rate | consumer error counter, alert on a sustained non-zero rate |
| Replay procedure | reset the consumer group offset for one partition; idempotency makes the replay safe (see `21-cutover-runbook.md` §6) |
| Schema evolution | topic names carry `.v1`; a layout change is a new version topic, because the payload is a fixed-width contract shared with an external acquirer |

## 8. Gaps and review-required items

| Item | Status |
|---|---|
| `MQTM` trigger attributes unavailable | UCR-17, REVIEW REQUIRED |
| MQ once-only delivery cannot be reproduced exactly | UCR-21 / CMD-12, compensated with idempotency and proven by test |
| Request payload is parsed with no validation in the source | UCR-19, AS-IS behaviour preserved, REVIEW REQUIRED |
| Production message volumes and trigger statistics absent | E-16 in `02-source-inventory-and-coverage.md`; partition count and concurrency are defaults |
| Reply-to queue security (who may name a reply destination) | REVIEW REQUIRED — the target restricts replies to configured topics before production use |
