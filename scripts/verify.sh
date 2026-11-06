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
tls_port=${APP_TEST_TLS_PORT:-18084}
export APP_HTTP_PORT="$port" APP_HTTP_BASE_URL="http://127.0.0.1:$port/"
export APP_BASE_URL="https://localhost:$tls_port/" APP_PUBLIC_ORIGIN="https://localhost:$tls_port"
temporary=$(mktemp -d)
export APP_JETTY_BASE="$temporary/jetty-base"
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
cp authentication/target/authentication-0.1.0.jar "$temporary/first-authentication.jar"
# Two clean production builds must produce exactly the same archive.
mvn -B -ntp clean package "-Dsource.revision=$BUILD_SOURCE_REVISION"
cmp "$temporary/first.war" application/target/finance-lab.war
cmp "$temporary/first-authentication.jar" authentication/target/authentication-0.1.0.jar
git diff --exit-code -- application/package.json application/package-lock.json
printf 'Reproducibility OK: two clean WAR and authentication JAR builds are byte-identical.\n'
scripts/check-parent.sh
mvn -B -ntp -f application/pom.xml exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.classpathScope=test "-Dexec.args=${PLAYWRIGHT_INSTALL_ARGS:-install chromium}"
# Use a disposable local TLS connector; production TLS/proxy deployment is separate.
"$JAVA_HOME/bin/keytool" -genkeypair -alias synthetic-test -keyalg RSA -keysize 2048 \
    -validity 1 -dname CN=localhost -ext SAN=dns:localhost,ip:127.0.0.1 \
    -storetype PKCS12 -keystore "$temporary/test.p12" -storepass synthetic-test-only -noprompt > target/test-tls.log 2>&1
mkdir -p "$APP_JETTY_BASE"
(cd "$APP_JETTY_BASE"; "$JAVA_HOME/bin/java" -jar "$JETTY_HOME/start.jar" --add-modules=https) > target/test-tls-setup.log 2>&1
cat > "$APP_JETTY_BASE/start.d/test-tls.ini" <<EOF
jetty.ssl.host=127.0.0.1
jetty.ssl.port=$tls_port
jetty.sslContext.keyStorePath=$temporary/test.p12
jetty.sslContext.keyStorePassword=synthetic-test-only
jetty.sslContext.keyManagerPassword=synthetic-test-only
EOF
# A missing account configuration must never yield a usable application.
env -u APP_MODE -u APP_ACCOUNT_FILE scripts/run.sh > target/jetty-unconfigured.log 2>&1 &
jetty_pid=$!
rejected=false
for attempt in {1..90}; do
    if grep -q 'A valid owner-only APP_ACCOUNT_FILE is required' target/jetty-unconfigured.log; then rejected=true; break; fi
    kill -0 "$jetty_pid" || break
    sleep 1
done
[[ "$rejected" == true ]]
status=$(curl --insecure --silent -o /dev/null -w '%{http_code}' "${APP_BASE_URL}login" || true)
[[ "$status" != 200 ]]
stop_jetty
export APP_ACCOUNT_FILE="$temporary/account.properties"
# Jetty's selected SLF4J API is reused; no duplicate logging implementation.
slf4j_api=$(find "$JETTY_HOME/lib" -name 'slf4j-api-*.jar' -print -quit)
account_classpath="authentication/target/test-classes:authentication/target/classes:authentication/target/lib/*:$slf4j_api"
"$JAVA_HOME/bin/java" -cp "$account_classpath" org.financelab.auth.SyntheticAccountFile "$APP_ACCOUNT_FILE"
start_configured() {
    scripts/run.sh > target/jetty.log 2>&1 &
    jetty_pid=$!
    local ready=false
    for attempt in {1..90}; do
        kill -0 "$jetty_pid"
        if curl --insecure --fail --silent "${APP_BASE_URL}login" -o /dev/null; then ready=true; break; fi
        sleep 1
    done
    [[ "$ready" == true ]]
}
integration_test() {
    mvn -B -ntp -f application/pom.xml "-Dit.test=$1" failsafe:integration-test failsafe:verify
}
start_configured
integration_test WarIT
integration_test AuthenticationIT
stop_jetty
"$JAVA_HOME/bin/java" -cp "$account_classpath" org.financelab.auth.SyntheticAccountFile "$temporary/rotated.properties" rotated
export APP_ACCOUNT_FILE="$temporary/rotated.properties"
start_configured
AUTH_TEST_PHASE=rotated integration_test AuthenticationIT
stop_jetty
export APP_ACCOUNT_FILE="$temporary/account.properties" APP_SESSION_IDLE_SECONDS=2
start_configured
AUTH_TEST_PHASE=idle integration_test AuthenticationIT
stop_jetty
export APP_SESSION_IDLE_SECONDS=30 APP_SESSION_ABSOLUTE_SECONDS=2
start_configured
AUTH_TEST_PHASE=absolute integration_test AuthenticationIT
stop_jetty
grep -q 'Finance Lab stopped' target/jetty.log
if curl --insecure --fail --silent "${APP_BASE_URL}login" -o /dev/null; then
    printf 'HTTP listener still responds after shutdown.\n' >&2; exit 1
fi
# Normal startup must not depend on the verifier's disposable TLS configuration.
rm "$temporary/test.p12"
env -u APP_JETTY_BASE scripts/run.sh > target/jetty-normal.log 2>&1 &
jetty_pid=$!
ready=false
for attempt in {1..90}; do
    kill -0 "$jetty_pid"
    if curl --fail --silent --header 'X-Forwarded-Proto: https' "${APP_HTTP_BASE_URL}login" -o /dev/null; then ready=true; break; fi
    sleep 1
done
[[ "$ready" == true ]]
stop_jetty
grep -q 'Finance Lab stopped' target/jetty-normal.log
printf 'Normal startup OK after removing the verifier TLS keystore.\n'
shasum -a 256 application/target/finance-lab.war authentication/target/authentication-0.1.0.jar \
    authentication/target/lib/*.jar config/jetty-auth.xml build-parent/pom.xml > target/SHA256SUMS
cat target/SHA256SUMS
printf 'Application checks passed: fail-closed config, HTTPS browser login/logout, CSRF, throttling, expiry, restart rotation and bounded shutdown.\n'
