# Reproduce Step 7 performance and resilience measurements

Use a local isolated environment. Requirements: Java 27.0.1.0.3, Maven 3.10.0,
Jetty 12.1.14, Python 3, Docker and the Playwright browser cache installed by the
[frozen app verifier](../../../build.md). No Node/npm build is performed here.
Use a clean full app checkout at f78682778eebed86730ec457f63c96648f19370d with its
accepted packaged outputs already built and verified.

    export REVIEW_APP_DIR=/path/to/frozen-verified-app
    export REVIEW_EVIDENCE_DIR=/path/to/new-step-7-evidence
    export JAVA_HOME=/path/to/jdk-27.0.1.0.3
    export JETTY_HOME=/path/to/jetty-home-12.1.14
    export PATH="$JAVA_HOME/bin:$PATH"
    export MAVEN_ARGS="--settings /path/to/private-review-settings.xml"
    python3 docs/review/reproductions/step-7/run.py

Use a fresh evidence directory outside the tracked source tree. The observed run
used its previously prepared isolated Maven cache/settings. Public dependencies
may be resolved if missing; no hosted application or production database URL is
accepted. The runner overwrites APP connection/account/origin settings with its
own loopback fixture.

## Fixture and provenance

The Python driver verifies the frozen commit, clean source, exact tool versions
and five accepted app/authentication/configuration/parent SHA-256 values. It
clones the source, copies those accepted binary bytes and validates the harness's
Budget/ORM dependency JARs against the nested WAR JARs. It compiles the external
measurement class and the original synthetic-account fixture; production code
and WAR bytes are not patched.

Jetty runs the unchanged launcher with a 256 MiB maximum Java heap, a disposable
localhost certificate and the actual authentication configuration. PostgreSQL
18.6 has a 256 MiB container memory cap, the production migration/runtime grants
and an owned disposable Docker volume. Its loopback port is explicitly fixed for
the probe, then checked across stop/start. The volume preserves data through
restart and is removed at final cleanup.

All test accounts, labels and amounts are synthetic. Browser traffic uses the
single configured owner. Two additional owner-scoped database fixtures separate
the persistence profiles; they are not additional supported login accounts.

## Measurements

- Persistence profiles: 10 categories / 12 months / 24 revisions per month;
  50 categories / 12 months / 24 revisions per month; and 10 categories / one
  month / 240 revisions. Every seed revision updates income and spending.
- Each profile measures catalog, saved current month, history list, first
  revision and atomic save. Each has five warmups and 30 recorded samples.
  Save preparation reads are outside the measured save interval. Save warmups
  and samples add 35 versions to the selected month.
- JDBC proxies count prepared-statement executions, excluding SET LOCAL timezone
  setup and driver-generated transaction commands. They also add instrumentation
  overhead; browser RPC measurements use the unmodified application pool.
- Five fresh browser contexts measure guest readiness, successful login POST and
  authenticated planner readiness. Browser-process startup is excluded. These
  page loads open the then-current unsaved calendar month; seeded saved-month
  operations below explicitly select January 2040.
- The 10- and 50-category account profiles measure 30 warm current-month RPCs and
  30 save RPCs after five warmups. Save timing excludes the preparation/follow-up
  reads and client-side form rendering.
- Four separate authenticated browser contexts perform 15 current-month reads
  each after three untimed warmups. The 60-read loops are synchronized and checked
  for overlap. This is a finite closed-loop observation, not sustained capacity.
- One pg_dump under the backup role overlaps direct current reads. The archive
  is synthetic and removed; age encryption, upload, provider behavior and restore
  are not included.
- Jetty RSS is sampled every half second during the harness. It excludes the
  browser, driver JVM, Docker VM/database, proxy and OS, and can miss short peaks.
  Closed browser contexts may leave server-side idle sessions until expiry.

Latency percentiles use nearest rank, including the reported p50/median field.
All raw latency and statement-count samples are retained. Thirty warm samples
and five page loads do not establish statistically stable tail latency.

## Controlled failures

The Java class verifies ownership of the Docker fixture before controlling it.

1. Hold the configured owner's catalog row lock. Start an authenticated save,
   confirm a real PostgreSQL lock wait, observe it still pending after 6.5 seconds,
   then release the lock. Require one confirmed save and one added revision.
2. Stop the database container. Observe the protected read result and anonymous
   guest availability, restart the same container/volume and require the selected
   income and revision to be readable again. Only explicit harness reads retry;
   no automatic write replay is added.
3. Verify the original Jetty process identity, kill that owned JVM, restart the
   same accepted application and require the old cookie to be rejected. Re-login,
   then verify selected confirmed income/revision and the browser's local value.

The main measurement process has a ten-minute watchdog. Cleanup stops owned
JVMs and removes the fixture container/volume and temporary checkout. Raw logs,
certificates, account files, cookies and machine paths remain local.

Three pilot fixture issues were corrected before the completed run: the browser
scheduler needed a Java Double timestamp rather than Long, and Docker changed
an automatically allocated port after restart. The final run uses a stable
explicit port. The parent watcher also reaps the killed JVM so the sibling Java
probe can observe its exit. Pilot failures are not application data-loss evidence
and are excluded from the final measurement set.

See [results.json](../../evidence/step-7/results.json) for the measured host,
resource settings, raw samples, observed failures/recovery and scope limits.
This fixture does not emulate the planned two-vCPU/20%-guaranteed/4-GiB Linux VM,
public TLS/proxy/WAN conditions, a complete encrypted backup, or a soak/exhaustion
test. No production capacity, uptime, recovery-time or latency SLO is certified.
