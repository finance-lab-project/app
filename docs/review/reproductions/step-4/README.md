# Reproduce Step 4 persistence checks

Requirements: Docker, Java 27, Maven 3.10.0, psql 18.6, Python 3 and curl.
Use disposable local resources and fresh frozen checkouts. Set JAVA_HOME to
the installed JDK. The runner creates its own server and accepts no existing
database URL.

From an app checkout containing this review:

    review_dir="$(pwd)/docs/review/reproductions/step-4"
    work="$(mktemp -d)"
    git clone --quiet https://github.com/finance-lab-project/app.git "$work/app"
    git clone --quiet https://github.com/finance-lab-project/orm.git "$work/orm"
    git clone --quiet https://github.com/finance-lab-project/budget.git "$work/budget"
    git -C "$work/app" checkout --quiet f78682778eebed86730ec457f63c96648f19370d
    git -C "$work/orm" checkout --quiet 8daaf91d07d2e30b563322b22a0c6da7545578e5
    git -C "$work/budget" checkout --quiet 43d86d28965a241cd45f7938c1adfb2ec50f8132
    export REVIEW_APP_DIR="$work/app"
    export REVIEW_ORM_DIR="$work/orm"
    export REVIEW_BUDGET_DIR="$work/budget"
    export REVIEW_EVIDENCE_DIR="$work/evidence"
    export PATH="$JAVA_HOME/bin:$PATH"
    bash "$review_dir/run.sh"
    (cd "$REVIEW_ORM_DIR" && bash scripts/verify.sh) > "$REVIEW_EVIDENCE_DIR/orm-verify.log" 2>&1

Maven downloads dependencies as needed; MAVEN_ARGS may select caller-owned
settings and an isolated cache. The observed review used its own settings/cache.

The runner verifies commits and clean sources, installs the reviewed parent and
ORM locally, and compiles Budget plus the external review class. It starts one
PostgreSQL 18.6 container on a random loopback port and removes it on exit.
Trust authentication and public fixture role names are only for that temporary
server. Two fresh databases are created:

- schema_check receives frozen V1 through psql and runs unchanged
  Budget verification/check.py: **112 checks**. The older Flyway/JDBC versions
  in the separate verification POM are not used.
- integrity is migrated and validated by **Flyway 13.12.0** with Budget's
  production migration resource. The runtime role receives the exact frozen
  runtime.sql grants. **8 cases** call actual PostgresBudgetStore/JdbcTransactions.
  Administrator rights are used only for setup and the test's own backend termination.

The second command runs unmodified ORM scripts/verify.sh: **6 unit + 75
integration tests** against another automatically cleaned disposable server.
It checks the downloaded migration fixture's hash; the observed fixture also
matched frozen Budget V1 byte for byte.

| Direct-probe case | Characterization asserted |
| --- | --- |
| bare | S4-01: owned category without required facts is silently omitted |
| futureIncomplete | S4-01: missing STATUS is ignored when other facts follow the cutoff |
| differentInitialTimes | S4-01: mismatched first coordinates are accepted |
| visibleMissingStatus | Control: adapter rejects visible incomplete category and reports diagnostics |
| legitimateLaterCategory | Control: current catalog has 11 categories, pinned history has 10 |
| runtimeFactPermissions | Fact UPDATE and DELETE fail with 42501 |
| terminateBeforeNextStatement | Actual backend termination before commit: NOT_COMMITTED |
| terminateBeforeCommit | Actual backend termination followed by commit failure: UNKNOWN |

Termination cases assert zero persisted probe rows, one callback invocation,
a successful subsequent pooled read and zero active leases. Only the backend
PID obtained from that callback is terminated.

**Passing S4-01 cases reproduce incorrect behavior.** Fix regression tests should
assert integrity failure for the three malformed categories and preserve the
valid later-category control.

The external Java class is compiled into Budget's ignored target/test-classes.
No production source or packaged runtime is patched. Raw Maven/JUnit logs stay
local because they can contain paths and connection details; share sanitized
totals and STEP4 JSON observations as in
[results.json](../../evidence/step-4/results.json). Clean-source checks do not
remove caller files or generated output.

These checks do not establish deployed schema/grants, packet loss after commit,
privileged tamper resistance or backup recovery. Budget's 29 implementation
integration tests are reused from Step 3, not rerun here.
