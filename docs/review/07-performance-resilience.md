# Step 7 — performance and resilience

Observation date: **8 November 2026**. PR #18 was independently confirmed merged.
Runtime source remains the frozen [Step 1 baseline](01-baseline.md).

## Conclusion

The completed isolated run shows **category-dependent query growth, an executing
query/lock deadline gap, and a remaining target-environment validation gap**.
Both controlled restart probes recovered the selected confirmed income and
revision. These observations do not establish production capacity, availability
or recovery-time guarantees.

[Results](evidence/step-7/results.json) retain every measured latency,
prepared-statement count and sampled Jetty RSS value.
[Reproduction instructions](reproductions/step-7/README.md) retain the fixture.
No production load, runtime fix, deployment or software/Zenodo publication was
performed.

## Sources, artifacts and environment

| Component | Reviewed source |
| --- | --- |
| App and authentication | [app f786827](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d) |
| Budget | [budget 43d86d2](https://github.com/finance-lab-project/budget/tree/43d86d28965a241cd45f7938c1adfb2ec50f8132) |
| ORM | [orm 8daaf91](https://github.com/finance-lab-project/orm/tree/8daaf91d07d2e30b563322b22a0c6da7545578e5) |

The five accepted WAR/authentication/runtime-dependency/configuration/parent
digests match Steps 1, 3, 5 and 6. The measurement process's Budget and ORM JARs
were checked byte for byte against the WAR's nested copies. The runner compiled
an external measurement class and the original synthetic-account fixture;
production classes, launcher and accepted archive bytes were unchanged. All
three original source checkouts remained clean at their frozen commits.

The run used macOS arm64, 12 physical/logical CPU cores and 24 GiB host RAM.
Docker's Linux aarch64 host reported 12 CPUs and approximately 7.75 GiB RAM.
Versions: Java 27.0.1.0.3, Maven 3.10.0, Jetty 12.1.14, PostgreSQL 18.6,
Flyway 13.12.0, HikariCP 7.1.0, PostgreSQL JDBC 42.7.14, Playwright 1.64.0,
Python 3.9.6 and Docker 29.4.0. Previously prepared dependency and browser caches
were reused; this step performed no frontend build.

Jetty used a **256 MiB maximum heap**, PostgreSQL a **256 MiB container memory
cap**. Application connections used the real runtime role and packaged migration/
runtime grants over a fixed loopback TCP endpoint. Disposable localhost TLS and
the actual application authentication protected browser requests. All accounts
and financial values were synthetic. Raw logs, account files and machine paths
remain local.

## Measurement method

There is no supplied latency, throughput or recovery-time acceptance target for
this review. These are exploratory observations from one completed local run.

- Three independent persistence fixtures cover 10 categories / 12 months /
  24 revisions per month; 50 categories / 12 months / 24 revisions; and
  10 categories / one month / 240 revisions. Each seed revision changes income
  and spending. Extra owners isolate database profiles; browser traffic uses
  the single configured account.
- Each persistence operation has five warmups and 30 recorded samples, timed
  with a monotonic clock. Save preparation reads are excluded. The write phase
  adds 35 revisions, taking the selected month from 24 to 59, or 240 to 275.
  Catalog values are not historized repeatedly during this seeding.
- A JDBC proxy counts prepared-statement executions in the direct persistence
  process. Counts exclude SET LOCAL timezone setup and driver-generated
  transaction commands. Instrumentation contributes overhead. Browser RPCs
  use the actual, unmodified application pool.
- Browser warm operations explicitly select the saved month January 2040.
  Both account profiles have five warmups and 30 recorded RPC samples. Save
  timing excludes preparation/follow-up reads and client-side form rendering;
  it is not the complete click-to-editor-ready workflow.
- Four separate authenticated browser contexts each perform 15 reads, following
  three untimed warmups. Their loops are synchronized and overlap. These
  60-request bursts are finite closed loops, without user think time.
- Percentiles use **nearest rank**, including p50 (the JSON median_ms field).
  Small samples, warm caches, sequential profile order and background host work
  limit comparisons. No confidence interval or stable tail-latency claim is made.

Three pilot fixture failures were diagnosed and corrected before the completed
run: a browser scheduler timestamp type, an automatically allocated Docker port
changing after restart, and the parent not reaping the killed child JVM.
Their measurements are excluded from the published result set; none establishes
an application defect or lost data.

## Observed performance

### Direct persistence operations

Every entry is **p50 / p95 milliseconds; prepared statements per call**.
All 30 recorded calls for each operation had the same statement count.

| Operation | 10 categories, 24 revisions/month | 50 categories, 24 revisions/month | 10 categories, 240 revisions |
| --- | --- | --- | --- |
| Catalog | 15.78 / 17.93; 32 | 73.53 / 89.08; 152 | 16.35 / 18.58; 32 |
| Saved current month | 28.70 / 33.19; 60 | 126.76 / 134.47; 260 | 29.29 / 36.21; 60 |
| Revision list | 1.69 / 3.64; 1 | 1.77 / 3.12; 1 | 1.75 / 2.99; 1 |
| First historical revision | 30.26 / 32.74; 60 | 125.83 / 139.08; 260 | 33.02 / 47.35; 60 |
| Atomic save | 39.34 / 46.86; 84 | 176.83 / 192.52; 364 | 41.47 / 49.37; 84 |

The 240-revision fixture does not demonstrate unbounded-history behavior or
catalog-history scaling. It contains one month, while the other fixtures
contain 12, and runs later with different cache/JIT state.

### Browser and application path

| Operation | 10 categories: p50 / p95 ms | 50 categories: p50 / p95 ms |
| --- | --- | --- |
| Warm saved-month RPC | 37.41 / 44.42 | 136.25 / 144.17 |
| Save RPC, excluding follow-up read | 38.68 / 42.76 | 136.82 / 148.36 |
| Four-session read burst, 60 reads | 62.50 / 81.80 | 236.30 / 260.20 |

Both four-session bursts returned zero RPC errors. Their measured spans were
964 ms and 3,566 ms, approximately 62.24 and 16.83 completed reads/second.
These are achieved rates for these bursts, **not maximum or sustainable RPS**.
The write profiles differ in revision/catalog state from the direct process;
the two timing sets are not interchangeable.

Five fresh-context page loads produced the following p50 / maximum values:
guest planner **52.06 / 190.26 ms**, valid login POST **31.02 / 64.33 ms**, and
authenticated planner **124.21 / 306.60 ms** excluding its preceding login.
Planner readiness means the save button is enabled. Browser-process startup
is excluded; DNS/TLS and the running application were already warm. These page
loads open the then-current unsaved calendar month, not the seeded saved month.

### Dump overlap and memory

One backup-role pg_dump completed in **182.78 ms**, producing **424,629 bytes**.
Two direct current reads started while the dump process was alive, taking
129.04 and 130.81 ms. The duration includes Docker command/stream overhead, and
the second read may finish after the dump. The tiny archive and two overlapping
reads do not establish backup impact under representative production history.
Encryption, upload, restore and production backup authentication were not tested.

Across 397 half-second samples after readiness over approximately 202 seconds, peak observed
Jetty RSS was **358,576 KiB (350.17 MiB)**. This is sampled process RSS rather
than heap usage or combined service memory, and can miss short peaks. It
excludes the browser, 384 MiB-heap measurement JVM, database, Docker VM, proxy
and OS. Closed browser contexts may leave idle server sessions until expiry;
this run does not establish a leak, memory headroom or long-term stability.

## Controlled resilience observations

| Probe | Observed result | Boundary of the evidence |
| --- | --- | --- |
| Catalog lock held during authenticated save | Real PostgreSQL lock wait confirmed; save still pending after 6.5 seconds. After release it returned Saved at 6,683.80 ms and added exactly one revision. | Executing work can outlast the 5,000 ms pool-acquisition timeout. No pool-exhaustion or deadlock probe was run. |
| PostgreSQL stop/start with retained disposable volume | Protected read returned error=unknown in 16.52 ms; anonymous guest route returned HTTP 200. Database ready at 195.04 ms after explicit start; first recovery read succeeded at 491.23 ms, on the first explicit read attempt. Selected income and revision matched. | Local container stop/start, not database crash, network blackhole, host loss, restore or full financial-data comparison. The 200 response does not test the full guest workflow during outage. |
| Force-kill owned Jetty JVM, then explicitly launch the same accepted application | Old session returned HTTP 401. Re-login, planner readiness and selected income/revision check completed 2,103.19 ms after the explicit launch began. A synthetic browser localStorage value remained byte-identical. | Harness-managed restart; excludes failure detection and kill/wait time. Automatic systemd recovery, unsaved editor recovery and all browser state were not tested. |

Recovery reads were explicitly issued by the harness. No automatic save retry
or write replay was introduced. These probes do not resolve the shared-editor
save/read failure behavior recorded in [Step 3](03-workflows.md).

## Findings and bounded follow-ups

### S7-01 — category-dependent projection cost

**Measured scaling constraint; optimization candidate, not an SLO failure.**
For a saved month with C active persisted spending categories, catalog reads
used 3C + 2 prepared statements and current-month projection used 5C + 10:
32/60 statements at 10 categories, 152/260 at 50. The source performs three
as-of fact reads per category and two per persisted line, in
[loadCatalog/loadBudget](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/java/org/financelab/budget/adapter/postgres/PostgresBudgetStore.java#L264).
Inactive categories, other persisted lines or different operations can change
these counts.

The uninstrumented browser measurements also increased with category count.
A future Budget-owned bulk projection could reduce round trips while retaining
the chosen ORM and owner/snapshot/as-of semantics. Any such work must preserve
historical facts and include the [S4-01 integrity cases](04-postgresql-orm.md).
No optimization is implemented by this review.

### S7-02 — executing query and lock waits lack an explicit deadline

**Medium resilience gap.**
The source configures four connections and a 5,000 ms connection-acquisition
limit in [ApplicationLifecycle](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/src/main/java/org/financelab/app/ApplicationLifecycle.java#L32).
The local runtime role reported statement_timeout=0 and lock_timeout=0;
the direct JDBC connection reported network timeout=0 and a new statement's
query timeout=0. The actual server save remained blocked beyond 6.5 seconds
until the harness released its catalog lock.

[HikariCP connectionTimeout](https://github.com/brettwooldridge/HikariCP#frequently-used)
bounds waiting for a connection from the pool, not execution after acquisition.
[PostgreSQL statement_timeout and lock_timeout](https://www.postgresql.org/docs/18/runtime-config-client.html#GUC-STATEMENT-TIMEOUT)
are disabled at zero. Thus the reviewed defaults supply no explicit query/lock
deadline for this stall. Actual deployed role/JDBC overrides remain unverified.

A follow-up should choose a runtime-role/per-operation timeout and cancellation
policy, then verify transaction cleanup, pool recovery and the existing
NOT_COMMITTED versus UNKNOWN distinction. Unknown commit outcomes must retain
the no-automatic-write-replay rule. This finding does not assert an agreed
response-time target was breached or that all four connections were exhausted.

### S7-03 — constrained Linux capacity remains unverified

**Validation gap.**
The frozen [sizing plan](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/architecture.md#L166)
allocates two vCPUs at 20% guaranteed performance and 4 GiB RAM for the combined
proxy, application, PostgreSQL and host. The
[runbook's capacity check](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/single-vm.md#L15)
requires representative history, workload during backup, combined memory/swap,
CPU contention and disk/staging headroom. The macOS/Docker run does not emulate
that CPU guarantee or measure the combined Linux service.

Capacity acceptance still needs owner-defined workload/latency/recovery targets
and a comparable constrained Linux measurement with the complete backup path.
This report cannot confirm or reject the proposed VM sizing, actual deployed
settings, automatic recovery, encrypted off-VM backups, WAN/proxy behavior,
network interruption handling, disk pressure or sustained/soak limits.
Operational checks belong to the separately requested Step 8.

## Verification and disposition

The completed command was python3 docs/review/reproductions/step-7/run.py with
the frozen checkout, accepted outputs and isolated runtime paths specified by
the reproduction guide. All three direct profiles, both browser profiles,
60 reads per four-session burst, the dump and all three resilience probes
completed. The runner's completed flag is true.

After completion, the dedicated container and named volume were absent, the
restarted Jetty process was gone, accepted hashes still matched, and all three
original source checkouts were clean. JSON sample sizes, nearest-rank summaries
and statement ranges were independently recalculated before publication.

Existing S2/S3/S4/S6 findings remain open; earlier suites were not rerun or
reclassified as new Step 7 evidence. This PR records measurements and follow-up
boundaries only. No software release, package, registry or Zenodo record was
created.
