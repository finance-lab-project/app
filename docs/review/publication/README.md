# Finance Lab MVP review - publication candidate

Version 1.0. Original review: 7-8 November 2026. Revised: 10 November 2026.

The report combines the product assessment and eight technical steps with the
later accepted Budget R1 deployment on Yandex Cloud. It records the released
app/Budget revisions, deployed checksums, completed issue closure, fixed findings
and approved follow-up boundary. Original measurements remain version-specific.

## Files

- main.tex: standalone LaTeX source.
- finance-lab-mvp-review.pdf: visually checked 18-page A4 export.
- report.md: equivalent manuscript.
- business-review.md: retained original product review, E0.
- release-update-2026-11-10.md: dated release-status supplement, E11.
- release-evidence-2026-11-10.json: captured issue/PR records, inspected CI status
  and new anonymous public request results.
- metadata-draft.json: proposed Zenodo field values, without an invented DOI or
  publication date; not a submitted/prevalidated API request.
- evidence-manifest.json: original 37 review files and one separately retained
  immutable release-verification file, with revisions and hashes.
- finance-lab-mvp-review-source.zip: eight document-source/metadata files under
  report/, 37 frozen review files and one release-verification file under evidence/.
- SHA256SUMS: ten prepared-file checksums, excluding the checksum file itself.

The original evidence is from 7edfb504d98ebe88df51a92124e217573addbd41.
The accepted app candidate is d64b16c86fb2dbd3f820dbb39fb78fd1fbc3fe37,
Budget is a0645781b1d5e25536b4651749a372d6fd906315, and ORM is unchanged.
The full runtime repositories are linked by revision, not copied into this archive.

## Build and validation

The export uses Tectonic 0.17.0, with public TeX packages/fonts cached after first
use. The desktop LaTeX compiler also checks the saved source. From this folder,
or the source archive's report/ folder:

    mkdir -p build
    SOURCE_DATE_EPOCH=1794268800 tectonic --keep-logs --keep-intermediates --outdir build main.tex

The output is build/main.pdf; the delivery export is named
finance-lab-mvp-review.pdf. The fixed epoch is 10 November 2026 00:00 UTC and
controls document-build metadata, not review/release experiment timestamps.
Changes to the DOI, manuscript or export require refreshed checksums and ZIP.
Tool/bundle differences may change PDF bytes.

Validation for this revision covers compilation, rendered-page review, PDF
metadata/link/digest checks, original finding IDs, all archived source blobs,
ZIP content and prepared-file checksums. Application suites and protected
production checks were not rerun for this manuscript update. The reported
release tests are attributed to the captured project records; the two successful
candidate CI statuses and current public responses were freshly inspected.

## Reserve a DOI

1. Sign in to [Zenodo](https://zenodo.org/) and choose **+ → New upload**.
2. Use this report's title, creator and Report type from metadata-draft.json.
3. Under the DOI question, select **No**, then **Get a DOI now!**.
4. Copy the DOI, complete required fields and choose **Save draft**.

Reservation is registered on publication. Keep the draft; add its DOI to the
sources/metadata and rebuild before uploading the final files.
[Official instructions](https://help.zenodo.org/docs/deposit/create-new-upload/).

This is a new report record. The earlier report's DOI is a bibliographic
reference; no version relationship is assigned. No Zenodo draft, reservation or
publication was created by this document preparation.

## Release status and limits

The R1 tracker and delivery issue are closed. The approved private Budget R1
release is deployed and accepted on Yandex Cloud, with no outstanding blockers
within the agreed scope. S2-01/S3-01, S3-02 and S4-01 are fixed in the accepted
Budget pin. Actual backup/mail/recovery, selected-VM and live acceptance checks
are documented in E11.

Sustained capacity, full replacement-VM/DNS-cutover timing, later retention
expiration/billing observations and the other documented engineering items are
follow-ups. They do not reopen R1 acceptance. Market demand was not measured.

## Attribution and license

Copyright 2026 Anton Serdyuchenko. This report, its Markdown/LaTeX sources,
retained narratives and document metadata use
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The LaTeX presentation adapts the author's
[earlier CC BY 4.0 report](https://doi.org/10.5281/zenodo.23293506); content is new.

The document license does not relicense the application, modules or archived
reproduction code, which retain their upstream status. OpenAI ChatGPT/Codex
assisted the review and preparation; Anton Serdyuchenko is the author. No peer
review or independent full reproduction is claimed.
