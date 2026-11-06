#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 27.0.1.0.3}"
: "${JETTY_HOME:?Set JETTY_HOME to Jetty 12.1.14}"
[[ "$("$JAVA_HOME/bin/java" -version 2>&1)" == *'"27.0.1.0.3"'* ]]
[[ -f "$JETTY_HOME/lib/jetty-server-12.1.14.jar" ]]
[[ -f application/target/finance-lab.war ]]
[[ -f authentication/target/authentication-0.1.0.jar ]]
port=${APP_HTTP_PORT:-18083}
[[ "$port" =~ ^[0-9]+$ ]] && (( port > 0 && port <= 65535 ))
base="$PWD/target/jetty-base"
app_root="$PWD"
mkdir -p "$base"
cd "$base"
"$JAVA_HOME/bin/java" -jar "$JETTY_HOME/start.jar" \
    --add-modules=http,forwarded,ext,ee11-deploy,ee11-annotations,ee11-websocket-jakarta > ../jetty-setup.log 2>&1
cp ../../authentication/target/authentication-0.1.0.jar lib/ext/
cp ../../authentication/target/lib/*.jar lib/ext/
cp ../../application/target/finance-lab.war webapps/ROOT.war
# Only a trusted local TLS proxy may reach this connector and supply forwarded headers.
exec "$JAVA_HOME/bin/java" -Xms64m -Xmx256m -jar "$JETTY_HOME/start.jar" \
    "$app_root/config/jetty-auth.xml" \
    jetty.http.host=127.0.0.1 "jetty.http.port=$port" \
    jetty.server.stopAtShutdown=true jetty.server.stopTimeout=5000
