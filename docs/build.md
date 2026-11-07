# Application assembly

Implements [app #3](https://github.com/finance-lab-project/app/issues/3) and the
[app #4 authentication boundary](authentication.md), using the
[verified runtime pins](runtime-stack.md). The protected production-built WAR
contains the guest planner, configured login and the authenticated Budget workspace.

## Assembly and build

- `build-parent/`: separately publishable `org.financelab:build-parent:pom:0.1.0`.
  Contains Java/Maven enforcement and build-plugin conventions; no application,
  Vaadin or business-module dependencies and no reactor modules.
- Root `pom.xml`: local reactor only, not published.
- `authentication/`: the server-classpath account adapter and Password4j 1.8.4
  Argon2id verifier, packaged separately from the WAR and Jetty itself.
- `application/`: `org.financelab:application:war:0.1.0`, assembled as
  `application/target/finance-lab.war`. Vaadin 25.3.5, Servlet 6.1 provided by
  Jetty 12.1.14 EE11, runtime pgJDBC 42.7.14 and the Budget/ORM libraries.
- Run `scripts/prepare-budget.sh` before the first build. It builds Budget 0.2.0 at
  `43d86d28965a241cd45f7938c1adfb2ec50f8132` and its pinned ORM 0.1.0 at
  `8daaf91d07d2e30b563322b22a0c6da7545578e5`. The WAR test checks both embedded source revisions.
- Flyway 13.12.0 is packaged for the separate administrative migration command in the [deployment runbook](single-vm.md).
  The runtime uses a PostgreSQL account with Budget DML privileges.

Install the exact toolchain from the runtime report, then run from a clean Git
checkout (synthetic installation paths):

```sh
export JAVA_HOME=/opt/runtime-tools/jdk-27.0.1.0.3
export JETTY_HOME=/opt/runtime-tools/jetty-home-12.1.14
export PATH="/opt/runtime-tools/apache-maven-3.10.0/bin:/opt/runtime-tools/node-v24.22.2/bin:$JAVA_HOME/bin:$PATH"
scripts/prepare-budget.sh
scripts/verify.sh
```

The script checks exact JDK/Node/npm/Maven/Jetty pins, builds the WAR twice from
clean Maven output and compares every byte, tests configuration, publishes the
parent to a disposable file registry and compiles an unrelated Java module
against it using a fresh Maven cache and `<relativePath/>`. It installs matching
Chromium and uses a disposable self-signed localhost TLS connector. It checks
fail-closed startup, login/logout, server-side events/reload, CSRF, endpoint
protection, throttling, idle/absolute expiry and credential rotation across a JVM
restart. SIGTERM must stop the listeners within 15 seconds. Startup polls are bounded to 90
seconds each; the CI job has an overall 25-minute deadline. Downloads need network access. The browser journey verifies local Save, sign-in, transfer, a second browser and conflict resolution against disposable PostgreSQL 18.6 (Docker required).

Direct development builds use:

```sh
mvn -B -ntp clean package -Dsource.revision="$(git rev-parse HEAD)"
```

Development builds may include working changes; only a clean tagged checkout
is publishable. Archive timestamps are fixed with Maven's
[`project.build.outputTimestamp`](https://maven.apache.org/guides/mini/guide-reproducible-builds.html),
plugins and direct dependencies are pinned, the Vaadin BOM coordinates Java
transitives, and committed npm lockfiles plus `vaadin.ci.build=true` use `npm ci`.
Enforcer rejects snapshots, dynamic dependency versions, non-release project
versions and missing full source revisions. Builds cap Maven heap at 1 GiB and
Node old-space at 1.5 GiB; builds belong outside the small deployment VM. This
does not assert that runner OS packages or the application fit the planned VM.

## Configuration and lifecycle

```sh
APP_ACCOUNT_FILE=/run/finance-lab/account.properties \
APP_PUBLIC_ORIGIN=https://finance.example.invalid scripts/run.sh
```

First apply the packaged migration and export the restricted runtime connection settings `APP_DB_URL`, `APP_DB_USER` and `APP_DB_PASSWORD` from the protected environment described in the [single-VM runbook](single-vm.md).

This starts a separate Jetty process on **127.0.0.1:18083** from the packaged WAR.
Override the port with `APP_HTTP_PORT` (1–65535). Use `APP_JETTY_BASE` to override
the default `target/jetty-base`. Verification uses its own disposable base to
keep test TLS settings out of normal startup. The script does not require
Node/npm at runtime. `APP_DISPLAY_ZONE` defaults to `Europe/Moscow` and must be
a valid Java zone ID; it does not inherit the machine's display zone.

Missing/invalid account configuration or HTTPS origin rejects startup. The former
`APP_MODE` bootstrap bypass is removed and rejected. The protected account file,
session limits, trusted TLS proxy settings and operator rotation/recovery procedure
are documented in the [authentication guide](authentication.md). Ship the
authentication JAR, its runtime `lib/` directory and `config/jetty-auth.xml` with
the WAR. Runtime requires no Maven, Node or npm.
The servlet listener opens a bounded Hikari pool, verifies the Budget mappings, and closes the pool at shutdown.
Stop with Ctrl-C or SIGTERM; Jetty runs with a five-second graceful stop timeout.

## CI and registry decision — 2026-11-05

**GitHub remains the code, issue, PR and CI home.** The checked-in
`.github/workflows/build.yml` runs one bounded GitHub-hosted Ubuntu 24.04 x64 job
for PRs, main pushes and manual checks. It has read-only repository permission,
does not retain checkout credentials, uses a commit-pinned checkout action and
checksum-pinned tool archives. It executes the same checks as local verification.
There is no deployment, package publication, cloud provisioning or paid service
setup. No additional service or paid runner was created.

**GitLab Maven Package Registry is the selected release registry**, addressing
the requested GitLab stack alignment without moving repositories or duplicating
CI. It is not provisioned or connected by this issue. Before the first separately
authorized publication, select an existing/approved free GitLab project, disable
[duplicate Maven packages](https://docs.gitlab.com/user/packages/maven_repository/)
with no exception regex, and restrict deletion/republication to release owners.
Do not publish if these controls or free storage/transfer allowances are absent;
obtain a new decision before incurring cost. Registry administrators can still
delete artifacts, so immutability also requires the provenance digest and release
retention policy. A transient CI artifact is not the module registry.

GitHub Packages was considered but requires authentication even for public Maven
packages ([GitHub documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)).
Choosing GitLab for packages provides the requested alignment without a second
build scheduler. GitLab CI, source mirrors, hosted deployment and automatic
release jobs are intentionally not introduced.

## Immutable publication and consumption

Release versions are never overwritten, including the parent. Version `0.1.0`
is unpublished in this increment. After review and separate publication
authorization, create protected tag `v0.1.0` on the exact clean, verified commit.
Any subsequent change to parent contents needs a new version/tag and consumer
pin update. The executable publication helper refuses a dirty or mismatched tag:

```sh
export GITLAB_MAVEN_URL=https://gitlab.com/api/v4/projects/12345678/packages/maven
export MAVEN_SETTINGS=/opt/private/maven-settings.xml
scripts/publish-parent.sh
```

Use the actual approved project ID, not this synthetic example. The private
settings file supplies server `gitlab-maven` using a restricted deploy token
as documented by [GitLab](https://docs.gitlab.com/user/packages/maven_repository/).
Never commit or log tokens. The helper uploads the exact parent POM and a
`provenance.properties` classifier with GAV, source URL, full commit, tag and
SHA-256. Verify both in the registry before allowing dependent release builds.
The helper is not executed by CI or by bootstrap verification.

An independent repository uses this parent and a registry entry in its own POM
or approved Maven settings (with snapshots disabled):

```xml
<parent>
  <groupId>org.financelab</groupId>
  <artifactId>build-parent</artifactId>
  <version>0.1.0</version>
  <relativePath/>
</parent>
```

Each library overrides `source.repository`, supplies its own full
`source.revision` during a clean tagged build, and publishes provenance alongside
its immutable artifact. Application builds embed source/version/parent provenance
in `META-INF/finance-lab/build.properties`; library JARs inherit source manifest
entries. `target/SHA256SUMS` records the checked application's WAR, authentication
adapter/runtime dependencies, Jetty configuration and parent POM digests.
The parent, ORM and Budget must be resolvable from the registry before
independent release builds can use them; a local `mvn install` is development only.

## Verification evidence

Local commands, observed results and GitHub CI status are recorded separately
in the implementation PR. Adding a workflow does not mean a GitHub job ran or
passed. No release artifacts have been published and no application deployed.
