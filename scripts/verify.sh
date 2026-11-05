#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/toolchain.sh
[[ -z "$(git status --porcelain)" ]] || { printf 'Verify from a clean checkout.\n' >&2; exit 1; }
export BUILD_SOURCE_REVISION
BUILD_SOURCE_REVISION=$(git rev-parse HEAD)
export PLAYWRIGHT_BROWSERS_PATH="$PWD/application/.cache/playwright"
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 PLAYWRIGHT_SKIP_BROWSER_GC=1
port=${APP_HTTP_PORT:-18083}
export APP_HTTP_PORT="$port" APP_BASE_URL="http://127.0.0.1:$port/"
temporary=$(mktemp -d)
jetty_pid=""
stop_jetty() {
    kill "$jetty_pid" 2>/dev/null || true
    for attempt in {1..15}; do
        if ! kill -0 "$jetty_pid" 2>/dev/null; then
            wait "$jetty_pid" || true
            jetty_pid=""
            return
        fi
        sleep 1
    done
    printf 'Jetty did not stop within 15 seconds.\n' >&2
    return 1
}
cleanup() {
    if [[ -n "$jetty_pid" ]]; then
        kill -KILL "$jetty_pid" 2>/dev/null || true
        wait "$jetty_pid" 2>/dev/null || true
    fi
    rm -rf "$temporary"
}
trap cleanup EXIT
mvn -B -ntp clean package "-Dsource.revision=$BUILD_SOURCE_REVISION"
cp application/target/finance-lab.war "$temporary/first.war"
# Two clean production builds must produce exactly the same archive.
mvn -B -ntp clean package "-Dsource.revision=$BUILD_SOURCE_REVISION"
cmp "$temporary/first.war" application/target/finance-lab.war
git diff --exit-code -- application/package.json application/package-lock.json
printf 'Reproducibility OK: two clean WAR builds are byte-identical.\n'
scripts/check-parent.sh
mvn -B -ntp -f application/pom.xml exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.classpathScope=test "-Dexec.args=${PLAYWRIGHT_INSTALL_ARGS:-install chromium}"
# A missing account/mode configuration must never yield a usable application.
env -u APP_MODE scripts/run.sh > target/jetty-unconfigured.log 2>&1 &
jetty_pid=$!
rejected=false
for attempt in {1..90}; do
    if grep -q 'APP_MODE=bootstrap is required' target/jetty-unconfigured.log; then rejected=true; break; fi
    kill -0 "$jetty_pid"
    sleep 1
done
[[ "$rejected" == true ]]
status=$(curl --silent -o /dev/null -w '%{http_code}' "$APP_BASE_URL" || true)
[[ "$status" != 200 ]]
stop_jetty
APP_MODE=bootstrap scripts/run.sh > target/jetty.log 2>&1 &
jetty_pid=$!
ready=false
for attempt in {1..90}; do
    kill -0 "$jetty_pid"
    if curl --fail --silent "$APP_BASE_URL" -o target/bootstrap.html; then ready=true; break; fi
    sleep 1
done
[[ "$ready" == true ]]
mvn -B -ntp -f application/pom.xml failsafe:integration-test failsafe:verify
stop_jetty
grep -q 'Finance Lab bootstrap stopped' target/jetty.log
if curl --fail --silent "$APP_BASE_URL" -o /dev/null; then
    printf 'HTTP listener still responds after shutdown.\n' >&2; exit 1
fi
shasum -a 256 application/target/finance-lab.war build-parent/pom.xml > target/SHA256SUMS
cat target/SHA256SUMS
printf 'Application checks passed: configuration rejection, startup, production browser flow and bounded shutdown.\n'
