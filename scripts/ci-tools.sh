#!/usr/bin/env bash
# GitHub-hosted Ubuntu x64 only. Installs into runner scratch space, not the VM.
set -euo pipefail
[[ "$(uname -s)-$(uname -m)" == Linux-x86_64 ]]
: "${RUNNER_TEMP:?Run on a GitHub-hosted runner}"
: "${GITHUB_ENV:?Missing GitHub environment file}"
: "${GITHUB_PATH:?Missing GitHub path file}"
tools="$RUNNER_TEMP/finance-lab-tools"
mkdir -p "$tools"
cd "$tools"
download() {
    local name=$1 url=$2 algorithm=$3 checksum=$4
    curl --fail --location --silent --show-error --connect-timeout 20 --max-time 300 "$url" -o "$name.tar.gz"
    printf '%s  %s\n' "$checksum" "$name.tar.gz" | "sha${algorithm}sum" --check --status
    tar -xzf "$name.tar.gz"
}
download jdk https://download.oracle.com/java/27/archive/jdk-27.0.1.0.3_linux-x64_bin.tar.gz 256 \
    d41bdc29a13920e90ee0f8e97a5bb9cb854ac06e0aa648a7fda1620db6c4e1ee
download maven https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.tar.gz 512 \
    908b1501bfb420bf7c8affb855534a9c407fd6099367bfb9f2f2dcb8e9799102bffb84518cde74c679bd76870247c6528683abdd620581bffa90f95d92d175aa
download node https://nodejs.org/dist/v24.22.2/node-v24.22.2-linux-x64.tar.gz 256 \
    361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9
download jetty https://repo.maven.apache.org/maven2/org/eclipse/jetty/jetty-home/12.1.14/jetty-home-12.1.14.tar.gz 512 \
    7f054f189b0b5b7a0c0add549cc957a4c141185d2fe8f0a288ed47b69c21d718445a934b2e378168b1937ae4365c0054cf5425ab7dcd4d15e4c6a4b70176f09c
printf 'JAVA_HOME=%s\nJETTY_HOME=%s\n' "$tools/jdk-27.0.1.0.3" "$tools/jetty-home-12.1.14" >> "$GITHUB_ENV"
printf '%s\n' "$tools/jdk-27.0.1.0.3/bin" "$tools/apache-maven-3.10.0/bin" "$tools/node-v24.22.2-linux-x64/bin" >> "$GITHUB_PATH"
