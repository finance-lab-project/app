#!/usr/bin/env python3
"""Local-only Step 8 drill; no production endpoint or cloud credentials are used."""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
import zipfile

REF="f78682778eebed86730ec457f63c96648f19370d"
EXPECTED={
 "application/target/finance-lab.war":"a2a81f71a567587934cf7c830b3adf2b4c10d4fa1e9004f9fc60fbfdba27307a",
 "authentication/target/authentication-0.1.0.jar":"661f6f65ccbde13e31cc432870ce16bb8e0e466cede7b8235bce8dd505645a24",
 "authentication/target/lib/password4j-1.8.4.jar":"d1406a158533b65bda7c20cebf7bf2a9a0626f8931fd1dc3d39243af5849edf5",
 "config/jetty-auth.xml":"fd8b7ab5d9393f7e1e945e6df0f384c4ac96a8da878d3dbc0ffaf28773cfb494",
 "build-parent/pom.xml":"aa61d398a42e3df231b696532a545872296bba8d039b453cf5ad98f5b284a34f"}
LABEL="org.financelab.review=step-8"

def main():
    source=Path(os.environ["REVIEW_APP_DIR"]).resolve()
    evidence=Path(os.environ["REVIEW_EVIDENCE_DIR"]).resolve()
    if evidence.exists():raise SystemExit("Use a new evidence directory.")
    evidence.mkdir(parents=True,mode=0o700)
    here=Path(__file__).resolve().parent
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=source,text=True).strip()==REF
    assert not subprocess.check_output(["git","status","--porcelain"],cwd=source,text=True).strip()
    for name,digest in EXPECTED.items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    def output(*args):return subprocess.check_output(list(args),text=True).strip()
    emulator=container=None
    with tempfile.TemporaryDirectory(prefix="finance-lab-step-8-") as temporary:
        inputs=Path(temporary)
        with zipfile.ZipFile(source/"application/target/finance-lab.war") as war:
            with zipfile.ZipFile(io.BytesIO(war.read("WEB-INF/lib/budget-0.2.0.jar"))) as budget:
                (inputs/"migration.sql").write_bytes(budget.read("db/migration/V1__budget_schema.sql"))
                (inputs/"runtime.sql").write_bytes(budget.read("db/permissions/runtime.sql"))
        try:
            with (evidence/"image-build.log").open("w") as log:
                subprocess.run(["docker","build","-t","finance-lab-recovery-check:local",
                    str(source/"ops/single-vm/checks")],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=300)
            emulator=output("docker","run","--rm","-d","--network","none","--label",LABEL,"motoserver/moto:5.1.14")
            container="finance-lab-step8-"+uuid.uuid4().hex
            started=time.monotonic()
            with (evidence/"drill.log").open("w") as log:
                result=subprocess.run(["docker","run","--rm","--name",container,"--label",LABEL,
                    "--network","container:"+emulator,
                    "--mount","type=bind,src="+str(source/"ops/single-vm")+",dst=/ops,readonly",
                    "--mount","type=bind,src="+str(here)+",dst=/review,readonly",
                    "--mount","type=bind,src="+str(inputs)+",dst=/inputs,readonly",
                    "--mount","type=bind,src="+str(evidence)+",dst=/evidence",
                    "--entrypoint","python3","finance-lab-recovery-check:local","/review/drill.py"],
                    stdout=log,stderr=subprocess.STDOUT,timeout=300)
            assert result.returncode==0,"See local drill.log"
            data=json.loads((evidence/"extended-drill.json").read_text())
            assert data["completed"] is True
            data["elapsed_drill_container_s"]=round(time.monotonic()-started,3)
            data["app_revision"]=REF
            data["accepted_sha256"]=EXPECTED
            data["ops_sha256"]={str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted((source/"ops/single-vm").rglob("*")) if p.is_file()}
            data["images"]={name:json.loads(output("docker","image","inspect",name))[0]["Id"]
                for name in ["finance-lab-recovery-check:local","motoserver/moto:5.1.14"]}
            (evidence/"results.json").write_text(json.dumps(data,indent=2)+"\n")
            assert not subprocess.check_output(["git","status","--porcelain"],cwd=source,text=True).strip()
            print("Completed isolated Step 8 recovery drill.")
        finally:
            if container:subprocess.run(["docker","rm","-f",container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            if emulator:subprocess.run(["docker","stop",emulator],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)

if __name__=="__main__":main()
