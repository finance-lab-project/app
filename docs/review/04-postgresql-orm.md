# Step 4 — PostgreSQL and custom ORM integrity

Observation date: **8 November 2026**. PR #15 was independently confirmed merged
before this step. The runtime source baseline remains the one frozen in
[Step 1](01-baseline.md).

## Conclusion

PostgreSQL constraints and the scoped JDBC implementation passed the completed
atomicity, rollback, snapshot and failure-classification checks. One **medium
priority correctness finding, S4-01**, remains in Budget's category projection:
the production reader does not enforce the agreed initial-fact integrity rule.
The passing ORM projection tests use a separate test helper.

No production database was accessed. The deployed commit, artifact checksum,
schema and effective runtime grants remain unverified.

## Sources and method

| Component | Immutable reviewed source |
| --- | --- |
| Application assembly / build parent | [app f786827](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d) |
| Budget schema, adapter and projections | [budget 43d86d2](https://github.com/finance-lab-project/budget/tree/43d86d28965a241cd45f7938c1adfb2ec50f8132) |
| Scoped transactions, mappings and codecs | [orm 8daaf91](https://github.com/finance-lab-project/orm/tree/8daaf91d07d2e30b563322b22a0c6da7545578e5) |

Reviewed V1, runtime-role grants, mapping descriptors/metadata checks, codecs,
transaction lifecycle, save locks/version checks and current/historical
projections. Ran the original ORM verifier and SQL harness, then review-only
probes against the actual Budget adapter.

All writes used synthetic data in disposable PostgreSQL 18.6 containers bound
to random loopback ports, with temporary storage and cleanup on exit. Tools:
Java 27.0.1.0.3, Maven 3.10.0, psql 18.6, Python 3.9.6, macOS arm64. Tests used
Pacific/Honolulu while the database and recording coordinates used UTC.
Reviewed dependencies: JDBC 42.7.14, HikariCP 7.1.0, Flyway 13.12.0, JUnit 6.1.3.

The original ORM verifier's older Budget V1 fixture matched frozen Budget V1
byte for byte; both had SHA-256
33396e787b738b46ce9933d1ff17abacec8326430f49f96758cea466658f0f0c.
The unchanged SQL harness received that V1 through psql in a fresh database.
The separate direct-reader database was migrated and validated with production
Flyway 13.12.0; repeat migration applied zero additional migrations.

## Completed verification

| Check | Observed result | Qualification |
| --- | --- | --- |
| Original ORM unit tests | 6 passed | Codec and descriptor tests |
| Original ORM PostgreSQL integration tests | 75 passed | 44 transaction and 31 mapping cases; no failures, errors or skips |
| Original Budget SQL harness | 112 checks passed | Negative transactions compare all 24 Budget tables; deferred cases verify DML reached COMMIT |
| New direct integrity probes | 8 passed | Three characterize S4-01; five cover controls, permissions and real backend termination |
| Existing Budget implementation integration tests | 29 passed in Step 3 | Same frozen source; reused evidence, not rerun or counted as new Step 4 tests |

[Sanitized results](evidence/step-4/results.json) retain suite totals, individual
observations, revisions and harness hashes.
[Reproduction instructions](reproductions/step-4/README.md) and the external
test class are versioned alongside this report. Passing characterization
assertions establish the defect, not compliance with the integrity contract.

A pilot run had one failed test-control assertion: its diagnostic reporter
raised an assertion while the test expected Budget's read error. The corrected
control collects diagnostics and asserts the production error and three reports.
The final fresh run passed all eight probes. Production code was unchanged.

## Integrity assessment

| Property | Enforcement and evidence | Assessment |
| --- | --- | --- |
| Atomic complete aggregates | Deferred reverse foreign keys enforce required Budget/line structure, fixed lines, guard/revision links and exact SET/amount pairing. Real commit failures roll back facts, guards and revisions. | Passed completed SQL/ORM checks |
| Concurrency and no-op writes | Catalog locks precede month-guard locks; expected versions and unique owner/month claims govern saves. Step 3 races cover first/existing saves, initialization and both catalog/save orders; no-op commands append no facts. | Supported by source and reused implementation tests |
| Owner scoping | Composite keys prevent cross-owner associations; adapter reads bind owner plus aggregate identity. SQL checks cover two owners in the same month. | Passed persistence checks; authentication is Step 5 |
| Historical reads | Revision pins the catalog; lines are filtered by creation coordinate; SET requires its exact companion amount and UNSET cannot reuse an earlier amount. Aggregate recording coordinates advance by at least one microsecond. | Passed covered cases, except S4-01 |
| Numeric/time fidelity | Domain/codec checks reject invalid scale, overflow and nonfinite values; bindings preserve UUIDs, decimals and UTC microseconds. PostgreSQL numeric assignment itself can round excess scale, so pre-bind validation remains necessary. | Passed covered boundaries |
| Schema compatibility | Metadata checks require expected columns/types/nullability, ordered primary keys and validated foreign keys. Domain projection rules remain Budget-owned. | Passed mismatch tests; not a full data audit |
| Transaction lifecycle | One connection owns the callback; writes use READ COMMITTED, read-only snapshots REPEATABLE READ and end with rollback. Scope/thread violations and swallowed SQL/mapper errors prevent confirmed writes. | Passed transaction/resource tests and recovery probes |
| Append-only facts | The frozen grants allow runtime INSERT/SELECT plus guard UPDATE. Fact UPDATE and DELETE both fail with 42501 under those actual fixture grants. | Verified fixture role; deployment grants unverified |

Primary evidence:
[V1 constraints](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/resources/db/migration/V1__budget_schema.sql#L93),
[save locks](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/java/org/financelab/budget/adapter/postgres/PostgresBudgetStore.java#L98),
[history assembly](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/java/org/financelab/budget/adapter/postgres/PostgresBudgetStore.java#L178),
[transaction lifecycle](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/src/main/java/org/financelab/orm/JdbcTransactions.java#L55),
[metadata checks](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/src/main/java/org/financelab/orm/MappingSchema.java),
[codecs](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/src/main/java/org/financelab/orm/Codecs.java),
[runtime grants](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/resources/db/permissions/runtime.sql)
and [Step 3's retained implementation-test evidence](evidence/step-3/results.json).

## S4-01 — required initial category facts are not validated

**Priority: medium. Owner: Budget PostgreSQL projection. Status: reproduced,
not fixed.**

The [explicit projection contract](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/docs/api-contract.md#L301)
requires the earliest NAME, POSITION and STATUS facts to exist at the same
coordinate before deciding that a category did not yet exist. Incomplete
categories must fail even when every requested as-of slot is absent.

Production [loadCatalog](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/java/org/financelab/budget/adapter/postgres/PostgresBudgetStore.java#L264)
reads only the three as-of slots. If all are empty it skips the category;
otherwise it requires their values without checking their initial coordinates.

Each probe initialized a normal ten-category catalog and saved one monthly
revision through the real adapter, then committed one extra owned category
using runtime INSERT rights, without disabling constraints.

| Committed extra category | Actual current and pinned-history result | Required behavior |
| --- | --- | --- |
| Owner, no NAME/POSITION/STATUS | Returns 10 categories without error | Integrity error |
| Future NAME/POSITION, no STATUS | Returns 10 categories without error | Integrity error even before visible creation |
| Three facts with different initial times, spaced one microsecond apart | Accepts 11 categories without error | Integrity error |

Malformed data can therefore appear to be a valid catalog, including read-only
history. Normal saves write all three initial facts atomically: these probes
do **not** show ordinary saves producing corruption or actual production damage.

Controls narrow the defect: visible NAME/POSITION without STATUS correctly
fails catalog, current-month and historical reads. A valid category added through
saveCatalog appears currently while remaining absent from an older pinned catalog.

The ORM's [test-only category helper](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/src/test/java/org/financelab/orm/AnchorMappingIT.java#L62)
validates initial coordinates. Its passing malformed-fixture tests call that
helper, not PostgresBudgetStore. A focused follow-up should enforce the agreed
rule in Budget's reader and assert failure for these three fixtures against
that reader, while preserving the valid later-category control. This finding
does not require a generic ORM entity-creation abstraction or schema redesign.

## Real connection termination and commit outcomes

Two probes inserted a synthetic row, obtained their connection's backend PID
and terminated only that backend from a fixture administrator connection.
PostgreSQL's [pg_terminate_backend with a positive timeout](https://www.postgresql.org/docs/18/functions-admin.html#FUNCTIONS-ADMIN-SIGNAL)
waits for termination.

| Failure point | Observed classification | Persistence and recovery |
| --- | --- | --- |
| Backend terminated, next callback statement fails before commit | NOT_COMMITTED, 57P01 | Zero rows; one callback call; next pooled read succeeds |
| Backend terminated, callback returns, commit fails | UNKNOWN, 57P01 | Zero rows observed externally; one callback call; next pooled read succeeds |

The second result is conservative: an unrecognized connection failure during
commit does not establish its outcome to the transaction layer. Neither callback
was retried; both cases ended with zero active pool leases.

These extend original ORM tests for real deferred rejection at COMMIT,
simulated acknowledgement loss before/after a real delegate commit and confirmed
commits followed by cleanup failure. They close Step 3's evidence gap for actual
connection termination and pool recovery. Wire-level packet loss after a real
commit was not tested; the after-commit case remains a driver-wrapper injection.

## Limits and follow-up boundary

This step records S4-01 without implementing fixes or changing the earlier
S2/S3 findings. Runtime source, migrations, deployment and production data were
unchanged. Effective deployed roles, backup/restore and operational recovery
remain Step 8 work; load/outage measurements remain Step 7. No releases, packages
or publication were created. Continue only on a separate request.
