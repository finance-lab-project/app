#!/bin/bash
# Run only inside the disposable, network-isolated container in the runbook.
set -euo pipefail
cd "$(dirname "$0")/.."
export PGHOST=/var/run/postgresql PGDATABASE=finance_lab DB_OWNER=finance_owner
export BACKUP_BUCKET=synthetic-drill S3_ENDPOINT=http://127.0.0.1:5000 AWS_DEFAULT_REGION=ru-central1
export AWS_ACCESS_KEY_ID=synthetic AWS_SECRET_ACCESS_KEY=synthetic AWS_EC2_METADATA_DISABLED=true
export REPORT_TO=operator@example.invalid MSMTP_CONFIG=/drill/msmtprc RUNTIME_DIRECTORY=/drill/receipt
export AGE_RECIPIENT_FILE=/drill/recipient AGE_IDENTITY_FILE=/drill/recovery/key
[[ -f /.dockerenv && ! -e /drill ]]
shellcheck backup fetch-backup report-backup restore checks/recovery.sh
APP_HOST=finance.example.invalid caddy validate --config "$PWD/Caddyfile" --adapter caddyfile
calendar=$(sed -n 's/^OnCalendar=//p' finance-lab-backup.timer)
next=$(TZ=UTC systemd-analyze calendar --base-time='2026-11-07 00:00:00 UTC' "$calendar")
[[ $next == *'Sun 2026-11-08 00:00:00 UTC'* ]]
mkdir -m 0755 /drill
useradd --system --no-create-home finance_backup
install -d -m 0700 -o finance_backup "$RUNTIME_DIRECTORY"
install -d -m 0700 /drill/recovery
age-keygen -o "$AGE_IDENTITY_FILE" 2>/dev/null
age-keygen -y "$AGE_IDENTITY_FILE" > "$AGE_RECIPIENT_FILE"
printf 'account default\nhost 127.0.0.1\nport 8025\nfrom backup@example.invalid\n' > "$MSMTP_CONFIG"
python3 -u -m aiosmtpd -n -l 127.0.0.1:8025 > /drill/mail 2>&1 &

s3=(aws --endpoint-url "$S3_ENDPOINT" s3api)
python3 - <<'PY_READY'
import socket
import time

for attempt in range(60):
    try:
        with socket.create_connection(("127.0.0.1", 5000), timeout=1):
            break
    except OSError:
        time.sleep(0.5)
else:
    raise SystemExit("Local S3 emulator did not start")
PY_READY
"${s3[@]}" create-bucket --bucket "$BACKUP_BUCKET" --create-bucket-configuration LocationConstraint=ru-central1 > /dev/null
"${s3[@]}" put-bucket-lifecycle-configuration --bucket "$BACKUP_BUCKET" --lifecycle-configuration file://lifecycle.json
rule=$("${s3[@]}" get-bucket-lifecycle-configuration --bucket "$BACKUP_BUCKET" \
    --query 'Rules[0].[Status,Filter.Prefix,Expiration.Days]' --output text)
[[ $rule == $'Enabled\tdatabase/\t28' ]]

admin=(runuser -u postgres --)
backup=(runuser -u finance_backup -- env PGUSER=finance_backup)
psql=("${admin[@]}" psql -X -v ON_ERROR_STOP=1)
start_cluster() {
    install -d -m 0700 -o postgres "$1"
    "${admin[@]}" initdb -D "$1" --no-locale --encoding=UTF8 --auth-local=peer --auth-host=scram-sha-256 > /dev/null
    cat postgresql.conf >> "$1/postgresql.conf"
    cp pg_hba.conf "$1/pg_hba.conf"
    "${admin[@]}" pg_ctl -D "$1" -l "$1/server.log" -w start
    "${psql[@]}" -d postgres -f roles.sql > /dev/null
    [[ $("${psql[@]}" -At -d postgres -c 'SHOW server_version_num') == 180006 ]]
}
start_cluster /drill/source
"${admin[@]}" createdb --template=template0 --owner="$DB_OWNER" "$PGDATABASE"
"${psql[@]}" <<'SQL'
SET ROLE finance_owner;
CREATE SCHEMA budget;
CREATE TABLE budget.history (revision integer PRIMARY KEY, amount numeric(12,2));
INSERT INTO budget.history VALUES (1, NULL), (2, 0.00), (3, 123.45);
CREATE VIEW budget.current_amount AS SELECT amount FROM budget.history ORDER BY revision DESC LIMIT 1;
GRANT USAGE ON SCHEMA budget TO finance_app;
GRANT SELECT ON ALL TABLES IN SCHEMA budget TO finance_app;
SQL
"${backup[@]}" ./backup weekly
SERVICE_RESULT=success "${backup[@]}" ./report-backup weekly
"${backup[@]}" ./backup pre-change
key=$(sed -n 's/^Object key: //p' "$RUNTIME_DIRECTORY/verified")
digest=$(sed -n 's/^SHA-256: //p' "$RUNTIME_DIRECTORY/verified")
[[ $key == database/pre-change/* ]]
if "${backup[@]}" env PGDATABASE=missing ./backup weekly; then exit 1; fi
if SERVICE_RESULT=exit-code "${backup[@]}" ./report-backup weekly; then exit 1; fi
grep -q 'Subject: Finance Lab weekly backup OK' /drill/mail
grep -q 'Subject: Finance Lab weekly backup FAILED' /drill/mail
"${admin[@]}" pg_ctl -D /drill/source -m fast -w stop

start_cluster /drill/clean
age-keygen -o /drill/recovery/wrong-key 2>/dev/null
if AGE_IDENTITY_FILE=/drill/recovery/wrong-key ./fetch-backup "$key" "$digest" /drill/recovery/wrong; then exit 1; fi
[[ ! -e /drill/recovery/wrong/database.dump ]]
./fetch-backup "$key" "$digest" /drill/recovery/verified
restore=("${admin[@]}" env PGUSER=postgres ./restore)
"${restore[@]}" "$PGDATABASE" < /drill/recovery/verified/database.dump
if "${restore[@]}" "$PGDATABASE" < /drill/recovery/verified/database.dump; then exit 1; fi
projection=$("${psql[@]}" -qAt <<'SQL'
SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='budget.history'::regclass;
SET ROLE finance_app;
SELECT amount FROM budget.current_amount;
SELECT revision,coalesce(amount::text,'unset') FROM budget.history ORDER BY revision;
SQL
)
[[ $projection == $'finance_owner\n123.45\n1|unset\n2|0.00\n3|123.45' ]]
# Damage only the emulator's synthetic object. The independently saved digest must reject it.
printf 'damaged' | aws --endpoint-url "$S3_ENDPOINT" s3 cp - "s3://$BACKUP_BUCKET/$key"
if ./fetch-backup "$key" "$digest" /drill/recovery/damaged; then exit 1; fi
[[ ! -e /drill/recovery/damaged/database.dump ]]
"${admin[@]}" pg_ctl -D /drill/clean -m fast -w stop
printf 'PASS: clean PostgreSQL 18.6 restore, owners/grants, current and historical values, existing target and damaged object rejection, local OK/FAILED mail, 28-day rule.\n'
