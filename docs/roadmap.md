# Current Finance Lab release plan

Updated 2026-11-10. The eleven implementation prerequisites are closed and the initial private deployment is running. The completion candidate integrates the confirmed-save, dialog and category-integrity fixes. Remaining R1 work is candidate verification, approved activation and human acceptance. Exact results and the accepted follow-ups are linked from the [R1 verification record](r1-verification.md).

## Agreed first-release scope

- Java/Vaadin without Spring; PostgreSQL Anchor Modeling and a custom mapper with separate Java definitions.
- Modular monolith with separate repositories and immutable library artifacts; Budget first, Portfolio/FIRE later.
- Russian UI, RUB, nonnegative inputs with at most two decimal places, exact arithmetic and explicit unset/zero distinction.
- Ten starter spending categories, add/rename/reorder/archive, shared catalog and preserved monthly values/history; income and investments separate.
- Explicit Save; changed monthly saves are atomic revisions; no-op saves create no history. Catalog changes have separate revisions. Prior Budget revisions retain original catalog labels/order.
- Version conflicts preserve drafts; no automatic write retries. History is read-only, with recorded changes rather than backdating/bitemporal behavior.
- One configured account, private online application and HTTPS on one Yandex Cloud VM with PostgreSQL on the same VM.
- Weekly Sunday 03:00 Europe/Moscow encrypted off-VM backups, 28-day retention, pre-database-change backups and verified restore.
- Clean start: no old data import or compatibility requirement. Legacy code can be removed during bootstrap. No repository deletion, live-site change or cloud deployment was performed by planning setup.

The [architecture](architecture.md) records the agreed contracts. The [build decision](build.md) selects GitHub Actions for CI and a separately provisioned GitLab Maven registry.

## Issue index

| Repository | Work | Status and evidence |
| --- | --- | --- |
| `app` | [R1 — Deliver the private Russian Budget application](https://github.com/finance-lab-project/app/issues/1) | Release tracker |
| `budget` | [Design R1 PostgreSQL schema, constraints and initial migration](https://github.com/finance-lab-project/budget/issues/1) | [Merged PR #5](https://github.com/finance-lab-project/budget/pull/5) |
| `app` | [Validate and pin the Java/Vaadin/Jetty/PostgreSQL runtime stack](https://github.com/finance-lab-project/app/issues/2) | [Merged PR #7](https://github.com/finance-lab-project/app/pull/7) |
| `orm` | [Design the minimal Anchor mapping, transaction and projection API](https://github.com/finance-lab-project/orm/issues/1) | [Merged PR #4](https://github.com/finance-lab-project/orm/pull/4) |
| `app` | [Bootstrap the Maven parent and Java application assembly](https://github.com/finance-lab-project/app/issues/3) | [Merged PR #8](https://github.com/finance-lab-project/app/pull/8) |
| `orm` | [Implement scoped JDBC transactions, codecs and failure outcomes](https://github.com/finance-lab-project/orm/issues/2) | [Merged PR #5](https://github.com/finance-lab-project/orm/pull/5) |
| `orm` | [Implement Anchor SQL generation and current/history projections](https://github.com/finance-lab-project/orm/issues/3) | [Merged PR #6](https://github.com/finance-lab-project/orm/pull/6) |
| `budget` | [Implement RUB Budget values and the configurable category catalog](https://github.com/finance-lab-project/budget/issues/2) | [Merged PR #6](https://github.com/finance-lab-project/budget/pull/6) |
| `budget` | [Implement atomic Budget/catalog saves and recorded history](https://github.com/finance-lab-project/budget/issues/3) | [Merged PR #7](https://github.com/finance-lab-project/budget/pull/7) |
| `budget` | [Build the Russian monthly planner, category settings and history views](https://github.com/finance-lab-project/budget/issues/4) | [Merged PR #8](https://github.com/finance-lab-project/budget/pull/8) |
| `app` | [Implement the configured single-account login and session boundary](https://github.com/finance-lab-project/app/issues/4) | [Merged PR #9](https://github.com/finance-lab-project/app/pull/9) |
| `app` | [Prepare the single-VM deployment and weekly backup/restore runbook](https://github.com/finance-lab-project/app/issues/5) | [Merged PR #10](https://github.com/finance-lab-project/app/pull/10) |
| `app` | [Verify and release the private Budget R1 on Yandex Cloud](https://github.com/finance-lab-project/app/issues/6) | [Completion candidate and release acceptance](r1-verification.md#r1-completion-candidate--10-november-2026) |
| `portfolio` | [Later — Review Portfolio requirements and module boundary](https://github.com/finance-lab-project/portfolio/issues/1) | Later; outside R1 |
| `fire` | [Later — Review FIRE requirements and calculation contracts](https://github.com/finance-lab-project/fire/issues/1) | Later; outside R1 |

Dependencies are specified in the individual issues. The [R1 tracker](https://github.com/finance-lab-project/app/issues/1) owns release acceptance; a merged feature or a local demo does not complete the online release.

## Tracking rules

Use one private project board per repository. Project status records the work lifecycle; issue bodies hold scope, dependencies and evidence. Public source does not imply public access to the deployed app. Track design and delivery directly, with no separate learning issues or learning gates. Former learning records are removed from active planning, not marked as completed competence.

Portfolio and FIRE remain outside the R1 runtime.

## Earlier work

Frontend category issues [#9](https://github.com/finance-lab-project/frontend/issues/9), [#10](https://github.com/finance-lab-project/frontend/issues/10) and [#11](https://github.com/finance-lab-project/frontend/issues/11) supply the product category requirements. Their React/localStorage implementation approach and old currency/language roadmap do not constrain this rewrite.

Previous delivery issues carry links to the new scope where relevant. Old releases/commits remain historical evidence; frozen platform/Go/BFF infrastructure is not reactivated. Personal/career planning is outside this product board setup and must not be copied into public repositories.

## Verification and release

Implementation evidence must include appropriate real PostgreSQL transaction/history/concurrency tests, Vaadin browser checks, configured login/session checks and exact assembled artifact revisions. Online acceptance additionally needs observed HTTPS, persistence across restart and off-VM restore evidence.

Current infrastructure figures are estimates, not a purchased resource or performance result: about 2 331 RUB for a 30-day month and 2 408 RUB for 31 days, with a 2 600 RUB planning allowance and existing domain/email. See the dated source-backed estimate in the architecture. Human approval remains necessary for merge, release, spending and deployment.
