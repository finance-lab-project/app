# Budget R1 verification record

The original candidate evidence below is dated **7 November 2026**. The eleven implementation prerequisites are closed; their merged changes and evidence are linked in the [issue index](roadmap.md#issue-index). [App #6](https://github.com/finance-lab-project/app/issues/6) and the [R1 tracker](https://github.com/finance-lab-project/app/issues/1) retain final release acceptance.

## R1 completion candidate — 10 November 2026

The candidate pins Budget `a0645781b1d5e25536b4651749a372d6fd906315` and ORM `8daaf91d07d2e30b563322b22a0c6da7545578e5`. Budget includes the merged [S3-01 fix](https://github.com/finance-lab-project/budget/pull/10) and [S3-02/S4-01 fixes proposed for R1](https://github.com/finance-lab-project/budget/pull/11): confirmed-save acknowledgement survives refresh failure, rapid dialog reuse preserves the next decision, and category projections reject missing or inconsistent initial facts. No migration or runtime-grants change is required. Budget verification passed 119 unit, 34 PostgreSQL integration and 15 Chromium tests on these sources.

Read-only checks on 10 November matched the initial deployment to app `f78682778eebed86730ec457f63c96648f19370d` and its original module checksums. Host services, restricted listeners/grants, trusted HTTPS and backup access/retention configuration were inspected. The first automatic weekly backup, on 8 November at 03:00 Europe/Moscow, matched the delivered report and independently downloaded ciphertext checksum. These observations concern the initial deployment, not activation of the completion candidate.

The revised assembly is checked with `scripts/prepare-budget.sh` and `scripts/verify.sh`, followed by an isolated recovery rehearsal from a real encrypted provider backup. The exact root revision, retained artifact manifest, completed results, recovery timing and activation decision are recorded in [release PR #12](https://github.com/finance-lab-project/app/pull/12) and [app #6](https://github.com/finance-lab-project/app/issues/6). Detailed infrastructure records, backup receipts and recovery material remain private.

Release completion proceeds through review/merge of Budget PR #11 and app PR #12, approval of the exact candidate, a pre-change backup and activation with live functional/restart checks, then explicit human acceptance before closing app #6 and app #1. Candidate preparation does not authorize production changes or issue closure.

### Documented follow-ups

On 10 November the release scope selected fixes for S3-02/S4-01 and documentation of the remaining review observations as follow-ups. These follow-ups do not add implementation gates to the original private-R1 acceptance criteria.

| Observation | Follow-up and current boundary |
| --- | --- |
| S2-02: transitive composition dependencies | Review direct dependency declarations; the frozen/current assembly must still resolve and pass verification. |
| S3-03: interrupted guest-history transfer | Retry may complete only the latest value after a partial replay. Browser originals remain available; no resumable full-history transfer is promised for R1. |
| S6-01/S6-02: publisher guards and library SCM metadata | Resolve before the corresponding Maven publication. Registry/tag/package publication is separate from this private VM release. |
| S6-03: persistence CI gate | Manual revision-bound module persistence and browser suites supply R1 evidence; automatic CI coverage remains follow-up work. |
| S7-01/S7-02: query growth and execution deadlines | Record the measured query growth and disabled production statement/lock deadlines. Select and validate an optimization or timeout policy separately. |
| S7-03: representative capacity | Existing constrained-Linux and selected-VM/backup-overlap samples are limited. Sustained representative load is not established. |
| Retention and billing | Verify actual 28-day expiration when observable from 5 December and a complete billing period later. Current policy, delivered backups, metered charges and the 2,600 RUB alert budget are recorded for acceptance; neither future observation imposes an automatic December release delay. |
| PostgreSQL logs and full lost-host recovery | Weekly/count-based log rotation lacks a byte cap. Timed recovery exercises use available runtimes/recovery equipment and exclude replacement-VM provisioning/DNS cutover. These limits remain explicit. |

The original [technical review](review/README.md) retains its dated baseline and findings. Fixes and release observations above are later evidence, not a rewrite of the original measurements.

## Original candidate identity — 7 November 2026

The WAR, authentication adapter, Jetty configuration and Maven parent came from app `f78682778eebed86730ec457f63c96648f19370d`. The WAR contains Budget 0.2.0 at `43d86d28965a241cd45f7938c1adfb2ec50f8132` and ORM 0.1.0 at `8daaf91d07d2e30b563322b22a0c6da7545578e5`. The app build properties and both nested JAR manifests identify these revisions. The Budget pin is the reviewed PR head and has the same source tree as the merge into Budget main. [App source](https://github.com/finance-lab-project/app/commit/f78682778eebed86730ec457f63c96648f19370d) · [Budget integration](https://github.com/finance-lab-project/budget/pull/9) · [ORM source](https://github.com/finance-lab-project/orm/commit/8daaf91d07d2e30b563322b22a0c6da7545578e5)

| Checked file | SHA-256 |
| --- | --- |
| `application:0.1.0` · `finance-lab.war` | `a2a81f71a567587934cf7c830b3adf2b4c10d4fa1e9004f9fc60fbfdba27307a` |
| `authentication-0.1.0.jar` | `661f6f65ccbde13e31cc432870ce16bb8e0e466cede7b8235bce8dd505645a24` |
| `password4j-1.8.4.jar` on the Jetty classpath | `d1406a158533b65bda7c20cebf7bf2a9a0626f8931fd1dc3d39243af5849edf5` |
| `config/jetty-auth.xml` | `fd8b7ab5d9393f7e1e945e6df0f384c4ac96a8da878d3dbc0ffaf28773cfb494` |
| `build-parent:0.1.0` POM, including each module's vendored copy | `aa61d398a42e3df231b696532a545872296bba8d039b453cf5ad98f5b284a34f` |
| Embedded `budget-0.2.0.jar` | `b24dfb9fb4d2a9a8ff1f2edcef7984b1b14f1e0542654c37f251752bb62fb730` |
| Embedded `orm-0.1.0.jar` | `f172af011a04d74549cb917f2bab8feb4bd79ad1594fdee8b71815db2f1145e4` |
| Budget `V1__budget_schema.sql` | `33396e787b738b46ce9933d1ff17abacec8326430f49f96758cea466658f0f0c` |
| Budget `db/permissions/runtime.sql` | `44f7ac90143f728bd7b50e65a5f55af0583ee2510992142347c0d4069f16c766` |

The five entries in `target/SHA256SUMS` matched the completed Ubuntu x64 build and passed `shasum -a 256 -c target/SHA256SUMS` locally. CI retained a log and zero binary artifacts. The candidate was built from the pinned sources using local Maven installs. Retain these exact files and their manifest privately before activating a release. [Linux CI job](https://github.com/finance-lab-project/app/actions/runs/42349683701/job/128334957411) · [Build and toolchain](build.md)

## Checks on the pinned revisions

| Repository | Command run | Observed result |
| --- | --- | --- |
| ORM | `scripts/verify.sh` | 6 unit and 75 PostgreSQL integration tests passed, including rollback, commit outcomes and snapshot/history projections. |
| Budget | `scripts/verify.sh` and `BUDGET_UI_PORT=28085 scripts/verify-ui.sh` | 119 unit, 29 PostgreSQL integration and 10 Chromium tests passed. Covered the ten starter categories, exact RUB amounts, explicit Save, conflicts, historical labels, guest storage and transfer. |
| App | `scripts/prepare-budget.sh` and `APP_HTTP_PORT=28083 APP_TEST_TLS_PORT=28084 scripts/verify.sh` | Two clean builds produced byte-identical WARs and authentication adapters. Seven unit tests passed in each build. The independent parent consumer, packaged WAR, two planner journeys and four authentication phases passed. Flyway applied V1 to an empty PostgreSQL 18.6 database. |
| Operations | Docker commands under [Isolated verification](single-vm.md#isolated-verification) | The weekly and pre-change helpers encrypted and verified backups. A clean restore preserved owners, grants and history. The drill rejected a wrong key, damaged object and existing destination. Local SMTP captured both outcomes. The timer expression resolved to Sunday 03:00 Moscow and the emulator returned the enabled 28-day rule. |

## Restart and recovery rehearsal

A local Linux arm64 container ran Caddy, the staged Jetty service command and PostgreSQL 18.6 under a combined 4 GiB memory limit and 0.4 CPU quota. A separate container held the age identity and recovery receipt. The S3 emulator and Chromium ran outside the application container.

- The configured account saved twelve synthetic months with three revisions each. A browser saved a fourth revision for one month. After restarting Jetty and PostgreSQL, a fresh sign-in showed the same current balance, original spending category and first revision.
- The browser reached the authenticated saved month in 2.16 s, confirmed a monthly save in 1.56 s and opened the first revision in 1.99 s. A weekly backup was running throughout each observation. These are single end-to-end samples including browser automation. The corresponding backup jobs took 2.31–3.44 s. The container peaked at 486.1 MiB with zero swap and OOM events. Its CPU controller recorded 85 throttled periods over the sampled interval.
- With the application stopped, the pre-change helper uploaded a 91,247-byte ciphertext. The source container then stopped. The recovery container downloaded it, checked the separately retained digest and decrypted it. A fresh cluster received the saved roles, account configuration and archive. All 24 Budget tables matched source row counts and content fingerprints. Flyway V1, 12 months and 37 revisions survived. Chromium signed in and verified the current balance and first revision.
- Local elapsed wall time was **28.32 s**, from starting the download procedure at 07:25:06.088 UTC through the authenticated history check at 07:25:34.412 UTC. Images, runtimes and candidate binaries were already available. PostgreSQL occupied 48.3 MiB including WAL, and the two staged release directories occupied 25.0 MiB excluding the mounted JDK and Jetty. The Docker host used a shared disk. The selected VM's disk headroom, performance and recovery time still require measurement.

## Original owner and live-evidence checklist — 7 November 2026

Record the observations below in the protected operator record. Keep account data, host and bucket identifiers, object keys, receipts, credentials and screenshots there. Only the owner-approved subset belongs in a release announcement.

| Pending item | Evidence to capture |
| --- | --- |
| Resource and release decision | Owner's dated approval for the exact candidate, provider quote, VM, disk, IPv4, private bucket, DNS/mail changes and deployment scope. Retain the off-VM binary/configuration bundle and confirm the actual deployment's privacy settings. [Before provisioning](single-vm.md#before-provisioning) |
| HTTPS and private workspace | From a clean client verify HTTP redirect, hostname and trusted certificate, guest isolation, denial of unauthenticated account access, configured sign-in and logout. Save a synthetic month, change categories, and check totals and read-only history. Reboot the VM and repeat the signed-in readback. [Proxy and staging](single-vm.md#stage-the-release-and-proxy) |
| Backup operations | Observe a scheduled Sunday 03:00 Moscow run and a pre-change backup, verified upload and delivery of both success and controlled failure reports to the real mailbox. Check the actual bucket's 28-day policy and record expiry when observable. [Backups](single-vm.md#weekly-and-pre-change-backups) |
| Timed off-VM recovery | Download a real release backup with the recovery identity, verify and decrypt it, restore into a clean isolated cluster, then sign in with the matching release and compare current and historical state. Record the selected recovery point, data-loss window, UTC start/end, elapsed time through verified access/cutover and gaps against the seven-day RPO and four-hour RTO. [Recovery procedure](single-vm.md#recover-from-a-lost-vm-or-disk) |
| Cost and acceptance | Record provider-metered usage, billing period and actual RUB cost, peak memory, CPU contention, free disk during backup and storage growth. Obtain human acceptance of the private release and approval of the specific metadata to publish before closing the release issue and tracker. [R1 tracker](https://github.com/finance-lab-project/app/issues/1) |
