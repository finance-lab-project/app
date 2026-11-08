#!/usr/bin/env bash
# Review fixtures only. Creates/removes one disposable loopback PostgreSQL server.
set -euo pipefail
: "$REVIEW_APP_DIR" "$REVIEW_ORM_DIR" "$REVIEW_BUDGET_DIR" "$REVIEW_EVIDENCE_DIR" "$JAVA_HOME"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$REVIEW_EVIDENCE_DIR"
export REVIEW_APP_DIR REVIEW_ORM_DIR REVIEW_BUDGET_DIR
python3 - <<'PY'
import os, pathlib, subprocess
for key, sha in [
    ('REVIEW_APP_DIR', 'f78682778eebed86730ec457f63c96648f19370d'),
    ('REVIEW_ORM_DIR', '8daaf91d07d2e30b563322b22a0c6da7545578e5'),
    ('REVIEW_BUDGET_DIR', '43d86d28965a241cd45f7938c1adfb2ec50f8132'),
]:
    root = pathlib.Path(os.environ[key]).resolve()
    assert subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'], text=True).strip() == sha
    assert not subprocess.check_output(['git','-C',str(root),'status','--porcelain'], text=True).strip()
PY
# Use Maven's normal settings or caller-supplied MAVEN_ARGS for an isolated cache.
mvn -B -ntp -f "$REVIEW_APP_DIR/build-parent/pom.xml" install \
    -Dsource.revision=f78682778eebed86730ec457f63c96648f19370d \
    > "$REVIEW_EVIDENCE_DIR/parent-install.log" 2>&1
mvn -B -ntp -f "$REVIEW_ORM_DIR/pom.xml" install -DskipTests -DskipITs \
    -Dsource.revision=8daaf91d07d2e30b563322b22a0c6da7545578e5 \
    > "$REVIEW_EVIDENCE_DIR/orm-install.log" 2>&1
mvn -B -ntp -f "$REVIEW_BUDGET_DIR/pom.xml" test-compile \
    org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath \
    -Dmdep.includeScope=test "-Dmdep.outputFile=$REVIEW_EVIDENCE_DIR/test-classpath.txt" \
    -Dsource.revision=43d86d28965a241cd45f7938c1adfb2ec50f8132 \
    > "$REVIEW_EVIDENCE_DIR/budget-compile.log" 2>&1
"$JAVA_HOME/bin/javac" \
    -cp "$REVIEW_BUDGET_DIR/target/test-classes:$REVIEW_BUDGET_DIR/target/classes:$(cat "$REVIEW_EVIDENCE_DIR/test-classpath.txt")" \
    -d "$REVIEW_BUDGET_DIR/target/test-classes" "$here/IntegrityReviewIT.java"

container=""
cleanup() { if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null; fi; }
trap cleanup EXIT
container=$(docker run --rm -d --memory=256m --tmpfs /var/lib/postgresql \
    -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=step4 -e POSTGRES_DB=integrity \
    -p 127.0.0.1::5432 postgres:18.6 -c timezone=UTC)
ready=false
for attempt in {1..60}; do
    if docker exec "$container" pg_isready -U step4 -d integrity >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
done
[[ "$ready" == true ]]
export PGHOST=127.0.0.1
export PGPORT="$(docker port "$container" 5432/tcp | awk -F: '{print $NF}')"
export PGUSER=step4 PGDATABASE=integrity
unset PGPASSWORD PGSERVICE PGSERVICEFILE PGOPTIONS
psql -X -v ON_ERROR_STOP=1 -c 'CREATE DATABASE schema_check' \
    > "$REVIEW_EVIDENCE_DIR/schema-setup.log" 2>&1
export PGDATABASE=schema_check
psql -X -v ON_ERROR_STOP=1 -f "$REVIEW_BUDGET_DIR/src/main/resources/db/migration/V1__budget_schema.sql" \
    >> "$REVIEW_EVIDENCE_DIR/schema-setup.log" 2>&1
python3 -u "$REVIEW_BUDGET_DIR/verification/check.py" \
    > "$REVIEW_EVIDENCE_DIR/schema-checks.log" 2>&1
export BUDGET_TEST_URL="jdbc:postgresql://127.0.0.1:$PGPORT/integrity"
export BUDGET_TEST_USER=step4
export BUDGET_RUNTIME_SQL="$REVIEW_BUDGET_DIR/src/main/resources/db/permissions/runtime.sql"
mvn -B -ntp -f "$REVIEW_BUDGET_DIR/pom.xml" \
    -Dit.test=IntegrityReviewIT failsafe:integration-test failsafe:verify \
    > "$REVIEW_EVIDENCE_DIR/integrity-probes.log" 2>&1
cp "$REVIEW_BUDGET_DIR/target/failsafe-reports/TEST-org.financelab.budget.adapter.postgres.IntegrityReviewIT.xml" \
    "$REVIEW_EVIDENCE_DIR/"
for repo in "$REVIEW_APP_DIR" "$REVIEW_ORM_DIR" "$REVIEW_BUDGET_DIR"; do
    [[ -z "$(git -C "$repo" status --porcelain)" ]]
done
tail -1 "$REVIEW_EVIDENCE_DIR/schema-checks.log"
grep '^STEP4 ' "$REVIEW_EVIDENCE_DIR/integrity-probes.log"
