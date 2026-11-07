#!/usr/bin/env bash
# Build the pinned module sources until the Maven registry is provisioned.
set -euo pipefail
revision=1660e6e6f6b6736d3760b8016d89e644f749bcf9
source_dir=$(mktemp -d)
trap 'rm -rf "$source_dir"' EXIT
git -C "$source_dir" init --quiet
git -C "$source_dir" fetch --quiet --depth=1 https://github.com/finance-lab-project/budget.git "$revision"
git -C "$source_dir" -c advice.detachedHead=false checkout --quiet --detach FETCH_HEAD
[[ "$(git -C "$source_dir" rev-parse HEAD)" == "$revision" ]]
"$source_dir/scripts/prepare-orm.sh"
mvn -B -ntp -f "$source_dir/pom.xml" clean install -DskipITs "-Dsource.revision=$revision"
