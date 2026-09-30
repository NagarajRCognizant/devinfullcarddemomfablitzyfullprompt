# Business Rules — PAUDBLOD, PAUDBUNL, DBUNLDGS (Authorization database load and unload)

Sources: `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL` (369 lines, load),
`cbl/PAUDBUNL.CBL` (317 lines, unload to sequential files), `cbl/DBUNLDGS.CBL` (366 lines, unload
to GSAM). Driven by `jcl/LOADPADB.JCL`, `jcl/UNLDPADB.JCL`, `jcl/UNLDGSAM.JCL`; the database itself
is defined and initialised by `jcl/DBPAUTP0.jcl` with DBDs `ims/DBPAUTP0.dbd` (HIDAM) and
`ims/DBPAUTX0.dbd` (the HIDAM primary index over the root key `ACCNTID`). PSBs: `PSBPAUTB` (load),
`PAUTBUNL` (unload),
`DLIGSAMP` (GSAM unload). Copybooks: `CIPAUSMY`, `CIPAUDTY`, `PAUTBPCB`, `PADFLPCB`, `PASFLPCB`,
`IMSFUNCS`. Data: `data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`.

These are data-movement utilities, not business services: they carry no pricing, decision or
validation logic. Their rules are the record layouts, the load order and the duplicate/failure
behaviour, all of which the target data-migration pipeline must reproduce.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Summaries are loaded before authorizations | The load reads the whole summary file first and only then the authorization file, so every authorization has its account's summary already present. | `PAUDBLOD` `MAIN-PARA` (169) lines 175-181 | Control Flow | Business-derived | — |
| BR-02 | Both input files must open | The load opens the summary and authorization files before processing and ends abnormally if either cannot be opened. | `PAUDBLOD` `1000-INITIALIZE` (190) lines 200-215 | Exception Handling | Business-derived | — |
| BR-03 | Summary record layout | Each summary input record is the pending authorization summary segment exactly as laid out in `CIPAUSMY`. | `PAUDBLOD` `2000-READ-ROOT-SEG-FILE` (222) `MOVE INFIL1-REC TO PENDING-AUTH-SUMMARY`, `FD INFILE1` LRECL of `CIPAUSMY` | Formatting/Conversion | Business-derived | Positional, EBCDIC packed fields — conversion rules in `05-data-mapping.md` |
| BR-04 | Authorization record layout | Each authorization input record is the pending authorization detail segment exactly as laid out in `CIPAUDTY`, prefixed by the account id of the summary it belongs to. | `PAUDBLOD` `3000-READ-CHILD-SEG-FILE` (269) `ROOT-SEG-KEY`, `MOVE CHILD-SEG-REC TO PENDING-AUTH-DETAILS` | Formatting/Conversion | Business-derived | — |
| BR-05 | An authorization with a non-numeric account id is skipped silently | An authorization record whose account id is not numeric is not loaded and nothing is reported. | `PAUDBLOD` `3000-READ-CHILD-SEG-FILE` lines 275-284 `IF ROOT-SEG-KEY IS NUMERIC` with no `ELSE` | Data Selection | Business-derived | Missing `ELSE` on a business-critical decision — silent data loss, UCR-28 |
| BR-06 | An authorization is loaded beneath its account's summary | Each authorization is loaded as a child of the summary of the account named on the record; a summary that cannot be found ends the run abnormally. | `PAUDBLOD` `3100-INSERT-CHILD-SEG` (292), `3200-INSERT-IMS-CALL` (318) | Control Flow | Business-derived | — |
| BR-07 | A record already in the database is reported, not loaded again | Loading a summary or authorization that is already present is reported and skipped; the run continues. | `PAUDBLOD` `2100-INSERT-ROOT-SEG` lines 255-257, `3200-INSERT-IMS-CALL` lines 329-331 (`'II'`) | Control Flow | Business-derived | Makes the load restartable — the target load is idempotent for the same reason |
| BR-08 | Any other load failure ends the run abnormally | Any other database outcome on a load is reported with the database status and the key feedback area and ends the run abnormally. | `PAUDBLOD` `2100-INSERT-ROOT-SEG` lines 258-261, `3200-INSERT-IMS-CALL` lines 332-336, `9999-ABEND` (360) | Exception Handling | Business-derived | — |
| BR-09 | End of an input file ends that phase | Reaching the end of an input file ends that phase of the load normally. | `PAUDBLOD` `2000`/`3000` file status `'10'` | Control Flow | Business-derived | Any other read status is only displayed and the phase loops on — REVIEW REQUIRED (UCR-28) |
| BR-10 | The unload walks the whole hierarchy | The unload reads every summary in order and, for each, every authorization beneath it. | `PAUDBUNL` `2000-FIND-NEXT-AUTH-SUMMARY` (207), `3000-FIND-NEXT-AUTH-DTL` (253); `DBUNLDGS` `2000` (216), `3000` (263) | Data Selection | Business-derived | — |
| BR-11 | The unload writes summaries and authorizations to separate outputs | Summaries are written to one output and authorizations to another, each in the segment layout, so the pair can be reloaded by the load program. | `PAUDBUNL` `4000-FILE-CLOSE` (289) with the two `FD`s; `DBUNLDGS` `3100-INSERT-PARENT-SEG-GSAM` (300), `3200-INSERT-CHILD-SEG-GSAM` (319) | Formatting/Conversion | Business-derived | — |
| BR-12 | The unload ends at the end of the database | Reaching the end of the database ends the unload normally; any other read outcome is reported and ends the run abnormally. | `PAUDBUNL` `2000`/`3000` `EVALUATE DIBSTAT`; `DBUNLDGS` the same | Exception Handling | Business-derived | — |
| BR-13 | The database is defined as a HIDAM hierarchy keyed by account | Pending authorizations are stored as one summary per account with its authorizations beneath it, addressed by account id, and each authorization is ordered under its account by the complemented date and time. | `ims/DBPAUTP0.dbd` (`ACCESS=(HIDAM,VSAM)`, root `PAUTSUM0` key `ACCNTID` `TYPE=P`, child `PAUTDTL1` key `PAUT9CTS` `TYPE=C`), `ims/DBPAUTX0.dbd` (`ACCESS=(INDEX,VSAM,PROT)`, `LCHILD NAME=(PAUTSUM0,DBPAUTP0),INDEX=ACCNTID`) | Data Selection | Business-derived | `DBPAUTX0` is the HIDAM primary index over the root key, not a card-number path; it has no target artifact because the primary-key b-tree replaces it — UCR-29 |

## Exception scenarios (file and database)

| Condition | Source treatment | Target treatment |
|---|---|---|
| file status `'00'`/blank | continue | record processed |
| file status `'10'` | end of that phase | reader exhausted |
| any other file status | message only, loop continues | migration step fails with the record number |
| database status blank | success | row inserted |
| database status `'II'` | already present, skipped | `ON CONFLICT DO NOTHING` — the load stays idempotent |
| any other database status | display and abend | migration step fails, transaction rolled back |

## Target implementation

The load/unload pair has no online or service equivalent: it becomes the one-off data-migration
pipeline for the authorization context — EBCDIC extract of
`AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`, conversion to UTF-8 with packed-decimal decoding, load
into `pending_auth_summary` and `pending_auth_detail` in that order with conflict-tolerant inserts,
then reconciliation of row counts and of the summary counters against the loaded authorizations.
Pipeline, type mapping, encoding conversion and reconciliation rules are in `05-data-mapping.md`;
the DBD-to-schema mapping is commented in
`authorization-service/src/main/resources/db/migration/V1__create_authorization_schema.sql`.
