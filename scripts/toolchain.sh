#!/usr/bin/env bash
# Source this file. Tool installation is separate; do not change the host JDK.
: "${JAVA_HOME:?Set JAVA_HOME to JDK 27.0.1.0.3}"
: "${JETTY_HOME:?Set JETTY_HOME to Jetty 12.1.14}"
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m}"
export NODE_OPTIONS="--max-old-space-size=1536"
[[ "$(java -version 2>&1)" == *'"27.0.1.0.3"'* ]]
[[ "$(mvn -B -ntp -version)" == *'Apache Maven 3.10.0'* ]]
[[ "$(node --version)" == v24.22.2 ]]
[[ "$(npm --version)" == 11.19.1 ]]
[[ -f "$JETTY_HOME/lib/jetty-server-12.1.14.jar" ]]
