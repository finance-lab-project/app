# Single-VM deployment and database recovery

Operator runbook for [app #5](https://github.com/finance-lab-project/app/issues/5). The [configuration files](../ops/single-vm/) target one systemd Linux host. Use the isolated drill below to rehearse. Obtain separate owner approval for the priced resources, DNS, mail, deployment and any production cutover. Record online release evidence in [app #6](https://github.com/finance-lab-project/app/issues/6).

## Before provisioning

| Item | Planning input |
| --- | --- |
| Compute | Russia `ru-central1`, regular non-preemptible `standard-v3` (Intel Ice Lake), 2 vCPUs at 20%, 4 GiB RAM |
| Disk and network | One persistent 30 GiB `network-ssd` for OS, releases, PostgreSQL and staging. One attached static public IPv4. Existing domain, DNS and mailbox |
| Backup storage | Private STANDARD Object Storage, an assumed 5 GiB retained across weekly and pre-change copies |
| Cost | The 2026-11-04 estimate was 2,331 RUB for 30 days and 2,408 RUB for 31 days. The planning allowance is 2,600 RUB/month. [Assumptions and tariff sources](architecture.md#first-release-vm-and-monthly-estimate) |

1. Re-price the exact platform, CPU performance, memory, disk, attached IPv4, storage, requests and transfer at 720 or 744 hours with current Russia-region RUB tariffs. Save the dated quote and assumptions privately for approval. [Yandex Cloud calculator](https://yandex.cloud/ru/prices)
2. Before requesting the VM, run the assembled release on comparable constrained Linux resources with synthetic representative history. Exercise sign-in, monthly saves and history while a backup runs. Record response times, CPU contention, peak combined memory/swap or OOMs, backup duration/bytes, disk free at peak and room for growth. Include OS, proxy, Jetty native memory, PostgreSQL, WAL, logs, ciphertext staging and the previous release. Build the WAR off-VM. If measurements or the quote are missing or exceed the baseline, bring the evidence to the owner before provisioning. Repeat on the approved VM before release. [Existing runtime measurements](runtime-stack.md#capacity-boundary)

## Host and database

Use a maintained Linux distribution with the Debian/Ubuntu PostgreSQL cluster layout (`18/main`), systemd, Caddy, Bash, util-linux, age, AWS CLI v2 and msmtp. Match PostgreSQL server **and client** 18.6, JDK **27.0.1.0.3** for Linux x64 and Jetty **12.1.14** EE11. Verify release hashes, record package versions and retain the previous runtime and artifacts. The build guide identifies the production WAR and authentication JARs. [Linux runtime checksums](../scripts/ci-tools.sh) · [Assembly and provenance](build.md)

- Expose TCP 80 and 443 to the proxy. Limit administrative SSH to the operator's approved source. Apply the same restrictions in the VPC security group and host firewall. Permit outbound HTTPS for certificate renewal and Object Storage, DNS/time and the mail provider's authenticated TLS submission port. Keep ports 18083, 5432 and Caddy's local admin port off public and VPC ingress. [VPC security groups](https://yandex.cloud/en/docs/vpc/concepts/security-groups)
- Create separate system users `finance_app` and `finance_backup` with no interactive login. Keep the database on the persistent disk under `postgres`. Include [postgresql.conf](../ops/single-vm/postgresql.conf) from the active cluster configuration and install [pg_hba.conf](../ops/single-vm/pg_hba.conf) as the **complete** HBA on the dedicated host. Remove any broader active overrides, restart `postgresql@18-main`, and inspect `SHOW listen_addresses`, `SHOW hba_file` and `pg_hba_file_rules` for errors. The configured TCP listener is `127.0.0.1:5432`. Local administration and backups use the Unix socket with peer authentication.
- On a **fresh** cluster, run `sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d postgres -f roles.sql` with the supplied [role definitions](../ops/single-vm/roles.sql). For the first installation create `finance_lab` with `sudo -u postgres createdb --template=template0 --owner=finance_owner finance_lab`. In recovery, the restore command creates the database. Set the `finance_app` SCRAM password through interactive `psql` `\password finance_app`. Keep it in the protected recovery configuration. Apply migrations as `finance_owner` through the release's administrative procedure. Grant `finance_app` only the module permissions it needs. The backup role can read all data, with HBA access limited to this database from its local service user.
- Bound journald and PostgreSQL log retention to a disk budget (e.g. `SystemMaxUse=200M` for journald and size-based rotation for PostgreSQL). Monitor free space, including `/var/tmp`. Disable SQL statement/parameter and HTTP access/debug logging of account, request and financial data.

## Stage the release and proxy

In the operator's protected release record, pair the exact app/ORM/Budget versions, commits, digests, migration versions, runtime versions and configuration revision. Keep the verified binaries and their manifests available off-VM. The root WAR includes the guest and authenticated Budget planners. Verify the source revisions of both embedded libraries and apply the versioned migration before startup. [Application assembly](build.md)

From a release bundle that preserves the build output paths, verify `target/SHA256SUMS` against the independently retained release manifest and confirm every JAR to be staged is listed. Run the following in Bash. `$release` is an unused absolute directory under `/srv/finance-lab/releases/` named for the immutable release. `$JAVA_HOME` and `$JETTY_HOME` point at the verified versioned Linux installations. Keep the bundle, runtime and staged files root-owned and readable by `finance_app`.

```sh
set -eu
sha256sum -c target/SHA256SUMS
mkdir -m 0755 "$release"
mkdir "$release/config" "$release/jetty-base"
ln -s "$JAVA_HOME" "$release/jdk"
ln -s "$JETTY_HOME" "$release/jetty-home"
base=$release/jetty-base
(cd "$base" && "$JAVA_HOME/bin/java" -jar "$JETTY_HOME/start.jar" \
  --add-modules=http,forwarded,ext,ee11-deploy,ee11-annotations,ee11-websocket-jakarta)
install -m 0644 application/target/finance-lab.war "$base/webapps/ROOT.war"
install -m 0644 authentication/target/authentication-0.1.0.jar authentication/target/lib/*.jar "$base/lib/ext/"
install -m 0644 config/jetty-auth.xml "$release/config/"
```

For an upgrade, complete the [pre-change backup](#weekly-and-pre-change-backups) before migrating or activating the candidate. Restrict access to the operator during the change.

Use separate SCRAM passwords for `finance_migrate` and `finance_app`. Set the runtime service environment to `APP_DB_URL=jdbc:postgresql://127.0.0.1:5432/finance_lab`, `APP_DB_USER=finance_app` and its protected `APP_DB_PASSWORD`. For each candidate, extract its WAR to a private scratch directory, set `FLYWAY_URL`, `FLYWAY_USER=finance_migrate` and the separate `FLYWAY_PASSWORD` in the operator environment. From the extracted directory run `java -cp 'WEB-INF/classes:WEB-INF/lib/*' org.financelab.app.BudgetMigration` with the pinned JDK. It runs Flyway as `finance_owner`. Apply [Budget grants](../ops/single-vm/budget-grants.sql) as the owner or cluster administrator. The application starts with the restricted runtime role. Review the [privacy and guest-storage details](guest-and-cloud.md) for the actual deployment.

1. Create the owner-only account file as described in the [authentication guide](authentication.md#provisioning-rotation-and-recovery). Install it at `/etc/finance-lab/account.properties`, owned by `finance_app`, mode `0400`, with root controlling the directory. Store `APP_ACCOUNT_FILE=/etc/finance-lab/account.properties` and `APP_PUBLIC_ORIGIN=https://finance.example.invalid` in the root-controlled `/etc/finance-lab/application.env`. Substitute the approved existing hostname and keep `APP_DISPLAY_ZONE=Europe/Moscow`. The stable owner UUID must match on restore and credential rotation.
2. Install [finance-lab.service](../ops/single-vm/finance-lab.service) under `/etc/systemd/system/`. With the JVM stopped, activate the staged directory using `ln -s "$release" /srv/finance-lab/current.next && mv -Tf /srv/finance-lab/current.next /srv/finance-lab/current`. Start the app with `systemctl daemon-reload` and `systemctl enable --now finance-lab`. Inspect its journal and listener. Jetty uses in-memory sessions and trusts the proxy's overwritten RFC 7239 `Forwarded` header on `127.0.0.1:18083`.
3. Install the [Caddyfile](../ops/single-vm/Caddyfile) at `/etc/caddy/Caddyfile`. Put `Environment=APP_HOST=finance.example.invalid` under `[Service]` in a Caddy systemd drop-in, using the same bare hostname as the app origin. Run `APP_HOST=finance.example.invalid caddy validate --config /etc/caddy/Caddyfile` with that substitution. After DNS approval, point just the application's A record at the static IPv4, check for a conflicting AAAA record and preserve existing MX/TXT records. Reload systemd and restart Caddy for the new environment. Keep Caddy's certificate state on disk and enable its service at boot. Caddy obtains and renews TLS and redirects HTTP to HTTPS. [Automatic HTTPS](https://caddyserver.com/docs/automatic-https) · [Proxy headers](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers)
4. Use `ss -lntp` locally and probe from outside the VM. Only intended web ports should be publicly reachable. Confirm HTTP redirects to the approved hostname, a trusted certificate serves `/login`, anonymous business requests are denied and direct loopback HTTP `/login` returns 403. Send `Forwarded: proto=http;host=spoof.example.invalid` and `X-Forwarded-Proto: http` to the **HTTPS** login endpoint and confirm it still serves the canonical secure login. Verify real login/logout, Budget save/history and restart persistence during release acceptance.

## Weekly and pre-change backups

1. On a protected **off-VM** recovery machine, generate an age identity (`umask 077`, `age-keygen -o recovery.key`). Retain the identity in an independent protected store and derive its public recipient with `age-keygen -y recovery.key`. Install only the recipient on the VM. Retain old identities until every object encrypted for them expires. [age](https://github.com/FiloSottile/age)
2. After resource approval, use a dedicated private, unversioned STANDARD bucket. Disable public read/list/config access. Give the backup service account object put/get and multipart-abort access **only** under `database/`. Keep delete, lifecycle and bucket administration with the operator. Give the off-VM recovery identity separate read access and a means to locate verified receipts. Configure the AWS CLI for the Yandex endpoint and `ru-central1`. [Yandex AWS CLI configuration](https://yandex.cloud/en/docs/storage/tools/aws-cli) · [Bucket policy actions](https://yandex.cloud/en/docs/storage/s3/api-ref/policy/actions)
3. As the authorized bucket administrator, inspect any existing lifecycle settings first. The following replaces the lifecycle configuration on the dedicated bucket. Install and read back the [28-day rule](../ops/single-vm/lifecycle.json). It covers both backup reasons. Expiration is processed asynchronously. [Yandex lifecycle operation](https://yandex.cloud/en/docs/storage/operations/buckets/lifecycles)

   ```sh
   aws --endpoint-url "$S3_ENDPOINT" s3api put-bucket-lifecycle-configuration \
     --bucket "$BACKUP_BUCKET" --lifecycle-configuration file://lifecycle.json
   aws --endpoint-url "$S3_ENDPOINT" s3api get-bucket-lifecycle-configuration --bucket "$BACKUP_BUCKET"
   ```

4. Install `backup` and `report-backup` from [ops/single-vm](../ops/single-vm/) as root-owned executables under `/usr/local/libexec/finance-lab/`. Place the service template and timer in `/etc/systemd/system/`. For `/etc/finance-lab/backup.env` use the following contract. Replace the synthetic bucket and mailbox. Store the static S3 key in the separate credentials file under `[default]`. Set up the `default` msmtp account for the existing provider with its host, port, `from`, authentication, TLS with certificate verification, and protected credentials. Limit both private files to `finance_backup` (mode `0400`).

   ```ini
   PGHOST=/var/run/postgresql
   PGUSER=finance_backup
   PGDATABASE=finance_lab
   S3_ENDPOINT=https://storage.yandexcloud.net
   AWS_DEFAULT_REGION=ru-central1
   AWS_SHARED_CREDENTIALS_FILE=/etc/finance-lab/backup/s3.credentials
   AWS_REQUEST_CHECKSUM_CALCULATION=when_required
   AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
   BACKUP_BUCKET=synthetic-backup-bucket
   AGE_RECIPIENT_FILE=/etc/finance-lab/backup/recipient
   MSMTP_CONFIG=/etc/finance-lab/backup/msmtprc
   REPORT_TO=operator@example.invalid
   ```

5. Run `systemd-analyze verify finance-lab.service finance-lab-backup@weekly.service finance-lab-backup@pre-change.service finance-lab-backup.timer` and `systemd-analyze calendar 'Sun *-*-* 03:00:00 Europe/Moscow'`. After approval, run `systemctl daemon-reload`, `systemctl start finance-lab-backup@weekly.service` and check the mailbox. Enable `finance-lab-backup.timer` with `systemctl enable --now finance-lab-backup.timer`. Confirm the next time with `systemctl list-timers finance-lab-backup.timer`. `Persistent=true` catches a missed run after downtime.

Each job holds the shared backup lock. It pipes the whole database from `pg_dump -Fc` into age, uploads only ciphertext to a unique UTC-stamped key and reads the object back to compare SHA-256. systemd runs `report-backup` on success or failure. A success mail carries the object key and verified digest. Save these off-VM with the release/schema revision. A failed job or mail submission is visible in `systemctl status` and `journalctl -u 'finance-lab-backup@*'`. On a failed or missing weekly report, check the timer, disk, database, bucket and relay, fix the cause and run the job again. A stopped VM cannot send mail. Check inbox delivery for success and failure during commissioning. Use only a recorded verified object for recovery.

**Before every database change**, stop the JVM and quiesce all other writers. Run `systemctl start finance-lab-backup@pre-change.service` and require its successful result, fresh verified key/digest and mailbox receipt. Pair it with the **previous** release, roles and configuration. If backup, integrity or notification fails, stop the release here. Run the candidate’s pinned migration as `finance_owner`, check the migration ledger against the manifest, then activate the candidate. Keep user writes closed through the private smoke checks.

## Recover from a lost VM or disk

The recovery point target is about seven days plus backup completion time when the weekly jobs succeed. A missed job increases the loss window. The four-hour recovery time target starts when the operator begins recovery and remains unproven until a timed exercise succeeds. Measure it through verified application access and cutover. A VM outage interrupts both the app and database.

1. Start the recovery clock. Select the newest **verified** point appropriate for the incident, record its UTC time and identify the data-loss window. Retrieve the matching release manifest, runtime, roles and protected configuration from off-VM storage. Maintain this bundle separately from the VM and update it whenever credentials, owner UUID, DB roles/grants, extensions, locale/encoding, database-level settings, DNS/proxy, SMTP or Object Storage access change. Keep a tested means to reach the bucket if the VM and its credentials are lost. A database dump covers the application schemas and history. Cluster roles, host settings and account credentials come from the recovery bundle.
2. On the protected recovery machine, use the read-only storage credentials and independently saved key/digest. Export `S3_ENDPOINT`, `AWS_DEFAULT_REGION`, `BACKUP_BUCKET`, `AWS_SHARED_CREDENTIALS_FILE` and `AGE_IDENTITY_FILE`. On an encrypted disk with room for ciphertext and the decrypted custom archive, run the first command below. Use a new private directory and continue only on exit 0. `fetch-backup` verifies the downloaded bytes and authenticated decryption before exposing `database.dump`.
3. Start a clean, isolated PostgreSQL 18.6 cluster, with the same locale/encoding and required extensions. Apply the saved roles and private role credentials first. Leave the target database absent. Run the second command on a clean local drill host. For an authorized replacement VM, use the SSH form instead. It sends the verified archive over the protected connection and keeps the age private key off the VM.

   ```sh
   ops/single-vm/fetch-backup "$key" "$digest" "$recovery_dir"
   # On the local recovery machine, import into its new cluster.
   sudo -u postgres env PGHOST=/var/run/postgresql PGUSER=postgres DB_OWNER=finance_owner \
     /usr/local/libexec/finance-lab/restore finance_lab < "$recovery_dir/database.dump"
   # For a new remote cluster, use the approved operator SSH destination.
   ssh -T "$recovery_host" 'sudo -n -u postgres env PGHOST=/var/run/postgresql PGUSER=postgres DB_OWNER=finance_owner /usr/local/libexec/finance-lab/restore finance_lab' \
     < "$recovery_dir/database.dump"
   ```

   Install the root-owned [restore helper](../ops/single-vm/restore) on the destination first. It refuses an existing database and uses `pg_restore --single-transaction --exit-on-error`, preserving object owners and grants. A failed import can leave a new empty database. Inspect the error and retry in a fresh target. Reapply the saved database-level privileges/settings and check schema owners, grants and migration versions. [PostgreSQL restore options](https://www.postgresql.org/docs/18/app-pgrestore.html)

4. Load the **same owner UUID** and account configuration into the matching immutable release. With access restricted to the operator, verify sign-in, a known current month and its saved totals, an earlier revision and its category labels, and persistent state after restarting the services. Compare against the private recovery record. Release the endpoint only after operator acceptance. Record the verified restore point, elapsed time and gaps privately. Dispose of plaintext scratch and the isolated drill data under the operator's encrypted-storage policy.
5. Before launch, time this procedure with a downloaded release backup. Repeat a database restore monthly into an isolated local cluster. Keep the date, verified checks, result and operator time off-VM. Published evidence should contain only synthetic values and check outcomes.

## Roll back a release

1. Restrict ingress to the operator, stop Jetty and freeze other writers. Record the failing release, last good release, actual migration state and pre-change receipt. Preserve the failed database and logs privately.
2. If the schema is unchanged or the **previous release is verified compatible** with the current schema, retain the database and switch `current` to the previous verified release using the atomic link change above. Restore its matching runtime/configuration and start Jetty. This path preserves subsequent committed writes.
3. For an incompatible or uncertain schema, keep writes closed. If writes were accepted after the pre-change backup, identify the loss window and obtain the owner's decision before reverting data. Restore that verified object to a **separate clean cluster** with the previous roles/configuration. Validate it with the previous release before switching the local database endpoint and `current`. Keep the failed cluster offline for investigation. Avoid running reverse SQL against the only copy.
4. Repeat the private login, current/history and restart checks, confirm backup scheduling, and reopen access only after acceptance. Keep the release pair and recovery receipt together for the next operator.

## Isolated verification

From the repository root, run the following on a machine with Docker. Image builds/downloads use the network. The drill itself shares only loopback with a disposable S3 emulator. PostgreSQL and the SMTP capture run inside the test container. No host ports or cloud credentials are needed.

```sh
(
  set -eu
  ops="$PWD/ops/single-vm"
  docker build -t finance-lab-recovery-check:local "$ops/checks"
  emulator=$(docker run --rm -d --network none motoserver/moto:5.1.14)
  trap 'docker stop "$emulator" >/dev/null' EXIT
  docker run --rm --network "container:$emulator" --mount "type=bind,src=$ops,dst=/ops,readonly" \
    --entrypoint /bin/bash finance-lab-recovery-check:local /ops/checks/recovery.sh
)
```

The drill starts PostgreSQL 18.6 twice with separate data directories and the supplied HBA, roles and listener settings. It exercises both backup reasons, a failed dump and local mail reports, then checks the downloaded/decrypted restore for ownership, grants, current and historical values, including unset and zero. It also rejects a wrong age key, an existing target and a damaged object. The emulator reads back the 28-day rule. Real expiration, provider mail delivery, public TLS, capacity and the four-hour recovery target require deployed evidence in [app #6](https://github.com/finance-lab-project/app/issues/6).
