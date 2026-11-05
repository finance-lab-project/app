# Runtime compatibility spike

Issue: [app #2](https://github.com/finance-lab-project/app/issues/2).
Version selection date: 2026-11-04. Scope: a synthetic compatibility probe,
not the application bootstrap, Budget implementation or deployment.

## Selected versions

The executable pins are in [the spike POM](../spikes/runtime-stack/pom.xml)
and [verification script](../spikes/runtime-stack/verify.sh). Releases were
checked against publisher documentation and Maven Central metadata on the date
above. Prereleases are excluded even when a repository's `release` field points
to one. Recheck this matrix when upgrading; do not replace pins with `latest`.

| Component | Exact selection | Source / constraint |
| --- | --- | --- |
| Java | **27.0.1.0.3**, compile release **27** | Latest GA feature line is [Java 27](https://www.oracle.com/java/technologies/downloads/), not Java 25 LTS. Selected distribution: standalone Oracle JDK, macOS arm64. This exact patch is checked by the script. |
| Vaadin platform / Maven plugin | **25.3.5** | [Current stable line](https://vaadin.com/roadmap), [published BOM](https://repo.maven.apache.org/maven2/com/vaadin/vaadin-bom/25.3.5/vaadin-bom-25.3.5.pom). Only `vaadin-core`; no Spring dependency. |
| Jetty distribution | **12.1.14** | [Published distribution](https://repo.maven.apache.org/maven2/org/eclipse/jetty/jetty-home/12.1.14/). Use **EE11**, not Jetty 12.0/EE10. |
| Jakarta Servlet API | **6.1.0**, `provided` | [Vaadin baseline](https://vaadin.com/docs/latest/compatibility); [Jetty 12.1 environments](https://jetty.org/docs/jetty/12.1/operations-guide/deploy/index.html). Container owns the Servlet implementation. |
| PostgreSQL | **18.6** | [Supported versions](https://www.postgresql.org/support/versioning/). PostgreSQL 19 is still a release candidate on the selection date. Disposable official `postgres:18.6` image; server version asserted over JDBC. |
| pgJDBC | **42.7.14** | [Publisher download page](https://jdbc.postgresql.org/download/). Test scope in this UI-only WAR probe. |
| Maven | **3.10.0** | [GA release notes](https://maven.apache.org/docs/3.10.0/release-notes.html). Maven 4 RC is excluded. Enforced by the POM. |
| Node.js / bundled npm | **24.22.2 / 11.19.1** | [Node release index](https://nodejs.org/dist/index.json). Latest patch in the build plugin's supported Node 24 line; see the constraint below. Build tools only. |
| JUnit Jupiter | **6.1.3** | [Published release](https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/6.1.3/). Runs on the selected JDK. |
| Playwright Java | **1.64.0** | [Published release](https://repo.maven.apache.org/maven2/com/microsoft/playwright/playwright/1.64.0/). Uses its matching Chromium download for the browser smoke. |
| Maven build plugins | Clean **3.5.0**, Resources **3.5.0**, Compiler **3.16.0**, Surefire/Failsafe **3.6.1**, WAR **3.5.1**, Enforcer **3.7.0**, Exec **3.6.4** | Explicit stable plugin pins in the POM; no Maven 4 beta plugins. Exec installs only the matching Chromium browser. |

**Observed build-tool constraint:** the documentation says Node 24 or later,
but the 25.3.5 plugin reports Node 26 as newer than its maximum supported 24.x
and falls back to its managed Node installation. An initial Node 26 build
succeeded, but the selected reproducible matrix uses Node 24.22.2 and its bundled
npm 11.19.1 without bypassing compatibility checks. Node 26.13.0 is therefore not
the selected build runtime. Java remains on the latest GA feature line, 27.

The Vaadin BOM supplies its coordinated transitive Java versions (Flow 25.3.7).
The committed frontend package lock records the resolved npm graph, and
`vaadin.ci.build=true` uses `npm ci`. No Testcontainers, mocking
framework, Spring, ORM or migration framework is needed for this probe. Flyway
selection remains bootstrap work, as specified in the architecture.

## WAR decision

Retain the proposed **ordinary WAR**. The probe produces `runtime-stack.war`
with production frontend assets and deploys that file as `ROOT.war` to a separate
Jetty process. It does not run from the source tree, use a Maven development
server, or replace Jetty with Tomcat/Spring Boot.

Jetty requires `http,ee11-deploy,ee11-annotations,ee11-websocket-jakarta` in this
tested arrangement. EE11 provides Servlet 6.1; annotation scanning discovers the
Vaadin route/initializer. The Jakarta WebSocket module supplies the associated
container integration; push behavior itself is outside this smoke. Servlet API,
Jetty, JDBC and test libraries are excluded from the probe WAR. The application
bootstrap must later add its own runtime JDBC dependency and module assembly.

The `build-frontend` goal enables production mode; the old `productionMode`
Maven parameter is no longer effective in this version. The spike uses
`forceProductionBuild=true`.
This explicitly exercises Vite instead of accepting a reusable prebuilt bundle.
The test inspects the WAR for generated JavaScript and production configuration,
then opens it in Chromium, clicks a server-handled button and reloads the route.
No Node.js/npm process is required by the deployed production WAR.

The probe has no login or real data. Its HTTP listener and disposable database
port bind to loopback only. It is not a release artifact for public deployment.

## Reproduce

Prerequisites: the pinned JDK, Maven and Node/npm above; unpacked Jetty 12.1.14;
Docker with the official PostgreSQL 18.6 image; Bash, curl, awk, ps and JDK `jcmd`.
Linux hosts also need the system libraries required by Playwright Chromium.
Initial Maven/npm/browser/image downloads require network access and disk space.
Install tools separately; the script does not change the system JDK or Node.

The observed macOS arm64 run used these downloaded archives (SHA-256):

| Archive | SHA-256 |
| --- | --- |
| [Oracle JDK 27.0.1.0.3](https://download.oracle.com/java/27/archive/jdk-27.0.1.0.3_macos-aarch64_bin.tar.gz) | `1bdff19b9042d693ec18c6623387ffa9945e3a34fe5ba24b9598aad2ffc5d82e` |
| [Node 24.22.2](https://nodejs.org/dist/v24.22.2/node-v24.22.2-darwin-arm64.tar.gz) | `aa9549349e47dffaa42dcf95d6e06ade8c4bdafc980979883b88767670df385a` |
| [Jetty Home 12.1.14](https://repo.maven.apache.org/maven2/org/eclipse/jetty/jetty-home/12.1.14/jetty-home-12.1.14.tar.gz) | `f269b7b98691a48647e54ca5bce9d1042c0eb5f12a0887879974f418f70baa23` |

The JDK and Node hashes match their publisher-provided checksums. Other operating
systems require the corresponding distribution archives and new validation;
these macOS archive hashes are not Linux installation instructions.

From the repository root, with tool directories substituted for these synthetic
examples:

```sh
export JAVA_HOME=/opt/runtime-tools/jdk-27.0.1.0.3
export JETTY_HOME=/opt/runtime-tools/jetty-home-12.1.14
export PATH="/opt/runtime-tools/node-v24.22.2/bin:$JAVA_HOME/bin:$PATH"
java -version
mvn -version
node --version
npm --version
./spikes/runtime-stack/verify.sh
```

`verify.sh` runs these Maven commands inside `spikes/runtime-stack`:

```sh
mvn -B -ntp clean package
mvn -B -ntp exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test -Dexec.args='install chromium'
mvn -B -ntp failsafe:integration-test failsafe:verify
```

It creates a disposable PostgreSQL container with a random loopback port and
tmpfs data; the synthetic `spike` user uses trust authentication only in that
isolated test container. It initializes a Jetty base under `target/`, starts the
WAR with a 256 MiB maximum Java heap, then runs both integration tests. Set
`SPIKE_HTTP_PORT` if loopback port 18082 is occupied. The exit trap stops Jetty
and removes the database container even on a failed test. `target/` holds local
logs, build output, Failsafe reports and memory snapshots and is not committed.
Chromium is cached under the ignored spike-local `.cache/playwright/`; the
script neither installs the other browsers nor garbage-collects shared browser
caches. Frontend lockfile regeneration after an intentional dependency change
uses `mvn -B -ntp -Dvaadin.ci.build=false clean package`; review both package files
and rerun the default verification afterward.

The JDBC test uses separate writer/reader connections: an uncommitted insert is
invisible, a committed `123.45` is visible, a second insert followed by an injected
duplicate-key error is rolled back, and a fresh connection reads only the first
row with exact decimal scale. It is a real PostgreSQL transaction check, not an
in-memory database or simulated JDBC driver. It does not implement the Budget
schema, custom mapper, history, concurrent-save protocol or unknown-commit logic.

## Completed observations

Completed on 2026-11-04 on macOS arm64, with Oracle JDK
`27.0.1.0.3+1-2`, Maven 3.10.0 and Docker Desktop. The final run began without
`node_modules`; Maven, npm download and Chromium caches were already warm.
`/usr/bin/time -l spikes/runtime-stack/verify.sh` exited **0**. Selected output
(local machine paths omitted):

```text
Running `npm ci` to resolve and optionally download frontend dependencies.
Using globally installed Node.js version 24.22.2
Build frontend completed in 3731 ms.
BUILD SUCCESS
Total time: 5.859 s
jetty-12.1.14; jvm 27.0.1.0.3+1-2
WAR OK: production assets; no Spring/container/test jars; Chromium 156.0.8078.4; route, server event and reload passed; no browser errors
JDBC OK: PostgreSQL 18.6 / pgJDBC 42.7.14; uncommitted row invisible; commit visible; failed transaction rolled back; fresh read = 123.45
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Jetty RSS after smoke (KiB): 257248
PostgreSQL memory after smoke: 71.86MiB / 256MiB
Runtime compatibility smoke passed.
```

Both Failsafe XML reports independently record one passing test, no skipped
tests and runtime `27.0.1.0.3+1-2`. The WAR is approximately 10 MiB. Jetty shut
down and the disposable PostgreSQL container was removed by the exit trap.
Shell syntax, POM/JSON parsing, local Markdown links and `git diff --check`
also passed. No GitHub asynchronous check result is inferred from these tests.

| Measurement after the final smoke | Observation |
| --- | --- |
| Jetty resident memory (`ps -o rss`) | 257,248 KiB, approximately **251 MiB** |
| Jetty Java heap (`jcmd GC.heap_info`) | **42.5 MiB used**, 66 MiB committed, 256 MiB maximum |
| JVM Native Memory Tracking | 234,575 KiB committed, approximately **229 MiB**; reserved address space is not resident memory |
| PostgreSQL (`docker stats --no-stream`) | **71.86 MiB** against the 256 MiB test-container limit |
| Full verification wall time (`time -l`) | **16.31 seconds** with warm download/browser caches and fresh `node_modules` |
| macOS `time -l` maximum RSS field | **700,661,760 bytes**, approximately 668 MiB; a process high-water field, not simultaneous aggregate memory of Maven, Node, browser, Jetty and Docker |

These are point-in-time smoke observations, not load-test peaks or an RSS budget
for the future application. The build completed with the heap limits below.

## Capacity boundary

The script caps the Maven heap at 1 GiB and Node's old-space heap at 1.5 GiB;
those are individual heap limits, not total process-tree or VM memory limits.
Jetty has a 256 MiB maximum Java heap and PostgreSQL a 256 MiB Docker memory limit
for this tiny probe. Native memory, thread stacks, code cache, browser processes,
filesystem cache and the operating system consume additional memory.

The 4 GiB / two shared vCPU VM remains a **planning baseline**. A single synthetic
page and one short transaction cannot establish application capacity, sustained
latency, concurrent-session behavior or backup headroom. This run is on macOS
arm64 with PostgreSQL in Docker, not on the planned Linux VM. Build production
assets outside the small runtime VM; validate the assembled application, database,
proxy and backup workload together on the selected host before deployment.
No cloud resource, purchase, deployment, merge or release is part of this check.
