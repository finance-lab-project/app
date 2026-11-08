# Step 5 — security boundaries

Observation date: **8 November 2026**. PR #16 was independently confirmed merged.
This step retains the frozen [Step 1](01-baseline.md) runtime baseline.

## Conclusion

**No new reportable security vulnerability was found** in the reviewed source
and completed isolated tests. Authentication, owner scoping, session lifecycle
and the tested input/output boundaries behaved as intended. This is not a
security certification or a production-readiness claim. Earlier S2/S3/S4
correctness findings remain unchanged.

The [generated security report](evidence/step-5/security-scan.md) retains the
threat model, reviewed surfaces, control dispositions and open questions.
[Results](evidence/step-5/results.json),
[source coverage](evidence/step-5/source-coverage.json) and
[reproduction instructions](reproductions/step-5/README.md) provide the public
evidence. The generated report's artifacts/step-5 labels identify retained local
audit records; the public runnable class, runner and results are linked here.

## Scope and sources

Static coverage is **all 73 tracked files** of
[app f786827](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d),
including authentication, Servlet/Vaadin entry points, configuration, operations,
CI, tests, examples and current-tree credential/private-key exposure.
An independent baseline audit, separate architecture/resource review and focused
boundary investigation were reconciled with parent source checks and runtime tests.

[Budget 43d86d2](https://github.com/finance-lab-project/budget/tree/43d86d28965a241cd45f7938c1adfb2ec50f8132)
and [ORM 8daaf91](https://github.com/finance-lab-project/orm/tree/8daaf91d07d2e30b563322b22a0c6da7545578e5)
were supporting sources for reachable identity, transfer, rendering and
persistence paths. This is not a separate complete security audit of either
library, an advisory-database scan or an audit of all dependency internals.

All write tests used synthetic accounts/data in disposable loopback Jetty and
PostgreSQL instances. No production service, real credential or private operator
record was accessed. Runtime source and packaged WAR were not patched.

## Completed verification

| Check | Observed result |
| --- | --- |
| App/authentication unit cases | 7 passed in each clean build |
| Packaged WAR security/browser case | 1 passed, including an invalid Vaadin CSRF token |
| Original planner journeys | 2 passed |
| Original authentication phases | Boundary, credential rotation/restart, idle expiry and absolute expiry all passed |
| New security probes | 5 passed; details below |
| Two clean production builds | WAR and authentication JAR byte-identical |
| Baseline artifact comparison | All five SHA-256 values matched earlier frozen-baseline verification |
| Startup/shutdown | Missing account failed closed; normal startup worked without the test TLS keystore; test services stopped |

Environment: Java 27.0.1.0.3, Maven 3.10.0, Node 24.22.2/npm 11.19.1, Jetty 12.1.14
EE11, PostgreSQL 18.6, Playwright Java 1.64.0 and packaged Flow server 25.3.7,
on macOS arm64. TLS used a disposable localhost certificate.

A pilot had one test-control failure: a container error response used the valid
combined header must-revalidate,no-cache,no-store, while the probe expected
exactly no-store. The corrected assertion checks that directive. The final
fresh verifier run exited zero and passed all cases; no product change was needed.

## Boundary results

| Boundary | Source control and observed result |
| --- | --- |
| Anonymous access | Raw-path allowlist, secure-request gate and owner principal/role checks. Thirteen protected/alias paths stayed closed despite forged identity headers; four public reads worked and three public writes were denied. |
| Native CSRF | Session-bound random tokens; supplied account Origin must match, transfer requires exact Origin plus token and form shape. Cross-session/stale tokens, foreign/null origins and malformed transfer requests were rejected. |
| Account identity | Owner UUID comes from the container principal and is rechecked by BudgetService. Forged owner/budget fields still returned the account's own month and history. A foreign category save was rejected without changing either owner's values or revision counts. |
| Browser output and SQL values | Shared UI uses textContent/value; persistence binds values. An HTML/SQL-looking stored label round-tripped literally, created no injected element, ran no script and left the catalog readable. |
| Vaadin RPC and logout | Missing RPC token produced the framework security-error response. Logout cleared its cookie; replaying an old authenticated cookie was denied. The original WAR case separately rejected a forged token. |
| Password/session lifecycle | Bounded owner-only verifier file, Argon2id, no plaintext password retained in authenticated sessions, session/token renewal and secure cookie attributes. Original tests passed throttling, expiry and operator rotation/restart. |
| Operator authority | Source separates application, schema migration, backup and recovery roles. Backup encryption/staging, receipts and off-VM recovery identity were traced to their actual consumers. These are source-level controls and operator obligations; deployed enforcement was not attested. |

Primary source anchors:
[authentication filter](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/src/main/java/org/financelab/app/AuthenticationFilter.java#L29),
[account lifecycle](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/src/main/java/org/financelab/app/AccountServlet.java#L30),
[owner identity](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/src/main/java/org/financelab/app/OwnerIdentity.java#L18),
[transfer guard](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/src/main/java/org/financelab/app/TransferServlet.java#L22),
[Budget identity recheck](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/java/org/financelab/budget/application/BudgetService.java#L24),
[label rendering](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/src/main/resources/META-INF/resources/ui/local.js#L3)
and [typed statement binding](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/src/main/java/org/financelab/orm/JdbcTransactions.java#L185).

## Accepted design choices and remaining uncertainty

- The [documented global cooldown](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/authentication.md#L26)
  deliberately trades new-login availability for bounded guessing/hash work.
  Sustained session exhaustion and load were not measured.
- Guest browser storage is plaintext origin/profile data, as disclosed in the
  privacy page. Control of that browser profile already grants access to it.
- Production Caddy overwrites proxy metadata and Jetty binds to loopback. The
  [production service](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/ops/single-vm/finance-lab.service#L10)
  explicitly sets forwardedOnly; local run.sh differs. Local TLS tests do not
  establish public proxy sanitization or host isolation.
- The [authentication document](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/authentication.md#L113)
  still says Budget/database integration is future work. Actual source wiring
  and the completed tests establish that it is integrated at this baseline.
  Older no-deployment statements likewise do not identify current production.
- Deployed commit/checksum, effective network and database grants, external
  storage IAM, mail TLS/delivery and recovery-key custody remain unverified.
  Performance/exhaustion measurements belong to Step 7; live configuration and
  backup/recovery evidence belong to Step 8.

This step records review results only. No runtime fix, deployment, release,
package or Zenodo publication was performed. Continue on a separate request.
