#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${BUILD_SOURCE_REVISION:?Set the source commit}"
root=$PWD
test_root="$root/target/parent-consumer"
mkdir -p "$test_root/registry" "$test_root/module/src/main/java/example"
# Publish to a disposable file registry, never to a hosted service in checks.
mvn -B -ntp -f build-parent/pom.xml deploy "-Dsource.revision=$BUILD_SOURCE_REVISION" \
    "-DaltDeploymentRepository=consumer-test::file://$test_root/registry"
cat > "$test_root/module/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.financelab</groupId><artifactId>build-parent</artifactId><version>0.1.0</version><relativePath/></parent>
  <groupId>example</groupId><artifactId>independent-module</artifactId><version>0.1.0</version>
  <properties><source.repository>https://example.invalid/independent-module</source.repository></properties>
  <repositories><repository><id>consumer-test</id><url>file://$test_root/registry</url><snapshots><enabled>false</enabled></snapshots></repository></repositories>
</project>
EOF
printf '%s\n' 'package example; public class IndependentModule {}' > "$test_root/module/src/main/java/example/IndependentModule.java"
# The consumer is outside the reactor, with no relative parent and a fresh cache.
mvn -B -ntp -f "$test_root/module/pom.xml" package \
    "-Dmaven.repo.local=$test_root/cache" "-Dsource.revision=$BUILD_SOURCE_REVISION"
printf 'Independent module resolved the published parent and built successfully.\n'
