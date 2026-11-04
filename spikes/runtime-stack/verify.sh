#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

: "${JAVA_HOME:?Set JAVA_HOME to the pinned JDK}"
: "${JETTY_HOME:?Set JETTY_HOME to the unpacked Jetty 12.1.14 distribution}"
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx1024m"
export NODE_OPTIONS="--max-old-space-size=1536"
[[ "$(java -version 2>&1 | head -n 1)" == *'"27.0.1.0.3"'* ]]
[[ "$(node --version)" == v24.22.2 ]]
[[ "$(npm --version)" == 11.19.1 ]]
[[ -f "$JETTY_HOME/lib/jetty-server-12.1.14.jar" ]]

mvn -B -ntp clean package
export PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright"
export PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
export PLAYWRIGHT_SKIP_BROWSER_GC=1
mvn -B -ntp exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.classpathScope=test -Dexec.args='install chromium'

# All state is disposable. No host database or persistent Docker volume is used.
database=""
jetty_pid=""
cleanup() {
    if [[ -n "$jetty_pid" ]]; then
        kill "$jetty_pid" 2>/dev/null || true
        wait "$jetty_pid" 2>/dev/null || true
    fi
    if [[ -n "$database" ]]; then docker rm -f "$database" >/dev/null; fi
}
trap cleanup EXIT
database=$(docker run --rm -d --memory=256m \
    --tmpfs /var/lib/postgresql:rw \
    -e POSTGRES_USER=spike -e POSTGRES_DB=spike -e POSTGRES_HOST_AUTH_METHOD=trust \
    -p 127.0.0.1::5432 postgres:18.6)
database_port=$(docker port "$database" 5432/tcp | awk -F: '{print $NF}')
export SPIKE_JDBC_URL="jdbc:postgresql://127.0.0.1:$database_port/spike"
for attempt in {1..60}; do
    if docker exec "$database" pg_isready -U spike -d spike >/dev/null; then break; fi
    sleep 1
done
docker exec "$database" pg_isready -U spike -d spike

base="$PWD/target/jetty-base"
mkdir -p "$base"
(
    cd "$base"
    java -jar "$JETTY_HOME/start.jar" --add-modules=http,ee11-deploy,ee11-annotations,ee11-websocket-jakarta \
        > ../jetty-setup.log 2>&1
)
cp target/runtime-stack.war "$base/webapps/ROOT.war"
port=${SPIKE_HTTP_PORT:-18082}
export SPIKE_BASE_URL="http://127.0.0.1:$port/"
(
    cd "$base"
    exec java -Xms64m -Xmx256m -XX:NativeMemoryTracking=summary \
        -jar "$JETTY_HOME/start.jar" jetty.http.host=127.0.0.1 "jetty.http.port=$port"
) > target/jetty.log 2>&1 &
jetty_pid=$!
ready=false
for attempt in {1..120}; do
    kill -0 "$jetty_pid"
    if curl --fail --silent "$SPIKE_BASE_URL" -o target/bootstrap.html; then ready=true; break; fi
    sleep 1
done
[[ "$ready" == true ]]

mvn -B -ntp failsafe:integration-test failsafe:verify
ps -o rss= -p "$jetty_pid" | awk '{print "Jetty RSS after smoke (KiB): " $1}'
jcmd "$jetty_pid" GC.heap_info > target/jetty-heap.txt
jcmd "$jetty_pid" VM.native_memory summary > target/jetty-native-memory.txt
docker stats --no-stream --format 'PostgreSQL memory after smoke: {{.MemUsage}}' "$database"
printf 'Runtime compatibility smoke passed. Logs: target/jetty.log and target/failsafe-reports/\n'
