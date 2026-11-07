# Budget R1 verification record

**7 November 2026 · Candidate verified locally. Cloud acceptance is pending.** The eleven prerequisite issues in the [R1 tracker](https://github.com/finance-lab-project/app/issues/1) are closed. Their merged changes and acceptance evidence are linked in the [issue index](roadmap.md#issue-index). [App #6](https://github.com/finance-lab-project/app/issues/6) remains open for the approved private deployment and recovery exercise.

## Candidate identity

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

## Pending owner and live evidence

Record the observations below in the protected operator record. Keep account data, host and bucket identifiers, object keys, receipts, credentials and screenshots there. Only the owner-approved subset belongs in a release announcement.

| Pending item | Evidence to capture |
| --- | --- |
| Resource and release decision | Owner's dated approval for the exact candidate, provider quote, VM, disk, IPv4, private bucket, DNS/mail changes and deployment scope. Retain the off-VM binary/configuration bundle and confirm the actual deployment's privacy settings. [Before provisioning](single-vm.md#before-provisioning) |
| HTTPS and private workspace | From a clean client verify HTTP redirect, hostname and trusted certificate, guest isolation, denial of unauthenticated account access, configured sign-in and logout. Save a synthetic month, change categories, and check totals and read-only history. Reboot the VM and repeat the signed-in readback. [Proxy and staging](single-vm.md#stage-the-release-and-proxy) |
| Backup operations | Observe a scheduled Sunday 03:00 Moscow run and a pre-change backup, verified upload and delivery of both success and controlled failure reports to the real mailbox. Check the actual bucket's 28-day policy and record expiry when observable. [Backups](single-vm.md#weekly-and-pre-change-backups) |
| Timed off-VM recovery | Download a real release backup with the recovery identity, verify and decrypt it, restore into a clean isolated cluster, then sign in with the matching release and compare current and historical state. Record the selected recovery point, data-loss window, UTC start/end, elapsed time through verified access/cutover and gaps against the seven-day RPO and four-hour RTO. [Recovery procedure](single-vm.md#recover-from-a-lost-vm-or-disk) |
| Cost and acceptance | Record provider-metered usage, billing period and actual RUB cost, peak memory, CPU contention, free disk during backup and storage growth. Obtain human acceptance of the private release and approval of the specific metadata to publish before closing the release issue and tracker. [R1 tracker](https://github.com/finance-lab-project/app/issues/1) |
