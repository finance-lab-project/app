# Budget R1 release evidence update - 10 November 2026

This is the dated E11 supplement to the 7-8 November MVP review. The original
runtime baseline, measurements and 37 archived review files remain unchanged.
This update records later fixes and accepted deployment, rather than treating
the original review findings as the state of the released application.

## Acceptance and issue closure

The inspected [release acceptance record](https://github.com/finance-lab-project/app/issues/6)
documents completed approval, production activation and all agreed live checks.
The R1 tracker, app issue #1, and delivery issue #6 were closed on 10 November.
The issue listings contained six closed app issues, four closed Budget issues
and three closed ORM issues, with no open issues in these three repositories.

The production release is accepted within the agreed private Budget R1 scope.
Longer-term follow-ups do not constitute remaining R1 release blockers.

## Sources and deployed artifact

| Component | Accepted source |
| --- | --- |
| App candidate | d64b16c86fb2dbd3f820dbb39fb78fd1fbc3fe37 |
| Budget | a0645781b1d5e25536b4651749a372d6fd906315 |
| ORM | 8daaf91d07d2e30b563322b22a0c6da7545578e5 |

The accepted WAR SHA-256 is
b14e206910217d8da34aa99f7b181cd0b3316c2ea281fad95a73347f9cc21746.
The full artifact manifest and activation results are retained in
[merged app PR #12](https://github.com/finance-lab-project/app/pull/12).
Its reviewed head is the exact app candidate above; its merge is
43d51524ad1c8938b57624b1a9ee393da72d42e4.

The release record also identifies the previous deployed source as the original
review baseline, with the original a2a81f71... WAR digest. Deployment identity
was unknown to the 7-8 November review but was subsequently established.

## Fixes and verification

[Budget PR #10](https://github.com/finance-lab-project/budget/pull/10) fixes
S2-01/S3-01: confirmed-save acknowledgement is retained separately from refresh
failure, with no write replay.

[Budget PR #11](https://github.com/finance-lab-project/budget/pull/11) fixes S3-02
and S4-01: dialog reuse preserves the next decision, and initial category facts
are validated in the production reader. The released Budget pin includes both
merged changes. The record reports 119 unit, 34 PostgreSQL integration and
15 Chromium tests, plus successful assembled-app checks.

The exact-candidate [Linux run](https://github.com/finance-lab-project/app/actions/runs/42794564231)
and [PR CI run](https://github.com/finance-lab-project/app/actions/runs/42792802711)
were independently inspected for completed/success status at the app candidate.

## Deployment and operational observations

The release record reports a verified pre-change backup and real mailbox
receipt, independent recovery-identity download/checksum/decryption, matching
release hashes and a 5.08-second application activation/readiness interval.
Fingerprints of all 24 Budget tables matched before/after restart and acceptance.
A real configured-account browser session checked planner, categories, history,
secure session attributes and logout without changing production financial data.

A separate selected-VM synthetic database/application fixture passed saves,
totals, renamed categories, pinned history and PostgreSQL/Jetty restart readback.
The live application, proxy, PostgreSQL and enabled backup timer were active;
boot configuration and restricted listeners were recorded.

A fresh real-provider backup download, decryption, clean PostgreSQL restore and
authenticated application checks took 24.13 seconds with prepared recovery
equipment. Replacement-VM provisioning and DNS cutover are excluded from that
timing. The scheduled 8 November 03:00 Europe/Moscow backup, its delivered report,
provider object and independently downloaded checksum matched.

## Retained follow-ups and evidence boundary

The accepted follow-ups include dependency declarations, limited interrupted
history replay, Maven publication guards/metadata, automatic persistence CI,
query growth/deadlines, sustained representative capacity, log byte limits and
full replacement-VM/DNS-cutover timing. Actual 28-day expiration and a complete
billing period are later observations, not additional R1 release gates.

The publication update inspected public primary records and CI status; it did
not repeat protected administration or private authenticated production checks.
Four new anonymous requests confirmed HTTP-to-HTTPS redirect, login/guest
availability and denied protected access. The machine-readable evidence file
retains these responses and the dated issue/PR records. No production change,
new backup, deployment or paid resource operation was performed for this update.

Market demand and commercial performance were not measured by either the
technical review or release acceptance.
