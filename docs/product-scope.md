# Finance Lab: product needs and Budget v1 scope

Date: 2026-11-10. Status: agreed product requirements; delivery approach and
technology choices remain open.

This document is the starting point for the next product iteration. The
[existing architecture](architecture.md) and [release record](roadmap.md)
describe the current implementation and its delivery history.

## User, problem and outcome

The first version serves one person who currently maintains a personal budget
in a spreadsheet and periodically updates monthly totals, often weekly.
The application should make that work convenient through a browser on both a
computer and a phone, with the same budget available across devices.

The main job is to distribute expected income, update actual income, spending
and investment contributions, and understand category balances and monthly
and yearly results. The interface should make financial figures easy to read
and edit without relying on a wide spreadsheet.

Budget v1 succeeds when the regular budgeting workflow and its monthly/yearly
review can be completed in Finance Lab without the spreadsheet. Investment
portfolio management and FIRE calculations are later product areas.

## Budget v1 capabilities

1. **Monthly totals.** Select a month and year; manually enter and correct actual
   income, cumulative spending for each category and actual investment
   contributions. Updates replace the recorded monthly total rather than add
   individual purchase transactions.
2. **Planning.** Enter a prepared monthly planning-income amount, set a target
   investment share, calculate essential needs and distribute the remaining
   budget between discretionary categories.
3. **Category settings.** Add and rename categories, assign them to essential
   needs or discretionary spending, change the investment target and set
   discretionary-category shares.
4. **Plan versus actual.** Show category plans, actual amounts, monthly remaining
   amounts and accumulated buffers as distinct figures.
5. **Monthly and yearly review.** Show income, spending, investment contributions
   and category plan/actual results with their accumulated buffers. Annual plans
   aggregate the applicable monthly plans.
6. **Manual history entry.** Enter past monthly plans and actual amounts so that
   historical results and accumulated category buffers can be calculated.

## Planning rules

- Actual monthly income is separate from the income amount used for planning.
  In v1, the planning-income amount is supplied manually after an offline
  calculation. A later capability will calculate it from salary-tagged receipts
  for the previous full calendar year divided by twelve.
- Each essential category's monthly plan is the average of its expenses over
  the previous full calendar year. Large one-off expenses remain included.
- For the initial setup, prepared essential-category averages may use the
  completed months available in the reference data. That temporary starting
  baseline does not change the normal previous-calendar-year rule.
- A new essential category without expense history receives a manually entered
  initial monthly plan.
- The target investment share is configurable. If the target contribution plus
  essential needs exceeds planning income, reduce the planned investment
  allocation to the amount available after essential needs, down to zero.
  Show the reduced allocation relative to the target.
- If essential needs alone exceed income, planned investments and discretionary
  spending are zero and the uncovered shortfall remains visible.
- The remainder after essential needs and planned investments is the
  discretionary budget. The user assigns shares to its categories; those shares
  must total 100% of the discretionary budget.
- Changes to planning income or allocation rules apply to the current and future
  months. Previously recorded plans for past months remain unchanged.

## Category buffers

A category buffer accumulates its monthly plan minus actual spending across
the recorded history. Both savings and overspending carry between months and
across year boundaries.

The monthly plan and buffer remain separate. In a synthetic example, spending
12,000 against a plan of 10,000 produces a buffer of -2,000; the next month's
plan stays 10,000. Accumulated savings can likewise explain how spending above
a monthly plan can remain within the accumulated budget.

Starting buffers are calculated from manually entered historical monthly plans
and actual amounts, rather than entered as standalone opening balances.

## First-version boundary

The first version covers the Budget product area, browser access across devices,
manual data entry and the planning/review rules above. It does not require
individual-purchase bookkeeping, automatic bank import, investment assets or
trades, portfolio valuation, FIRE calculations, or dedicated desktop/phone apps.
Automatic calculation of the planning-income baseline is deferred.

Product areas do not prescribe repositories, services, frameworks, persistence
models or deployment topology. Those choices follow the product workflow and
its acceptance criteria.
