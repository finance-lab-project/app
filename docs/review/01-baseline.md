# Step 1 — Source, build and deployment identity

Observation date: **7 November 2026 (Europe/Moscow)**.

**Result:** the source and CI-build baseline is identified. Its correspondence
to the running service is **unverified**. The owner confirmed that no deployment
manifest is currently available. This record does not establish production
release acceptance.

## Scope and method

Read the current default-branch references, commit trees, POMs, assembly scripts,
build guide, CI run/log, release lists and available verification record.
Use immutable source links below for subsequent technical review. No new build,
runtime compatibility test, deployment or recovery experiment was executed in
this step.

The same-day guest-browser review established that the public HTTPS planner
was reachable and exercised synthetic browser-local data. Page appearance and
successful guest behavior do not identify the server's WAR, source revision,
JVM, Jetty or database versions.

## Frozen source baseline

| Component | Revision selected for review | Reason |
| --- | --- | --- |
| Application, build parent, authentication and operations | [`f78682778eebed86730ec457f63c96648f19370d`](https://github.com/finance-lab-project/app/commit/f78682778eebed86730ec457f63c96648f19370d) | App `main` and the successful assembled-application CI run at observation time |
| Budget embedded by the app build | [`43d86d28965a241cd45f7938c1adfb2ec50f8132`](https://github.com/finance-lab-project/budget/commit/43d86d28965a241cd45f7938c1adfb2ec50f8132) | Exact pin in [prepare-budget.sh](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/scripts/prepare-budget.sh) |
| ORM embedded through Budget | [`8daaf91d07d2e30b563322b22a0c6da7545578e5`](https://github.com/finance-lab-project/orm/commit/8daaf91d07d2e30b563322b22a0c6da7545578e5) | Exact pin in [prepare-orm.sh](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/scripts/prepare-orm.sh) |

Budget `main` was
[`4d48f5bc5daf41fb08cfafe2a41f747bede05bd4`](https://github.com/finance-lab-project/budget/commit/4d48f5bc5daf41fb08cfafe2a41f747bede05bd4).
Both that commit and the application's Budget pin reference tree
`9ec0f71166873cfc95aa1bfb43b53afc82bd45e0`. Their tracked contents therefore
match; the commit-ID difference is not a source-content mismatch. The tree
references were checked through the
[pinned commit API](https://api.github.com/repos/finance-lab-project/budget/git/commits/43d86d28965a241cd45f7938c1adfb2ec50f8132)
and the [main branch API](https://api.github.com/repos/finance-lab-project/budget/branches/main).
The latter is a time-dependent observation.

The app's
[build-parent POM](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/build-parent/pom.xml),
[Budget vendored copy](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/build-support/build-parent-0.1.0.pom)
and [ORM vendored copy](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/build-support/build-parent-0.1.0.pom)
have identical contents and Git blob ID
`f276c8cd9ed0cba562bf83853f6fc503fa5178f6`.

Portfolio and FIRE are deferred planning modules according to the
[reviewed application inventory](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/README.md).
The archived React, Go, BFF and platform repositories are outside this runtime
baseline.

## Declared outputs and runtime/build pins

| Maven coordinate (`groupId:artifactId:packaging:version`) | Role |
| --- | --- |
| `org.financelab:build-parent:pom:0.1.0` | Separately publishable parent |
| `org.financelab:authentication:jar:0.1.0` | Jetty-classpath account adapter; ships with runtime dependencies and authentication configuration |
| `org.financelab:application:war:0.1.0` | Assembled `finance-lab.war`, including Budget and ORM |
| `org.financelab:budget:jar:0.2.0` | Monthly planning domain, persistence, UI and migration resources |
| `org.financelab:orm:jar:0.1.0` | JDBC transactions, mapping and projections |

The app root `app-assembly` is a local reactor, not a separately published
component. These are declared build coordinates, not proof of published packages.

Sources:
[app build guide](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/build.md),
[application POM](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/application/pom.xml),
[authentication POM](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/authentication/pom.xml),
[Budget POM](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/pom.xml),
[ORM POM](https://github.com/finance-lab-project/orm/blob/8daaf91d07d2e30b563322b22a0c6da7545578e5/pom.xml).

The checked-in [runtime report](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/runtime-stack.md)
pins Java 27.0.1.0.3 (compile release 27), Vaadin 25.3.5, Jetty 12.1.14/EE11,
Servlet API 6.1.0, PostgreSQL 18.6, pgJDBC 42.7.14, Maven 3.10.0,
Node 24.22.2 and npm 11.19.1. These describe the selected build/test stack.
Actual production versions were not inspected in this step.

## CI evidence and expected bundle checksums

[Application run 42349683701](https://github.com/finance-lab-project/app/actions/runs/42349683701)
completed successfully for app `f78682778eebed86730ec457f63c96648f19370d`.
Its [verification job log](https://github.com/finance-lab-project/app/actions/runs/42349683701/job/128334957411)
was read during this review. The log contains these final manifest entries:

| File | SHA-256 printed by CI |
| --- | --- |
| `application/target/finance-lab.war` | `a2a81f71a567587934cf7c830b3adf2b4c10d4fa1e9004f9fc60fbfdba27307a` |
| `authentication/target/authentication-0.1.0.jar` | `661f6f65ccbde13e31cc432870ce16bb8e0e466cede7b8235bce8dd505645a24` |
| `authentication/target/lib/password4j-1.8.4.jar` | `d1406a158533b65bda7c20cebf7bf2a9a0626f8931fd1dc3d39243af5849edf5` |
| `config/jetty-auth.xml` | `fd8b7ab5d9393f7e1e945e6df0f384c4ac96a8da878d3dbc0ffaf28773cfb494` |
| `build-parent/pom.xml` | `aa61d398a42e3df231b696532a545872296bba8d039b453cf5ad98f5b284a34f` |

The log entries match the five corresponding values in the
[earlier candidate verification record](https://github.com/finance-lab-project/app/blob/fdeef5525a08ecf6022630b64749c25140b58ac5/docs/r1-verification.md).
That record is in [open PR #12](https://github.com/finance-lab-project/app/pull/12),
not the frozen app default branch. Its additional nested-JAR checksums and local
test/recovery results remain prior implementation evidence; they were not rerun
or independently recomputed from binaries during this step.

The [run artifact listing](https://api.github.com/repos/finance-lab-project/app/actions/runs/42349683701/artifacts)
returned no retained binary artifacts. Reading a digest in a log is not
verification of downloaded or deployed bytes.

## Release and publication state

The GitHub release listings for
[app](https://api.github.com/repos/finance-lab-project/app/releases),
[Budget](https://api.github.com/repos/finance-lab-project/budget/releases) and
[ORM](https://api.github.com/repos/finance-lab-project/orm/releases)
were empty at observation time. Tag inventory and package-registry contents
were not established by this review.

The [build guide](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/docs/build.md)
selects GitLab Maven Package Registry and describes it as unprovisioned, with
no packages published. The reviewed Budget and ORM POMs also set
`maven.deploy.skip=true`. Registry availability and actual publication behavior
belong to Step 6; a local Maven install is not a published release.

No `LICENSE`, `CITATION.cff` or `.zenodo.json` file was present in the reviewed
app, Budget or ORM trees. Publication preparation needs an explicit licensing
and attribution decision; this review does not choose one.

## Findings and remaining evidence

| ID | Finding | Required follow-up |
| --- | --- | --- |
| S1-01 | The deployed source revision and artifact digests are unknown. | Record the actual deployed WAR, authentication JAR, dependencies/configuration digests and source/module revisions in the protected operator record. Compare them with the selected release manifest. Publish only the approved provenance summary. |
| S1-02 | The successful CI run exposes checksums but retains no downloadable release bundle. | During release preparation, retain the verified binaries, required configuration and manifest so the exact candidate can be retrieved and checked. |
| S1-03 | Declared package versions have no corresponding GitHub releases in the inspected listings; registry publication is not independently verified. | Step 6 should establish the exact version/tag/artifact mapping and registry evidence before release publication. |
| S1-04 | The inspected trees have no explicit license or citation metadata files. | Select the intended code/report licenses and authors, then validate citation metadata before the planned publication. |

S1-01 prevents claiming that a later source finding necessarily describes the
currently deployed bytes. It does not prevent reviewing the frozen source
baseline. Source/build identity is recorded; deployed correspondence remains
open. Follow-up production-runtime and recovery observations belong in Step 8.
