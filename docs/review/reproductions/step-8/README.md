# Reproduce Step 8 deployment and recovery checks

Run only the isolated fixture here. Requirements: Docker, Python 3 and a clean
full app checkout at f78682778eebed86730ec457f63c96648f19370d with the five accepted
bundle outputs from [Step 1](../../01-baseline.md). Building the checked-in
recovery image may download public images and Debian packages.

    export REVIEW_APP_DIR=/path/to/frozen-verified-app
    export REVIEW_EVIDENCE_DIR=/path/to/new-private-step-8-evidence
    python3 docs/review/reproductions/step-8/run.py

Use a new evidence directory outside the source checkout. No cloud credentials,
production URLs, login credentials or host services are used. The Docker
fixtures have no published host ports and share an isolated emulator's network
namespace. Only the new evidence directory is mounted writable; inputs and
operating scripts are read-only. No privileged container is required.

## What runs

The host runner verifies the frozen commit, clean source and five accepted
SHA-256 values. It extracts Budget V1 SQL and runtime grants from the accepted
WAR, builds the repository's recovery-check image and launches Moto 5.1.14 plus
a disposable check container.

The container first runs the unmodified checked-in recovery.sh, including
ShellCheck, Caddy validation, calendar/lifecycle checks, weekly/pre-change
encrypted backups, local OK/FAILED mail, clean restore and wrong-key/damaged/
existing-target rejection.

The external drill.py then uses the extracted Budget schema and seed.sql:
24 real tables containing 47 synthetic rows, two revisions, category rename,
unset amounts and explicit zero/value facts. A real pre-change encrypted backup
is uploaded/read back in Moto, fetched/decrypted and restored into a fresh
PostgreSQL 18.6 cluster. Sorted row data for every table and relation/schema
owners/ACLs are fingerprinted before and after; selected projections run with
the runtime role.

Negative checks deny the backup user's access to the private age key, reject
mail submission to a closed local relay, require an import without its owner
role to fail without leaving the Budget schema, and refuse the resulting
existing empty target. The test drops only that owned empty fixture database,
applies the saved role definitions and verifies a fresh-target retry.

The host runner has a five-minute drill watchdog. Cleanup removes the owned
containers and temporary extracted SQL. Database files, synthetic object data,
keys and plaintext archives live only inside the removed containers. Local
logs/results remain in the requested evidence directory; do not publish raw
logs or machine paths.

## Scope and interpretation

This uses the unchanged backup, fetch-backup, restore and report-backup scripts.
The schema seed is direct SQL: Flyway, its migration ledger, Java/browser
application startup and SCRAM password login are not tested by the added phase.
The runtime role is exercised through SET ROLE. Fingerprints cover table data
and object owners/ACLs, not a separate canonical diff of every DDL definition.

The private key is protected from the backup user but physically shares the
disposable container; production off-VM independence is not established.
SMTP is a local capture/closed-port failure, without a real relay's TLS/auth.
Moto does not establish provider IAM, live lifecycle expiration or durability.
Caddy validation is syntax/configuration validation; there is no systemd PID 1,
actual service sandbox, automatic restart, boot or public TLS renewal test.

The tiny local durations exclude tool installation, a real download, provisioning,
full application verification, human work and cutover. They are not RPO/RTO or
production capacity acceptance. See the [report](../../08-deployment-recovery.md)
and [results](../../evidence/step-8/results.json) for exact observations/limits.

The public website GET observations in the report were separate one-time
read-only checks. This reproduction runner does not contact the production
website or repeat those requests. The published JSON combines the raw fixture
result with sanitized public observations and review metadata.
