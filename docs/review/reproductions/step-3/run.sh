#!/usr/bin/env bash
# Review-only harness; production sources and the packaged WAR remain unchanged.
set -euo pipefail
# Required: REVIEW_APP_DIR (clean frozen checkout), REVIEW_EVIDENCE_DIR (outside it).
test -n "$REVIEW_APP_DIR"
test -n "$REVIEW_EVIDENCE_DIR"
review_here="$(cd "$(dirname "$0")" && pwd)"
export REVIEW_SOURCE="$review_here/WorkflowReviewIT.java"
mkdir -p "$REVIEW_EVIDENCE_DIR"
export REVIEW_APP_DIR REVIEW_EVIDENCE_DIR
driver="$(mktemp)"
trap 'rm -f "$driver"' EXIT
python3 - "$driver" <<'PY'
import os, pathlib, shlex, subprocess, sys
root=pathlib.Path(os.environ['REVIEW_APP_DIR']).resolve()
assert subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()=='f78682778eebed86730ec457f63c96648f19370d'
assert not subprocess.check_output(['git','-C',str(root),'status','--porcelain'],text=True).strip()
text=(root/'scripts/verify.sh').read_text()
start='cd "$(dirname "$0")/.."'
marker='integration_test PlannerJourneyIT\nintegration_test AuthenticationIT'
assert text.count(start)==1 and text.count(marker)==1
hook=r'''
mvn -B -ntp -f application/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath \
    -Dmdep.includeScope=test "-Dmdep.outputFile=$REVIEW_EVIDENCE_DIR/application-test-classpath.txt"
"$JAVA_HOME/bin/javac" \
    -cp "application/target/test-classes:application/target/classes:$(cat "$REVIEW_EVIDENCE_DIR/application-test-classpath.txt")" \
    -d application/target/test-classes "$REVIEW_SOURCE"
integration_test WorkflowReviewIT
cp application/target/failsafe-reports/TEST-org.financelab.app.WorkflowReviewIT.xml "$REVIEW_EVIDENCE_DIR/"
'''
text=text.replace(start,'cd '+shlex.quote(str(root)),1)
text=text.replace(marker,'integration_test PlannerJourneyIT\n'+hook+'\nintegration_test AuthenticationIT',1)
pathlib.Path(sys.argv[1]).write_text(text)
PY
bash "$driver"
git -C "$REVIEW_APP_DIR" diff --exit-code
