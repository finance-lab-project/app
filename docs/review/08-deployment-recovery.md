# Step 8 — deployment and recovery

Observation date: **8 November 2026**. [PR #19](https://github.com/finance-lab-project/app/pull/19)
was independently confirmed merged at
[5dc2e43](https://github.com/finance-lab-project/app/commit/5dc2e43668ea06889d5a5966b42b11bcc0fd8e3f).
The reviewed runtime remains the frozen [Step 1 baseline](01-baseline.md).

## Conclusion

The public HTTPS boundary is reachable, and the isolated encrypted recovery
pipeline passed for both the checked-in demonstration and the accepted Budget
schema. The latter preserved **all 24 table data fingerprints, relation/schema
owners and grants, and selected historical projections**. Expected restore and
notification failures were rejected.

**Current production operational acceptance remains unverified.** This step
did not inspect the private host, provider account, mailbox or recovery bundle.
The source/runbook, isolated results, prior implementation evidence and current
public observations are distinguished below. No new implementation defect was
reproduced by these checks.

[Results](evidence/step-8/results.json) retain the public observations, fixture
versions/image IDs, source hashes, table fingerprints and outcomes.
[Reproduction instructions](reproductions/step-8/README.md) retain the isolated
runner and synthetic seed. No runtime fix, production restart, backup, restore,
cutover, resource purchase or software/Zenodo publication was performed.

## Scope and provenance

| Component | Frozen source |
| --- | --- |
| App, authentication, runbook and operating scripts | [app f786827](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d) |
| Budget schema packaged in the accepted WAR | [budget 43d86d2](https://github.com/finance-lab-project/budget/tree/43d86d28965a241cd45f7938c1adfb2ec50f8132) |
| ORM packaged through Budget | [orm 8daaf91](https://github.com/finance-lab-project/orm/tree/8daaf91d07d2e30b563322b22a0c6da7545578e5) |

The five accepted bundle SHA-256 values were checked again. The runner extracts
the migration and runtime grants directly from the accepted WAR's nested
Budget JAR. Fourteen operating/configuration/check files are individually
hashed in the results. All three original source checkouts remained clean.

The [single-VM runbook](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/single-vm.md)
and all files under
[ops/single-vm](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d/ops/single-vm)
were reviewed. Production configurations are not inferred from their repository
templates. The owner already confirmed that no deployment manifest is available;
this review does not request that decision again or close S1-01.

## Current public observations

At approximately **06:58 UTC on 8 November**, five read-only requests were made
without signing in, sending a login POST, retaining cookies or accessing account
data. HTTPS requests used default certificate-chain and hostname verification.

| Request | Observed result |
| --- | --- |
| HTTP root | 308 redirect to the same hostname over HTTPS |
| HTTPS /login | 200; password and CSRF inputs present |
| HTTPS /local | 200; planner markup present |
| HTTPS /transfer, anonymous | 401 |
| HTTPS /login with Forwarded proto=http / spoof hostname and X-Forwarded-Proto=http | 200 login; spoof hostname absent from the returned page |

The hostname was [finance-lab-project.com](https://finance-lab-project.com/).
These observations establish reachability and these particular public responses,
not deployed archive identity, private workflow acceptance, origin-port isolation,
all firewall rules, certificate renewal, host configuration or availability
over time. No origin port scan or production failure experiment was run.
The browsing service could not retrieve the site; these results came from the
direct HTTP client recorded in the evidence.

## Reviewed operating controls

| Area | Checked-in design | What this review established |
| --- | --- | --- |
| Artifact activation | Check retained digests, stage an immutable release and atomically switch current with the JVM stopped | Source procedure reviewed; no actual host release or symlink inspected |
| Runtime identity | Restricted finance_app account; separate migration role and stable owner UUID | Role definitions and runtime grants exercised locally; real passwords/account file not accessed |
| Network and TLS | Caddy terminates HTTPS, overwrites proxy headers; Jetty and PostgreSQL listen on loopback; external ingress limited separately | Caddy template validation and public responses passed; actual firewall/listeners and certificate renewal unverified |
| Process supervision | App unit requires/orders after PostgreSQL, uses a 256 MiB heap, Restart=on-failure and a 15-second stop allowance | Source reviewed; no live systemd manager, reboot or automatic restart tested in this step |
| Backup scheduling | Sunday 03:00 Europe/Moscow, Persistent=true, shared flock between weekly/pre-change jobs | Calendar expression resolved to 8 November 00:00 UTC in the isolated check; actual timer enabled/last/next state not inspected |
| Encryption and integrity | pg_dump custom archive piped into age; unique ciphertext object; upload readback SHA-256; private verified receipt | Real tools and scripts passed against the local S3 emulator; provider IAM/TLS/object durability not established |
| Retention | Enabled 28-day expiration for database/ and one-day incomplete multipart cleanup | Lifecycle configuration installed/read back in emulator; actual provider configuration and elapsed expiration unverified |
| Notifications | ExecStopPost calls report-backup on success/failure; operator checks missing reports | Local SMTP capture received OK/FAILED messages; failed relay submission returned nonzero; real inbox delivery unverified |
| Recovery dependencies | Off-VM key, verified key/digest, exact release, roles/configuration and credentials | Backup user could not read the fixture key; real independent storage, access and bundle completeness not inspected |
| Logging and disk | Bound journals/PG logs, protect financial values, monitor free staging/WAL/ciphertext space | Runbook reviewed; actual rotation, alerts and free-space headroom unverified |

Parsing a calendar or validating a Caddyfile does not exercise a running timer,
service sandbox, boot ordering or public certificate renewal. The Linux fixture
ran ordinary container processes, without systemd as PID 1.

## Completed isolated checks

The repository's
[recovery.sh](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/ops/single-vm/checks/recovery.sh)
passed first on its own and again as the base of the extended review runner.
It verified ShellCheck, Caddy configuration, the timer expression, emulator
lifecycle readback, both backup reasons, ciphertext verification, local success/
failure mail, clean restore, owners/grants, unset versus zero, and rejection of
a wrong age key, damaged object and existing target.

That checked-in drill uses a demonstration history table. The new external
fixture adds the accepted Budget V1 schema and production runtime grants,
without changing the operating scripts:

| Added check | Observed outcome |
| --- | --- |
| Synthetic seed in the accepted schema | 24 tables, 47 total rows, two Budget/catalog revisions, category rename, UNSET and SET zero/value facts |
| Pre-change backup / verified receipt / fetch / decrypt | Passed using actual pg_dump, age, AWS CLI and the unchanged helpers |
| Backup user access to private age identity | Denied |
| Every Budget table's sorted-row SHA-256 and row count | All 24 matched after restore |
| Schema/relation owners and ACL fingerprint | Matched |
| Runtime-role SQL projections | Both revisions preserved original/renamed labels and income/spending/investment state/value pairs |
| Mail submission to a closed local relay port | report-backup exited 1 despite a verified backup receipt |
| Restore into a fresh cluster without saved owner role | Exited 1; new database remained, without the Budget schema |
| Retry against that existing empty database | Refused |
| Fresh-target retry after applying roles | Passed; fingerprints/projections matched |
| Fetch scratch | Ciphertext and partial file removed; validated database.dump retained only inside the disposable fixture |
| Final cleanup | Owned containers absent; no persistent fixture volumes or original source changes |

The failed import is the documented behavior: createdb is outside the import's
transaction. The helper's
[pg_restore --single-transaction --exit-on-error](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/ops/single-vm/restore#L10)
rolls back the import, while the newly created empty database can remain.
[PostgreSQL's restore documentation](https://www.postgresql.org/docs/18/app-pgrestore.html)
defines that transaction boundary. Roles and the protected release bundle must
be restored separately before a successful import.

The completed runner used PostgreSQL client/server 18.6, age 1.2.1, AWS CLI
2.23.6, Linux Bash 5.2.37, Caddy 2.6.2, systemd-analyze 257 and ShellCheck 0.10.0
on Linux aarch64, with Moto 5.1.14. Image IDs and fuller version output are in
the results. Tools came from the checked-in Dockerfile; mutable image tags and
package repositories mean this is not a hermetic toolchain build.

The combined drill container took **18.936 seconds**, excluding image building.
Its local fetch/new-cluster/negative-case/retry/SQL-validation phase took
**2.345 seconds**. Neither is a production recovery time: the fixture has tiny
synthetic data, prepared tools, local storage/emulated networking, no Java app,
no DNS/proxy cutover and no human acceptance. The schema was applied directly
from V1 SQL; Flyway's ledger, SCRAM login and the recovered browser workflow
were not executed by this new fixture.

## Rollback and evidence boundaries

The source runbook distinguishes compatible-schema rollback from data rollback.
The former retains subsequent committed writes and switches to the verified
previous application/runtime/configuration. An incompatible or uncertain schema
requires a verified pre-change backup restored to a separate clean cluster.
If later writes were accepted, the owner must decide the loss window before
data reversion. Preserving the failed cluster and keeping writes closed are
explicit steps. This is a reviewed procedure, not an executed production rollback.

[Open PR #12](https://github.com/finance-lab-project/app/pull/12) still provides
prior implementation evidence for a local staged application, restore and
current/history browser checks. Its local exercise and candidate checks are
not relabeled as new Step 8 or current provider verification.
[Step 7](07-performance-resilience.md) separately records selected confirmed
income/revision recovery after database and explicit JVM restart. That check
does not prove systemd recovery or full lost-VM recovery.

The existing source plan targets roughly **seven days plus backup completion**
of possible loss when weekly jobs succeed, and **four hours from operator
recovery start** through verified access/cutover. A missed backup enlarges the
loss window. These remain targets until current receipts and a complete timed
off-VM exercise establish the actual recoverable point and elapsed work.

## Findings and acceptance still required

### S8-01 — deployed controls and provenance remain unverified

**Operational evidence gap; includes the existing S1-01 boundary.**
Public responses do not establish the exact deployed WAR/dependencies,
configuration, runtime, roles, listeners, firewall, service overrides or release
bundle. A protected operator record should compare those with the frozen
candidate and retain only an approved sanitized summary in GitHub. Current
account credentials and personal/financial values must stay out of the report.

### S8-02 — scheduled off-VM recovery and release acceptance remain unverified

**Operational acceptance gap.**
The review has no fresh inspected evidence for the actual scheduled weekly job,
off-VM verified receipt and independently recoverable key/bundle, provider
retention/expiration, real notification delivery, complete timed lost-host
recovery, current billing period or human release acceptance.
[Issue #6](https://github.com/finance-lab-project/app/issues/6) remained open
when inspected. This states what this review verified; it does not assert that
no private operational work or records exist.

Acceptance needs the current provider/host evidence, a timed isolated recovery
from an actual verified backup through authenticated current/history access,
and the owner's release decision. The combined-memory/CPU/backup/disk capacity
boundary remains [S7-03](07-performance-resilience.md), not a new measured failure.
No threshold, new infrastructure or automatic publication is introduced here.

## Disposition

Step 8 records completed public and isolated checks plus the remaining
production evidence boundaries. Existing S2/S3/S4/S6/S7 findings remain open.
All eight technical review steps now have versioned reports; that does not
close release acceptance or publish the planned combined Zenodo report.
