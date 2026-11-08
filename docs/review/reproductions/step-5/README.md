# Reproduce Step 5 security-boundary probes

Requirements: the pinned Java 27.0.1.0.3, Maven 3.10.0, Node 24.22.2/npm 11.19.1,
Jetty 12.1.14, Docker, Python 3, curl and the verification tools described in
[the build guide](../../../build.md). Tests create disposable localhost TLS,
PostgreSQL 18.6 and synthetic credentials. They do not accept a production URL.

Keep this review directory separate from a clean frozen app checkout:

    review_dir="$(pwd)/docs/review/reproductions/step-5"
    work="$(mktemp -d)"
    git clone --quiet https://github.com/finance-lab-project/app.git "$work/app"
    git -C "$work/app" checkout --quiet f78682778eebed86730ec457f63c96648f19370d
    export REVIEW_APP_DIR="$work/app"
    export REVIEW_EVIDENCE_DIR="$work/evidence"
    export JAVA_HOME=/path/to/jdk-27.0.1.0.3
    export JETTY_HOME=/path/to/jetty-home-12.1.14
    export PATH="$JAVA_HOME/bin:/path/to/node-v24.22.2/bin:$PATH"
    (cd "$REVIEW_APP_DIR" && bash scripts/prepare-budget.sh)
    bash "$review_dir/run.sh" > "$work/security-verifier.log" 2>&1

The preparation command builds the exact pinned Budget and ORM sources into the
local Maven repository until the registry is available. The observed review reused
the same already-built dependencies in its isolated cache. MAVEN_ARGS may select
caller-owned settings/cache. Default loopback test ports are 18083/18084; set
APP_HTTP_PORT and APP_TEST_TLS_PORT before execution if needed. This review used
different free loopback ports to avoid other local previews.

The runner checks frozen HEAD and clean sources, then derives a temporary driver
from the unchanged app verifier. It preserves both clean production builds,
unconfigured-account rejection, migration/runtime grants, the original WAR and
planner tests, all four authentication phases, shutdown and normal startup checks.

Its only additions compile SecurityReviewIT into ignored target/test-classes,
run it before the original authentication boundary test triggers the global
cooldown, and retain each authentication phase's JUnit result separately. Neither
the packaged WAR nor production source is patched.

Expected new cases:

| Case | Checked boundary |
| --- | --- |
| anonymousBoundary | 13 protected/alias paths and forged identity headers stay closed; four public reads work; three public writes are denied |
| nativeCsrf | Cross-session and stale tokens, foreign/null origins, missing transfer Origin, JSON content and transfer query parameters are rejected |
| ownerIsolation | Forged owner/budget fields still read the configured owner's month/history; a foreign category cannot be saved; both owners' values/history stay unchanged |
| literalCategoryRendering | An HTML/SQL-looking category label round-trips literally, without creating markup, executing script or damaging the catalog |
| vaadinCsrfAndLogoutReplay | A missing Vaadin token produces the framework security-error response; logout clears the cookie; the old authenticated cookie cannot replay RPC |

The owner probe seeds a second owner UUID in the disposable database to verify
logical scoping; it does not introduce a second supported login account. The
label probe restores the prior catalog in a finalizer.

Expected final results: **7 unit cases**, **1 WAR case**, **2 planner journeys**,
**4 authentication-phase runs** and **5 new probes**, all passing. Two clean
WAR/authentication archives must match byte for byte. The final five digest
values are retained in results.json and matched earlier baseline verification.

An initial probe assertion expected exactly no-store; a container error page
correctly supplied a combined Cache-Control header. The corrected probe checks
for the no-store directive. The final fresh run passed all cases.

Raw logs, cookies, account files, certificates and JUnit properties stay local.
Share sanitized totals/STEP5 JSON observations as in
[results.json](../../evidence/step-5/results.json). Test services and temporary
credentials are removed by the original verifier's cleanup; generated checkout
build files remain until the caller removes the disposable work directory.
