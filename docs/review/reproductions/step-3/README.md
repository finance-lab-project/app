# Step 3 workflow reproductions

These review-only checks use the frozen application and synthetic data.
They do not change production sources, the packaged WAR or application routes.

The class contains assurance checks and issue-characterization checks.
A passing characterization check means the described limitation was reproduced,
not that it has been fixed.

Assurance cases cover active-planner validation/navigation/history, stale month
and catalog saves across authenticated sessions, and retry after a local
acknowledgement failure. The characterization cases establish these limitations:

- A confirmed account Save followed by a forced planner-read failure blocks the
  editor although a second authenticated browser sees the committed revision.
- Rapidly reopening the unsaved-change dialog lets an older queued close event
  consume the new prompt's handler. A separate normal-navigation comparison
  waits for the preceding close event to finish.
- A replay stopped before its second commit keeps the first account revision;
  an explicit retry merges the latest snapshot and does not replay the missing
  middle snapshot. Browser originals are retained by the normal transfer flow.

## Preconditions

Use Java 27.0.1.0.3, Maven 3.10.0, Node 24.22.2/npm 11.19.1,
Jetty 12.1.14 and Docker with PostgreSQL 18.6 available. Follow the
[frozen build guide](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/build.md)
for tool setup. Set JAVA_HOME, JETTY_HOME and PATH for those versions.

Keep this reproduction directory outside the app checkout. Use a clean
application checkout at f78682778eebed86730ec457f63c96648f19370d and a clean
Budget checkout at 43d86d28965a241cd45f7938c1adfb2ec50f8132.
Budget's prepare-orm.sh supplies ORM at 8daaf91d07d2e30b563322b22a0c6da7545578e5.

## Commands

Replace the example paths with separate local checkouts/output directories.

```sh
# First verify Budget and install the tested module for application assembly.
cd /path/to/budget
scripts/verify.sh
mvn -B -ntp install -DskipTests -DskipITs \
  -Dsource.revision=43d86d28965a241cd45f7938c1adfb2ec50f8132

# Run the original app verifier plus the additional review class.
REVIEW_APP_DIR=/path/to/app \
REVIEW_EVIDENCE_DIR=/path/to/step-3-output \
APP_HTTP_PORT=28113 APP_TEST_TLS_PORT=28114 \
bash /path/to/this-reproduction/run.sh
```

Choose unused loopback ports if those examples are occupied. The runner uses the
original app verifier's disposable PostgreSQL container, local test certificate,
synthetic login, configured startup and cleanup. The test certificate is not
evidence about public HTTPS. The runner requires the frozen app commit and a
clean tracked/untracked working tree.

run.sh reads the original verifier and creates a temporary execution copy. It
adds compilation of WorkflowReviewIT into the ignored test output after WAR
packaging, then invokes it through the existing Failsafe runner after the normal
planner journeys. The remaining original verification phases continue.
The Java test is never added to the WAR.

## Controlled failures and outputs

The refresh reproduction wraps only the browser's planner bridge: the genuine
save reaches the application; the next month read is rejected. A second browser
checks the actual stored revision.

The replay cases use a BudgetStore decorator around a real PostgresBudgetStore
and real database transactions. The second save either returns Failed/Unknown
before delegation, or commits through the delegate and then returns Unknown.
This tests workflow handling of those outcomes; it is not a database-network
fault or a validation of the JDBC outcome classifier.

The local-acknowledgement case rejects deletion of one synthetic browser-local
month after a successful server reply, restores normal storage, and retries.
The navigation stress case schedules two change events with a cancel/reopen
between them in one browser task, then discards the second prompt after the first
close event has arrived. It does not replace application dialog handlers.

Outputs include the review JUnit XML, replay outcome text, dialog close-event
JSON and synthetic screenshots. Raw Maven/JUnit output contains local machine
paths; the published evidence is a sanitized summary in
[results.json](../../evidence/step-3/results.json). The frozen-source findings and
limits are in the [Step 3 report](../../03-workflows.md).
