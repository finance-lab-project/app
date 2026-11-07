# MVP technical review

Started 7 November 2026. This directory records the step-by-step technical review
of the Budget MVP and provides the evidence for a planned combined Zenodo
publication. The reviewed system consists of the application assembly, Budget,
ORM, browser storage and deployment configuration.

## Review sequence

Each step is a separate work item. This index does not dispatch subsequent steps.

| Step | Question | Status |
| --- | --- | --- |
| [1. Source, build and deployment identity](01-baseline.md) | Which exact sources and build outputs are under review, and can they be matched to production? | Source/build inventory recorded; deployed identity unverified |
| [2. Architecture and boundaries](02-architecture.md) | Are responsibilities, dependencies and transaction ownership consistent with the agreed design? | Source review recorded; two follow-ups and transfer clarification |
| 3. Complete workflow correctness | Do editing, saving, history, category changes and guest transfer behave consistently across layers and failures? | Not started |
| 4. PostgreSQL and custom ORM integrity | Are atomicity, concurrency, owner scoping and historical projections enforced? | Not started |
| 5. Security boundaries | Do authentication, authorization, session handling and input/output boundaries protect account data? | Not started |
| 6. Builds, tests and delivery | Can the exact software be rebuilt, tested and distributed with reliable provenance? | Not started |
| 7. Performance and resilience | What do representative measurements and controlled failures show? | Not started |
| 8. Deployment and recovery | Can the deployed service be operated, diagnosed, backed up and restored as documented? | Not started |

Use read-only production observations. Run write tests and failure experiments
with synthetic data in isolated environments.

## Evidence in each step

Record the observation date, exact source revisions, scope, methods, completed
checks, observed results, findings and remaining uncertainty. Link to immutable
source revisions and specific CI runs. Distinguish checks performed during this
review from results reported by earlier implementation work. A missing
measurement is an evidence gap, not a measured failure.

Keep later corrections dated and explain changes to an earlier conclusion.
Version the reports through ordinary pull requests. The reviewed source baseline
remains the one recorded in Step 1 even when documentation commits are added.

## GitHub documentation arrangement

This directory is the canonical review record. The application README links
here. Repository READMEs should provide a short module overview and links to
relevant reports. A future organization overview can provide the project map,
review entry point and release links. The public overview uses
`.github/profile/README.md`, as described in
[GitHub's organization profile documentation](https://docs.github.com/en/organizations/collaborating-with-groups-in-organizations/customizing-your-organizations-profile).

If repository wikis are used later, link to these versioned reports so each
finding retains one maintained source and an immutable citation target.
Organization-profile creation and additional README/wiki edits are proposed
follow-up work.

## Release and package preparation

A GitHub release identifies a repository snapshot and its reviewed release
notes/assets. Maven packages distribute the actual Java components. Plan
publication around the outputs that exist:

| Repository | Outputs to evaluate for publication |
| --- | --- |
| `app` | Build-parent POM; authentication JAR and required runtime dependencies; application WAR and required configuration bundle |
| `orm` | ORM JAR and POM with source provenance |
| `budget` | Budget JAR and POM with source provenance |
| `portfolio`, `fire` | Deferred planning repositories; evaluate software releases when implementations exist |

The [existing registry decision](../build.md#ci-and-registry-decision--2026-11-05)
selects GitLab Maven Package Registry and describes it as unprovisioned. Keep that
decision unless the owner selects another registry. Step 6 should establish the
publication commands, version/tag mapping, dependency order, reproducible
artifacts and retained checksums before the first publication. Step 8 supplies
the evidence for any production-readiness claim. Recording this plan does not
create releases, tags, packages or a registry.

## Planned Zenodo publication

At the end, combine the reviewed business findings and technical step reports
into a report covering scope, exact software versions, methods, results,
limitations and reproduction instructions. Link each conclusion to its
underlying evidence. Choose the title, authors, licenses, citation metadata and
publication files from the completed work.

Software archives may have separate version-specific records linked from the
report where independently useful. Zenodo supports
[archiving GitHub releases](https://help.zenodo.org/docs/github/archive-software/github-upload/)
and [software citation metadata](https://help.zenodo.org/docs/github/describe-software/).
Use [version-specific DOIs](https://zenodo.org/help/versioning) when citing exact
reviewed software. Enabling integration, publishing software releases and
publishing the final report are future publication steps.
