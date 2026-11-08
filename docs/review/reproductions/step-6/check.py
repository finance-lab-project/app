#!/usr/bin/env python3
"""Review-only build/package checks; the publisher is redirected to a local file registry."""
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

REFS = {
    "app": "f78682778eebed86730ec457f63c96648f19370d",
    "budget": "43d86d28965a241cd45f7938c1adfb2ec50f8132",
    "orm": "8daaf91d07d2e30b563322b22a0c6da7545578e5",
}
EXPECTED = {
    "application/target/finance-lab.war": "a2a81f71a567587934cf7c830b3adf2b4c10d4fa1e9004f9fc60fbfdba27307a",
    "authentication/target/authentication-0.1.0.jar": "661f6f65ccbde13e31cc432870ce16bb8e0e466cede7b8235bce8dd505645a24",
    "authentication/target/lib/password4j-1.8.4.jar": "d1406a158533b65bda7c20cebf7bf2a9a0626f8931fd1dc3d39243af5849edf5",
    "config/jetty-auth.xml": "fd8b7ab5d9393f7e1e945e6df0f384c4ac96a8da878d3dbc0ffaf28773cfb494",
    "build-parent/pom.xml": "aa61d398a42e3df231b696532a545872296bba8d039b453cf5ad98f5b284a34f",
}
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
DEPLOY = "org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file"
ENDPOINT = "https://gitlab.com/api/v4/projects/12345678/packages/maven"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def manifest(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        raw = archive.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
    return dict(line.split(": ", 1) for line in raw.splitlines() if ": " in line)


def main():
    sources = {name: Path(os.environ["REVIEW_" + name.upper() + "_DIR"]).resolve() for name in REFS}
    evidence = Path(os.environ["REVIEW_EVIDENCE_DIR"]).resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    settings = str(Path(os.environ["REVIEW_MAVEN_SETTINGS"]).resolve())
    real_mvn = shutil.which("mvn")
    assert real_mvn and Path(settings).is_file()
    for name, root in sources.items():
        assert subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip() == REFS[name]
        assert not subprocess.check_output(["git", "status", "--porcelain"], cwd=root, text=True).strip()
    for path, expected in EXPECTED.items():
        assert digest((sources["app"] / path).read_bytes()) == expected, path
    parent = (sources["app"] / "build-parent/pom.xml").read_bytes()
    assert all((sources[name] / "build-support/build-parent-0.1.0.pom").read_bytes() == parent for name in ("orm", "budget"))
    results = {"schema_version": 1, "source_baseline": REFS,
               "verified_baseline_sha256": EXPECTED, "parent_copies_byte_identical": True,
               "libraries": {}, "publisher_cases": []}
    bash = shutil.which("bash")
    version = subprocess.check_output([bash, "--version"], text=True).splitlines()[0]
    probe = subprocess.run([bash, "-c", "set -e; [[ no == yes ]]; echo CONDITIONAL_CONTINUED"],
                           text=True, capture_output=True)
    condition_stops = probe.returncode != 0
    results["shell"] = {"version": version, "failed_conditional_exit_code": probe.returncode,
                        "failed_conditional_stops_script": condition_stops}

    def run(argv, cwd, log_name, env=None, success=True):
        with (evidence / log_name).open("w") as log:
            status = subprocess.run(argv, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
        if success and status:
            raise RuntimeError("Command failed; inspect " + log_name)
        return status

    with tempfile.TemporaryDirectory(prefix="finance-lab-step-6-") as temporary:
        work = Path(temporary)
        repos = {}
        for name, root in sources.items():
            destination = work / name
            run(["git", "clone", "--quiet", "--no-hardlinks", "--local", "--no-checkout", str(root), str(destination)],
                work, name + "-clone.log")
            run(["git", "-c", "advice.detachedHead=false", "checkout", "--quiet", REFS[name]],
                destination, name + "-checkout.log")
            repos[name] = destination

        with zipfile.ZipFile(sources["app"] / "application/target/finance-lab.war") as war:
            for name, version in (("orm", "0.1.0"), ("budget", "0.2.0")):
                jar = repos[name] / "target" / (name + "-" + version + ".jar")
                hashes = []
                for attempt in (1, 2):
                    run([real_mvn, "-B", "-ntp", "clean", "package", "-DskipTests", "-DskipITs",
                         "-Dsource.revision=" + REFS[name]], repos[name], name + "-package-" + str(attempt) + ".log")
                    hashes.append(digest(jar.read_bytes()))
                assert hashes[0] == hashes[1], name
                jar_bytes = jar.read_bytes()
                assert war.read("WEB-INF/lib/" + jar.name) == jar_bytes, name
                fields = manifest(jar_bytes)
                assert fields["Build-Source-Revision"] == REFS[name]
                assert fields["Build-Source-Repository"] == "https://github.com/finance-lab-project/" + name
                effective = evidence / (name + "-effective-pom.xml")
                run([real_mvn, "-B", "-ntp", "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom",
                     "-Dsource.revision=" + REFS[name], "-Doutput=" + str(effective)],
                    repos[name], name + "-effective-pom.log")
                pom = ET.parse(effective).getroot()
                results["libraries"][name] = {
                    "version": version, "first_sha256": hashes[0], "second_sha256": hashes[1],
                    "byte_identical": True, "matches_nested_war_bytes": True,
                    "manifest_repository": fields["Build-Source-Repository"],
                    "manifest_revision": fields["Build-Source-Revision"],
                    "effective_project_url": pom.findtext("m:url", namespaces=NS),
                    "effective_scm": {k: pom.findtext("m:scm/m:" + k, namespaces=NS)
                                      for k in ("url", "connection", "tag")},
                    "tests_rerun": False,
                }

        # Exercise the unchanged publisher. Only its deployment URL is redirected.
        # Only two synthetic input URLs are accepted by the wrapper, both mapped to file://.
        bin_dir = work / "bin"
        bin_dir.mkdir()
        calls = work / "deployment-calls.jsonl"
        registry = work / "registry"
        wrapper = bin_dir / "mvn"
        wrapper.write_text("""#!/usr/bin/env python3
import json,os,subprocess,sys
from pathlib import Path
args=sys.argv[1:]
if '-version' in args or '--version' in args:
    raise SystemExit(subprocess.call([os.environ['REVIEW_REAL_MVN']]+args))
expected='-Durl='+os.environ['REVIEW_SYNTHETIC_ENDPOINT']
goal='org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file'
urls=[x for x in args if x.startswith('-Durl=')]
allowed={expected,'-Durl=https://foreign.invalid/packages/maven'}
if goal not in args or len(urls)!=1 or urls[0] not in allowed:
    raise SystemExit('Unexpected Maven invocation: blocked by local review wrapper')
with Path(os.environ['REVIEW_DEPLOY_CALLS']).open('a') as f:
    f.write(json.dumps({'goal':goal,'approved_input_url':urls[0]==expected,'redirected_to_file_registry':True})+'\\n')
args=[('-Durl='+os.environ['REVIEW_LOCAL_REGISTRY']) if x==urls[0] else x for x in args]
raise SystemExit(subprocess.call([os.environ['REVIEW_REAL_MVN']]+args))
""")
        wrapper.chmod(0o700)
        env = dict(os.environ)
        env.update({"PATH": str(bin_dir) + os.pathsep + env["PATH"], "MAVEN_SETTINGS": settings,
                    "GITLAB_MAVEN_URL": ENDPOINT, "REVIEW_SYNTHETIC_ENDPOINT": ENDPOINT,
                    "REVIEW_REAL_MVN": real_mvn, "REVIEW_DEPLOY_CALLS": str(calls),
                    "REVIEW_LOCAL_REGISTRY": registry.as_uri()})
        app = repos["app"]

        def case(name, intended_acceptance, url=None):
            before = len(calls.read_text().splitlines()) if calls.exists() else 0
            local = dict(env)
            local["REVIEW_LOCAL_REGISTRY"] = (registry / name).as_uri()
            if url:
                local["GITLAB_MAVEN_URL"] = url
            status = run([bash, "scripts/publish-parent.sh"], app, "publisher-" + name + ".log",
                         env=local, success=False)
            after = len(calls.read_text().splitlines()) if calls.exists() else 0
            observed_acceptance = status == 0
            expected_characterization = intended_acceptance or not condition_stops
            assert observed_acceptance == expected_characterization, name
            assert after - before == (1 if observed_acceptance else 0), name
            record = {"case": name, "intended_acceptance": intended_acceptance,
                      "observed_acceptance": observed_acceptance, "exit_code": status,
                      "deployment_goal_invocations": after - before,
                      "guard_violation_reproduced": observed_acceptance != intended_acceptance}
            if observed_acceptance:
                target = registry / name / "org/financelab/build-parent/0.1.0/build-parent-0.1.0.pom"
                assert target.is_file(), name
                record["local_artifact_written"] = True
                record["deployed_pom_matches_frozen_parent"] = target.read_bytes() == parent
            results["publisher_cases"].append(record)

        case("missing_tag", False)
        run(["git", "tag", "v0.1.0", "HEAD^"], app, "wrong-tag-create.log")
        case("mismatched_tag", False)
        run(["git", "tag", "-d", "v0.1.0"], app, "wrong-tag-remove.log")
        run(["git", "tag", "v0.1.0", "HEAD"], app, "fixture-tag-create.log")
        case("unapproved_endpoint", False, "https://foreign.invalid/packages/maven")
        changed_pom = app / "build-parent/pom.xml"
        before = changed_pom.read_bytes()
        changed_pom.write_bytes(before + b"\n<!-- Synthetic review-only dirty artifact fixture. -->\n")
        try:
            case("dirty_checkout", False)
        finally:
            changed_pom.write_bytes(before)
        case("clean_matching_tag", True)
        deployed = registry / "clean_matching_tag/org/financelab/build-parent/0.1.0"
        assert (deployed / "build-parent-0.1.0.pom").read_bytes() == parent
        sidecar = deployed / "build-parent-0.1.0-provenance.properties"
        provenance = dict(line.split("=", 1) for line in sidecar.read_text().splitlines() if "=" in line)
        assert provenance == {"coordinates": "org.financelab:build-parent:pom:0.1.0",
                              "source.repository": "https://github.com/finance-lab-project/app",
                              "source.revision": REFS["app"], "source.tag": "v0.1.0",
                              "artifact.sha256": digest(parent)}
        results["parent_publication"] = {"destination": "temporary file registry; no hosted write",
                                         "pom_byte_identical": True, "provenance_matches_source_and_digest": True,
                                         "hosted_registry_auth_or_immutability_tested": False}

    for root in sources.values():
        assert not subprocess.check_output(["git", "status", "--porcelain"], cwd=root, text=True).strip()
    results["tracked_sources_unchanged"] = True
    results["tests_note"] = "Existing test results are reused from review Steps 3-5. Library builds intentionally skip tests; this checks new artifact reproducibility, not a new test-suite run."
    (evidence / "local-results.json").write_text(json.dumps(results, indent=2) + "\n")
    print("Two libraries rebuilt twice and matched the WAR; five publication-helper cases characterized.")
    print("Only a temporary file registry and disposable local tags were used.")


if __name__ == "__main__":
    main()
