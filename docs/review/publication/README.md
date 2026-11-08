# Finance Lab MVP review - publication candidate

Prepared 8 November 2026, version 1.0. Combined product assessment and
eight-step technical review of the Budget MVP, prepared for Zenodo deposit.

## Files

- main.tex: standalone LaTeX source; open this folder in a LaTeX editor.
- finance-lab-mvp-review.pdf: visually checked 16-page A4 export.
- report.md: equivalent text/Markdown manuscript.
- business-review.md: retained initial product review (E0), with its limits.
- metadata-draft.json: proposed Zenodo field values; not submitted or a
  prevalidated API request. Actual publication date and DOI are omitted.
- evidence-manifest.json: exact runtime/review revisions, 37 evidence file
  paths, Git blob IDs and SHA-256 values.
- finance-lab-mvp-review-source.zip: document sources/metadata under report/
  and all 37 original merged review files under evidence/docs/review/.
- SHA256SUMS: prepared-file digests, excluding the checksum file itself.

The PDF and source ZIP are supplied alongside the document sources. The ZIP
contains six document-source/metadata files and the 37 evidence files; the PDF
is a separate export. The source archive preserves reports, raw results, external reproduction
fixtures and the synthetic screenshot from review snapshot
7edfb504d98ebe88df51a92124e217573addbd41. Each archived evidence file was checked
against that snapshot's Git blob ID. Runtime A/B/O source repositories are
linked by full commit, rather than copied as complete software distributions.

## Build and validation

The export used Tectonic 0.17.0. Its LaTeX packages/fonts were fetched on first
use and cached; the document is standalone but a first build needs the TeX
bundle. The desktop LaTeX compiler also successfully checked the final source.

From this folder (or the source archive's report/ folder):

    mkdir -p build
    SOURCE_DATE_EPOCH=1794096000 tectonic --keep-logs --keep-intermediates --outdir build main.tex

The output is build/main.pdf. The supplied export has been named
finance-lab-mvp-review.pdf. The fixed epoch is 8 November 2026 00:00 UTC;
it controls document-build metadata, not the timestamps of the review runs.
Recheck rendered pages and update SHA256SUMS after changing the manuscript,
metadata, DOI or export. TeX tool/bundle differences can change PDF bytes.

Validation completed for this candidate:

- Final command-line compilation: exit 0, no TeX warnings.
- Desktop-editor compilation: success for the final source.
- All 16 pages rendered and visually inspected; text bounds and A4 geometry
  checked, including the wrapped artifact paths and checksum table.
- Step 7's 25 latency groups / 707 samples and 397 RSS samples verified;
  Step 8's 24 tables / 47 synthetic rows checked from the retained results.
- All 37 archived evidence files match the immutable Git blob identifiers.
- Finding IDs and evidence links checked; S2-01/S3-01 remain one defect.
- Prepared source/metadata/archives checked for private host paths and secrets.
- Application tests were not rerun to prepare this manuscript.

## Zenodo handoff

Use a new Report record for this work. The prior report's DOI is a reference,
not this report's DOI or an IsNewVersionOf relation. Copy the proposed field
values from metadata-draft.json; set the real publication date when releasing.
If a DOI is reserved for this new object, insert it in the source/metadata,
rebuild the PDF and update the bundle/checksums before uploading.

The creator and ORCID match the referenced report. No affiliation is asserted.
The description uses the Abstract plus classification, synthetic-data and
AI-assistance disclosures. Upload the PDF and sources; the source archive also
retains the evidence. No Zenodo draft or published record was created by this
preparation, and publication/release acceptance is not implied by a GitHub merge.

## Attribution and license scope

Copyright 2026 Anton Serdyuchenko. The new report, its Markdown/LaTeX sources,
business-review narrative and document metadata use
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The layout and evidence
conventions adapt the author's earlier CC BY 4.0 report with new content;
that reference is credited on the cover and in R1.

This document license does not relicense the app, Budget, ORM or archived
reproduction code. Archived review evidence retains its upstream status.
Software code licensing/publication decisions remain unresolved in the review.

OpenAI ChatGPT/Codex assisted the review, probes, synthesis and typesetting.
Anton Serdyuchenko is the author; no peer review or independent reproduction
is claimed. Current deployed identity and production operational acceptance
remain unverified by the reviewed evidence.
