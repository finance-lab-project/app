# Step 6 — builds, tests and delivery

Observation date: **8 November 2026**. PR #17 was independently confirmed merged.
Runtime source remains the frozen [Step 1 baseline](01-baseline.md).

## Conclusion

The accepted application artifacts remain reproducible, and both library JARs
now also rebuild byte for byte and match the nested WAR copies. Delivery still
has **two reproduced defects and a CI validation gap**: Bash 3.2 does not stop
the publisher's failing conditional guards, library POMs inherit incorrect SCM
URLs, and the checked-in CI gate omits the persistence integration suites.

[Results](evidence/step-6/results.json) distinguish new checks, reused test
evidence, live GitHub observations and limits.
[Reproduction instructions](reproductions/step-6/README.md) retain the runner.
The [release preparation plan](release-preparation.md) establishes dependency
order, proposed version/tag mapping, command shapes and candidate evidence.
No runtime fix or hosted publication was performed.

## Reviewed build and provenance

| Component | Reviewed source / declared output |
| --- | --- |
| App, authentication and parent | [app f786827](https://github.com/finance-lab-project/app/tree/f78682778eebed86730ec457f63c96648f19370d); parent POM, authentication JAR/runtime dependency and application WAR at 0.1.0 |
| Budget | [budget 43d86d2](https://github.com/finance-lab-project/budget/tree/43d86d28965a241cd45f7938c1adfb2ec50f8132); JAR/POM 0.2.0 |
| ORM | [orm 8daaf91](https://github.com/finance-lab-project/orm/tree/8daaf91d07d2e30b563322b22a0c6da7545578e5); JAR/POM 0.1.0 |

The parent and both vendored parent copies are byte-identical. The source
records fixed archive timestamps, pinned build plugins/direct releases, release/
dynamic-version enforcement, npm lockfiles/npm ci, checksum-verified Linux
tool archives and exact Budget/ORM source pins. WAR checks validate embedded
source revisions and exclude server/test libraries from the application archive.

New Step 6 builds used fresh local source clones with the already-prepared
isolated dependency cache: Java 27.0.1.0.3, Maven 3.10.0, Node 24.22.2/npm 11.19.1,
Jetty 12.1.14 on macOS arm64. Maven help 3.5.1 captured effective POM metadata.
This is evidence of reproducible accepted outputs, not a claim of an air-gapped,
fully hermetic or new empty-cache build.

### New completed checks

| Check | Observed result |
| --- | --- |
| Five accepted app/authentication/configuration/parent digests | Match the frozen CI and prior review values |
| Parent copies | All three byte-identical |
| ORM and Budget | Each rebuilt twice; both pairs identical and equal to the WAR's nested JAR bytes |
| JAR manifests | Correct module repository and full revision |
| Effective library POMs | Incorrect inherited project/SCM URLs, S6-02 |
| Publisher | Five cases exercised against per-case temporary file registries; four required rejections fail on Bash 3.2, S6-01 |
| Valid parent publication | Exact POM and matching provenance sidecar reach the local registry |
| Original tracked source | Unchanged; disposable clones/tags/registries removed |

The newly rebuilt library digests are:

| Artifact | SHA-256 |
| --- | --- |
| orm-0.1.0.jar | f172af011a04d74549cb917f2bab8feb4bd79ad1594fdee8b71815db2f1145e4 |
| budget-0.2.0.jar | b24dfb9fb4d2a9a8ff1f2edcef7984b1b14f1e0542654c37f251752bb62fb730 |

Library package builds intentionally skipped tests. Completed tests from the
same frozen sources are reused below; they were not rerun or relabeled as new
Step 6 tests.

## Test evidence and the CI gate

| Layer | Completed review evidence | Checked-in app CI path |
| --- | --- | --- |
| ORM unit cases | 6 passed in Step 4 | Runs during source bootstrap |
| ORM PostgreSQL cases | 75 passed in Step 4 | Skipped |
| Budget unit cases | 119 passed in Step 3 | Runs during source bootstrap |
| Budget PostgreSQL cases | 29 passed in Step 3 | Skipped |
| SQL design harness | 112 passed in Step 4 | Not invoked |
| App/authentication unit cases | 7 passed in Step 5, in each clean build | Runs |
| Packaged app | 1 WAR case, 2 planner journeys and 4 authentication phases passed in Step 5 | Runs |
| Review-only characterizations/probes | Retained in Steps 3–5 | Not automatically invoked; future fixes need strict regression assertions |

This maps behavior to commands, not a line/branch-coverage percentage. The
reviewed POMs/workflow contain no configured coverage measurement or threshold.
Passing counts do not establish that every required behavior is tested:
S4-01 demonstrated a difference between a test helper and the actual reader.

### S6-03 — persistence suites are outside the automatic gate

**Release-validation gap; not a claim that the implementation tests fail.**
The [app workflow](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/.github/workflows/build.yml#L22)
calls preparation and the assembled-app verifier.
[prepare-budget.sh](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/scripts/prepare-budget.sh#L12)
and [prepare-orm.sh](https://github.com/finance-lab-project/budget/blob/43d86d28965a241cd45f7938c1adfb2ec50f8132/scripts/prepare-orm.sh#L13)
use skipITs when installing dependencies. Neither frozen module has a checked-in
workflow, and both public Actions run listings returned zero at observation time.

A green assembly job therefore does not gate the database concurrency, rollback,
commit-outcome and projection cases in those module suites. A focused follow-up
should make the accepted persistence regression suites part of the release/CI
gate and retain their reports. This step does not add CI automation.

## S6-01 — publication guards do not stop Bash 3.2

**Medium priority; reproduced, not fixed.** The
[publisher](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/scripts/publish-parent.sh#L8)
uses standalone conditional commands plus errexit to reject an invalid endpoint,
dirty checkout and a missing/mismatched tag. The native host selected by its
Bash launcher was 3.2.57. That shell continued after a false standalone
conditional; a simple failing command did stop it.

The unchanged publisher was exercised with a narrow Maven wrapper that forces
both synthetic input URLs to separate local file registries. It executes the
real deploy-file goal and checks the resulting files. No hosted endpoint was
contacted for publication, and no deployment credential was supplied.

| Fixture | Required result | Observed Bash 3.2 result |
| --- | --- | --- |
| Missing v0.1.0 | Reject before deployment | Exit 0; local POM written |
| v0.1.0 points to another commit | Reject | Exit 0; local POM written |
| Unapproved synthetic endpoint | Reject | Exit 0; redirected local POM written |
| Matching tag but modified parent POM | Reject | Exit 0; modified POM written |
| Clean matching tag | Publish exact POM/provenance | Correct local bytes and sidecar |

The dirty fixture added a valid XML comment. Its deployed POM differed from the
frozen/tagged file while provenance still named the unchanged HEAD and tag.
Thus the release guard does not reliably bind the artifact to approved clean
source in that environment.

A separate cached-container control using Bash 5.2.37 stopped the failed
conditional with status 1. The full publisher was not tested in that Linux
shell. The checked-in CI targets Ubuntu; this finding does not claim identical
failure on its newer shell or an observed unauthorized hosted publication.

A focused fix should make rejection explicit or enforce a tested shell with an
explicit failure path, and verify all four negative cases stop before Maven.
The toolchain script uses the same conditional pattern, so shell compatibility
also matters to its early pin checks. Do not treat this review runner's
characterization success as publication acceptance.

**Dated qualification:** earlier source descriptions record the intended
clean/tag checks. These new runtime observations qualify that guarantee for
Bash 3.2. Prior JUnit results, byte comparisons and independently checked tool/
artifact versions are not invalidated.

## S6-02 — library POMs identify the application repository

**Low priority metadata defect; reproduced, not fixed.** The libraries override
source.repository for their JAR manifests but inherit project/SCM metadata from
the [application parent](https://github.com/finance-lab-project/app/blob/f78682778eebed86730ec457f63c96648f19370d/build-parent/pom.xml#L10).
Maven's effective models are:

| Module | Effective project/SCM URL | Effective connection |
| --- | --- | --- |
| ORM | `https://github.com/finance-lab-project/app/orm` | `scm:git:https://github.com/finance-lab-project/app.git/orm` |
| Budget | `https://github.com/finance-lab-project/app/budget` | `scm:git:https://github.com/finance-lab-project/app.git/budget` |

Both also inherit v0.1.0, which does not establish a module-specific release tag.
Their JAR manifests correctly identify the real repositories/revisions. The
defect affects Maven source discovery and release/citation metadata; it does
not show wrong runtime code in the WAR.

Before library publication, make each effective project/SCM location and tag
identify that library's approved source, then recheck the published POM alongside
the JAR manifest and provenance sidecar.

## Hosted delivery state and remaining work

The frozen [CI run 42349683701](https://github.com/finance-lab-project/app/actions/runs/42349683701)
remains successful, with **zero retained artifacts** returned by its artifact
listing. The [PR #17 run](https://github.com/finance-lab-project/app/actions/runs/42447963551)
also completed successfully, at a documentation commit distinct from the frozen
runtime revision. No tags or public releases were returned for app, Budget or ORM.

These observations continue S1-02/S1-03: there is no retrievable hosted candidate
bundle in the inspected run, and public release/tag mapping has not been created.
The selected GitLab registry is described as unprovisioned in the reviewed
build guide; its actual project, credentials, contents, duplicate controls and
consumer behavior were not inspected. The local parent experiment does not prove
those hosted controls or full-module clean-cache consumption.

S1-04 also remains open: no root code-license/citation/Zenodo metadata was found
in the reviewed repositories. The [release plan](release-preparation.md) retains
those owner decisions, candidate files, dependency order and future command
templates. It does not select versions/commits on the owner's behalf.

No production connection, deployment, hosted tag/release/package publication or
Zenodo publication occurred. Earlier correctness findings remain open.
