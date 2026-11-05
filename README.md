# Finance Lab App

Java/Vaadin modular monolith without Spring, backed by PostgreSQL Anchor Modeling and a custom ORM. Public source repositories; the first deployed application is private and single-user.

## Start here

- [Architecture and agreed contracts](docs/architecture.md)
- [Runtime version pins and reproducible compatibility spike](docs/runtime-stack.md)
- [Application build, configuration, CI and immutable artifacts](docs/build.md)
- [Current release plan and issue index](docs/roadmap.md)
- [Issues](https://github.com/finance-lab-project/app/issues)

## Repository ownership

| Repository | Responsibility | R1 |
| --- | --- | --- |
| [app](https://github.com/finance-lab-project/app) | Architecture, release coordination, Maven parent/application assembly, login and runtime configuration | Active design |
| [orm](https://github.com/finance-lab-project/orm) | Anchor metadata, JDBC transactions, SQL generation and history projections | Active design |
| [budget](https://github.com/finance-lab-project/budget) | Monthly planning, categories, validation, Vaadin UI, mappings and migrations | First business module |
| [portfolio](https://github.com/finance-lab-project/portfolio) | Independent holdings/transactions/calculations | Planning only; deferred |
| [fire](https://github.com/finance-lab-project/fire) | Independent goals/assumptions/projections | Planning only; deferred |

## First release

Russian interface and money/date presentation, RUB only, up to two decimal places without silent rounding. One configured account, no public registration. Explicit Save, atomic monthly revisions, read-only history and category archiving that retains saved amounts.

Start from an empty database; no V2/V3 import or legacy compatibility requirement. Legacy code removal belongs to rewrite bootstrap. Git history and the agreed product/design decisions remain useful.

Hosting plan: one Yandex Cloud VM for proxy, Jetty/Vaadin and PostgreSQL; 2 shared vCPUs at 20%, 4 GiB RAM, 30 GiB network SSD, static IPv4. Weekly encrypted off-VM backups retained for four weeks, plus pre-migration backups. Planning allowance: 2 600 RUB/month; no resources purchased or deployed.

## Delivery status

The Maven parent and initial Java/Vaadin WAR assembly are implemented; ORM/Budget integration, login and deployment remain separate work. The [build guide](docs/build.md) covers local verification, bootstrap-only startup, GitHub Actions checks and the selected GitLab Maven registry. The registry is not provisioned and no artifacts are published. The [runtime report](docs/runtime-stack.md) records the compatibility spike; it does not establish application capacity.

Issues carry scoped acceptance criteria and dependencies. Each repository has a private project board. Board status is a planning signal, not permission to implement, merge, deploy or spend. There are no separate learning issues in the new backlog. Use synthetic data and keep secrets out of repositories, issues and logs.
