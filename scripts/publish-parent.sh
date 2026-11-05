#!/usr/bin/env bash
# Deliberate manual publication only; never called by the verification workflow.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/toolchain.sh
: "${GITLAB_MAVEN_URL:?Set the approved GitLab project Maven endpoint}"
: "${MAVEN_SETTINGS:?Set a private Maven settings file containing server gitlab-maven}"
[[ "$GITLAB_MAVEN_URL" =~ ^https://gitlab.com/api/v4/projects/[0-9]+/packages/maven$ ]]
[[ -z "$(git status --porcelain)" ]]
revision=$(git rev-parse HEAD)
[[ "$(git rev-parse 'v0.1.0^{commit}')" == "$revision" ]]
mkdir -p target
provenance="$PWD/target/build-parent-0.1.0-provenance.properties"
digest=$(shasum -a 256 build-parent/pom.xml | awk '{print $1}')
cat > "$provenance" <<EOF
coordinates=org.financelab:build-parent:pom:0.1.0
source.repository=https://github.com/finance-lab-project/app
source.revision=$revision
source.tag=v0.1.0
artifact.sha256=$digest
EOF
# deploy-file uploads the exact checked-in POM bytes plus its provenance sidecar.
mvn -B -ntp -s "$MAVEN_SETTINGS" -f build-parent/pom.xml \
    org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file \
    -DrepositoryId=gitlab-maven "-Durl=$GITLAB_MAVEN_URL" \
    -Dfile=build-parent/pom.xml -DpomFile=build-parent/pom.xml \
    -DgeneratePom=false "-Dfiles=$provenance" -Dclassifiers=provenance -Dtypes=properties
