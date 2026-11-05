# Application bootstrap

Implements [app #3](https://github.com/finance-lab-project/app/issues/3), using
the [verified runtime pins](runtime-stack.md). This is a production-built WAR
containing a bootstrap page, not a deployable Budget release. Login is
[app #4](https://github.com/finance-lab-project/app/issues/4).

## Assembly and build

- `build-parent/`: separately publishable `org.financelab:build-parent:pom:0.1.0`.
  Contains Java/Maven enforcement and build-plugin conventions; no application,
  Vaadin or business-module dependencies and no reactor modules.
- Root `pom.xml`: local reactor only, not published.
- `application/`: `org.financelab:application:war:0.1.0`, assembled as
  `application/target/finance-lab.war`. Vaadin 25.3.5, Servlet 6.1 provided by
  Jetty 12.1.14 EE11 and runtime pgJDBC 42.7.14. No Spring, ORM/Budget artifacts,
  Portfolio/FIRE, reporting, workflow, messaging or cryptography integrations.

ORM/Budget have no published implementation artifacts yet; do not invent
coordinates or require sibling source trees. When delivered, pin their exact
release versions in the application's dependency management and record each
GAV, source repository, full commit and artifact SHA-256 in its release manifest.
Do not add module dependencies to the parent or depend on the application WAR.

Flyway core and PostgreSQL extension **13.12.0** are reserved in application
dependency management, selected from [publisher release metadata](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-core/maven-metadata.xml)
on 2026-11-05. They are not runtime dependencies yet: there are no module schemas
or migrations in this increment. PostgreSQL 18.6 compatibility and migration
ordering must be checked with the first actual module migration before adding
the runner. This is a version choice, not a claim that migrations ran.

Install the exact toolchain from the runtime report, then run from a clean Git
checkout (synthetic installation paths):

```sh
export JAVA_HOME=/opt/runtime-tools/jdk-27.0.1.0.3
export JETTY_HOME=/opt/runtime-tools/jetty-home-12.1.14
export PATH="/opt/runtime-tools/apache-maven-3.10.0/bin:/opt/runtime-tools/node-v24.22.2/bin:$JAVA_HOME/bin:$PATH"
scripts/verify.sh
```

The script checks exact JDK/Node/npm/Maven/Jetty pins, builds the WAR twice from
clean Maven output and compares every byte, tests configuration, publishes the
parent to a disposable file registry and compiles an unrelated Java module
against it using a fresh Maven cache and `<relativePath/>`. It installs matching
Chromium, checks that an unconfigured WAR is unavailable, opens the configured
production page, performs a server-side event and reload, sends SIGTERM, and
requires listener shutdown within 15 seconds. Startup polls are bounded to 90
seconds each; the CI job has an overall 25-minute deadline. Downloads need
network access. No database is created or contacted by this UI bootstrap check.
The real PostgreSQL transaction probe remains in `spikes/runtime-stack`.

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
APP_MODE=bootstrap scripts/run.sh
```

This starts a separate Jetty process on **127.0.0.1:18083** from the packaged WAR.
Override the port with `APP_HTTP_PORT` (1–65535). The script does not require
Node/npm at runtime. `APP_DISPLAY_ZONE` defaults to `Europe/Moscow` and must be
a valid Java zone ID; it does not inherit the machine's display zone.

Missing `APP_MODE`, `production`, or any other mode rejects webapp startup.
Bootstrap mode is explicitly unauthenticated and contains no business data.
Keep it on loopback; it is not an online release. Normal application startup
remains closed until #4 implements and validates the real account configuration.
The Servlet listener owns application configuration, removes it on stop and
provides a lifecycle seam for actual resources when modules arrive. There are
no database pools, workers or optional clients to initialize in this increment.
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
entries. `target/SHA256SUMS` records the checked application's WAR and parent POM
digests. The parent, ORM and Budget must be resolvable from the registry before
independent release builds can use them; a local `mvn install` is development only.

## Verification evidence

Local commands, observed results and GitHub CI status are recorded separately
in the implementation PR. Adding a workflow does not mean a GitHub job ran or
passed. No release artifacts have been published and no application deployed.
