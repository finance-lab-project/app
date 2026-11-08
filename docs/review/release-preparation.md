# Release preparation after the MVP review

Status on **8 November 2026: proposed procedure, not publication authorization**.
This is Step 6's delivery plan. It preserves the existing GitLab Maven registry
decision. No registry project, hosted tag, release or package was created.

## Decisions and prerequisites still open

The [Step 6 report](06-builds-tests-delivery.md) records a publisher guard failure
on Bash 3.2, inherited library SCM metadata, missing database CI gates and missing
retained candidate artifacts. Resolve those delivery findings before relying on
the publication path. Earlier S2/S3/S4 correctness findings also remain open;
release acceptance must state their disposition.

An owner decision is still needed for the actual approved GitLab project,
release commits/versions, code/report licenses, authors/citation metadata,
candidate retention and publication approval. This plan does not assume that a
declared version is unused in an uninspected registry. Never overwrite an
existing release coordinate. Portfolio/FIRE have no implemented software output
in this MVP and do not need placeholder packages.

## Dependency order and proposed mapping

These are **reviewed coordinates and proposed matching tag names**, not selected
release commits. Fixes or previously used registry versions may require new
versions and consumer pin updates.

| Order | Repository / output | Reviewed coordinate | Proposed tag relationship |
| --- | --- | --- | --- |
| 1 | app build parent | org.financelab:build-parent:pom:0.1.0 | App v0.1.0, exact approved parent bytes |
| 2 | ORM | org.financelab:orm:jar:0.1.0 | ORM v0.1.0, consumes approved parent |
| 3 | Budget | org.financelab:budget:jar:0.2.0 | Budget v0.2.0, consumes approved ORM/parent |
| 4 | App authentication + WAR | org.financelab:authentication:jar:0.1.0 and org.financelab:application:war:0.1.0 | App v0.1.0, consumes approved Budget/ORM and ships server authentication separately |

The app reactor is a build coordinator, not another public package. The parent
and app outputs share a repository/tag; an unchanged published parent must keep
its original bytes and provenance even if later app releases move on.

## Candidate acceptance and retained evidence

Before a hosted publication, record exact commits/tags, GAVs, effective project/
SCM URLs and each artifact's SHA-256. Verify clean source and tag equality with
explicit failing branches; do not rely on standalone conditional commands under
an unverified shell. Run the actual accepted full source versions through:

- ORM scripts/verify.sh, including its real PostgreSQL integration suite.
- Budget scripts/verify.sh and the relevant production UI/workflow regression
  checks, including assertions for any fixes accepted from this review.
- App scripts/prepare-budget.sh and scripts/verify.sh against the selected pins.
- The publication guard acceptance suite on the supported release shell:
  missing/mismatched tag, dirty source and invalid endpoint must all stop before
  the deployment goal. Recheck effective Maven metadata and reproducible archives.

Retain test reports, tool/runtime versions, exact dependency pins, checksum
manifests and the actual candidate bytes. The current CI prints digests but
uploads no artifacts; a digest alone cannot retrieve a candidate.

For the app bundle, retain the WAR, authentication JAR, its runtime lib contents,
Jetty authentication configuration, build-parent POM and required non-secret
operations/configuration files from the accepted source. Record the selected
JDK/Jetty distributions and their verified digests or retrieval references.
Account files, passwords, tokens and private recovery keys stay in protected
operator storage. The build manifest must distinguish those external requirements
from files actually present in the bundle.

This step verified a parent POM plus provenance through a local file registry,
and library JAR reproducibility. It did not test hosted authentication,
immutability, publication of the full module set or clean-registry consumption
of all transitive dependencies.

## Publication command shapes

The current parent helper is the existing command path **after its guard issue
is fixed and revalidated**:

    export GITLAB_MAVEN_URL=https://gitlab.com/api/v4/projects/APPROVED_PROJECT_ID/packages/maven
    export MAVEN_SETTINGS=/path/to/private-release-settings.xml
    bash scripts/publish-parent.sh

The placeholder must be replaced with the approved numeric project ID. Confirm
the endpoint and token server ID match, and verify duplicate-package controls
before sending bytes. GitLab documents
[token authentication and disabling duplicate Maven versions](https://docs.gitlab.com/user/packages/maven_repository/).
Registry administrators can still delete artifacts; retain an independent
manifest and candidate copy.

The equivalent module publication shape is shown below for ORM; Budget and
authentication use their own approved GAV/file/POM and provenance. These are
**future templates, not commands executed against GitLab in this review**:

    mvn -B -ntp -s "$MAVEN_SETTINGS" \
      org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file \
      -DrepositoryId=gitlab-maven "-Durl=$GITLAB_MAVEN_URL" \
      -Dfile=target/orm-0.1.0.jar -DpomFile=pom.xml -DgeneratePom=false \
      "-Dfiles=$PROVENANCE_FILE" -Dclassifiers=provenance -Dtypes=properties

Construct and check the sidecar before this command: coordinate, source
repository, full approved revision, exact tag and the deployed artifact digest.
Read the hosted POM, artifact and sidecar back and compare their actual bytes/
digests. The deploy-file goal has its own
[skip parameter](https://github.com/apache/maven-deploy-plugin/blob/maven-deploy-plugin-3.1.4/src/main/java/org/apache/maven/plugins/deploy/DeployFileMojo.java#L182);
the existing lifecycle maven.deploy.skip setting is not publication authorization
and must not be treated as an access-control guard.

After each dependency publication, prove consumption from the selected registry
with no preinstalled org.financelab artifacts and no relative parent fallback.
Only then advance to the next dependency. The app's current bootstrap path builds
pinned dependencies from source; registry-only consumption remains to be
established for the complete module set.

## GitHub release and Zenodo handoff

After candidate acceptance and explicit publication approval, GitHub releases
should link the exact repository tags, version-specific notes, applicable review
findings, retained assets and checksums. App is the assembled distribution;
Budget and ORM provide independently usable module archives/packages.

Choose code/report licenses and citation authors/metadata before enabling the
planned Zenodo publication. Preserve version-specific source and evidence links
when combining the business and technical reports. Deployment identity and
operational readiness still require Step 8 evidence; source build success does
not supply it.
