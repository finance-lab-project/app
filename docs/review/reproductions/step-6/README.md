# Reproduce Step 6 build and publication checks

Requirements: Python 3, Git, Maven 3.10.0, Java 27.0.1.0.3, Node 24.22.2/npm
11.19.1 and Jetty 12.1.14. The script uses only disposable local clones, tags and
file registries. It redirects the unchanged publisher before any Maven deployment;
neither synthetic URL is contacted.

Use clean full checkouts at the frozen commits and an app checkout already
built/verified through scripts/prepare-budget.sh and scripts/verify.sh. See
[the build guide](../../../build.md). That prior verification prepares the local
Maven dependencies and five accepted baseline files. Existing review work reused
its isolated Maven cache/settings; no global settings were changed.

From an app checkout containing this review, set:

    export REVIEW_APP_DIR=/path/to/frozen-app
    export REVIEW_BUDGET_DIR=/path/to/frozen-budget
    export REVIEW_ORM_DIR=/path/to/frozen-orm
    export REVIEW_EVIDENCE_DIR=/path/to/new-local-evidence
    export REVIEW_MAVEN_SETTINGS=/path/to/private-review-settings.xml
    export JAVA_HOME=/path/to/jdk-27.0.1.0.3
    export JETTY_HOME=/path/to/jetty-home-12.1.14
    export PATH="$JAVA_HOME/bin:/path/to/node-v24.22.2/bin:$PATH"
    export MAVEN_ARGS="--settings $REVIEW_MAVEN_SETTINGS"
    python3 docs/review/reproductions/step-6/check.py

The exact source revisions are checked by the script. The evidence directory
must be outside the tracked source roots. Maven may download public dependencies,
including the review-only help plugin 3.5.1. No credentials are needed for the
file-registry experiment. Do not supply hosted deployment configuration.

The runner checks the five baseline digests and identical parent copies, rebuilds
ORM and Budget twice each in fresh clones, and compares their full JAR bytes
with the nested WAR entries. Library builds intentionally skip tests: this adds
artifact reproducibility evidence and reuses the completed Step 3–5 test results.
It captures effective project/SCM metadata from Maven.

For the publisher, four negative cases and one positive case run the unmodified
publish-parent.sh. A narrowly scoped Maven wrapper delegates version requests,
accepts only the exact deploy-file goal and two synthetic input URLs, and
replaces either deployment URL with a per-case file registry. It verifies that
an actual local artifact was written, not only that Maven was invoked. The dirty
fixture appends a valid XML comment to the parent POM and restores it afterward.

**The publication cases characterize behavior, not acceptance of a fix.**
On the observed Bash 3.2.57 host, all four negative cases incorrectly deploy:

| Case | Intended acceptance |
| --- | --- |
| missing_tag | Reject |
| mismatched_tag | Reject |
| unapproved_endpoint | Reject |
| dirty_checkout | Reject |
| clean_matching_tag | Accept and preserve exact POM/provenance |

The script independently probes failed-conditional handling and records whether
this shell stops. On a shell that stops as intended, the negative cases should
be rejected. A fix regression suite must require rejection rather than copying
the vulnerable expected outcome.

The separate modern-shell control used the already-cached image:

    docker run --rm --entrypoint /bin/bash postgres:18.6 -c \
      'bash --version | head -1; (set -e; [[ no == yes ]]; echo CONDITIONAL_CONTINUED); printf "conditional_status=%s\n" "$?"'

Observed Bash 5.2.37 returned conditional_status=1 without the continuation marker.
This control starts no database and does not run the complete publisher in Linux.

The script writes local-results.json and local diagnostic logs. Public
[results.json](../../evidence/step-6/results.json) additionally records the separate
modern-shell control, read-only GitHub observations and reused test evidence.
Raw effective POMs/logs can contain machine paths; publish only sanitized fields.

Temporary clones, local tags and file registries are removed on exit. Original
tracked sources remain unchanged. No GitHub tag, release, hosted package or
production change is created.
