#!/usr/bin/env python3
"""Local-only performance/resilience fixture. Reuses accepted binary bytes."""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import urllib.request
import uuid
import zipfile

REF = "f78682778eebed86730ec457f63c96648f19370d"
EXPECTED = {
 "application/target/finance-lab.war":"a2a81f71a567587934cf7c830b3adf2b4c10d4fa1e9004f9fc60fbfdba27307a",
 "authentication/target/authentication-0.1.0.jar":"661f6f65ccbde13e31cc432870ce16bb8e0e466cede7b8235bce8dd505645a24",
 "authentication/target/lib/password4j-1.8.4.jar":"d1406a158533b65bda7c20cebf7bf2a9a0626f8931fd1dc3d39243af5849edf5",
 "config/jetty-auth.xml":"fd8b7ab5d9393f7e1e945e6df0f384c4ac96a8da878d3dbc0ffaf28773cfb494",
 "build-parent/pom.xml":"aa61d398a42e3df231b696532a545872296bba8d039b453cf5ad98f5b284a34f",
}


def main():
    source = Path(os.environ["REVIEW_APP_DIR"]).resolve()
    evidence = Path(os.environ["REVIEW_EVIDENCE_DIR"]).resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    here = Path(__file__).resolve().parent
    java_home, jetty_home = Path(os.environ["JAVA_HOME"]), Path(os.environ["JETTY_HOME"])
    java, javac = str(java_home/"bin/java"), str(java_home/"bin/javac")
    assert subprocess.check_output(["git","rev-parse","HEAD"],cwd=source,text=True).strip()==REF
    assert not subprocess.check_output(["git","status","--porcelain"],cwd=source,text=True).strip()
    for name, expected in EXPECTED.items():
        assert hashlib.sha256((source/name).read_bytes()).hexdigest()==expected, name
    assert {p.name for p in (source/"authentication/target/lib").glob("*.jar")}=={"password4j-1.8.4.jar"}
    assert "27.0.1.0.3" in subprocess.check_output([java,"-version"],stderr=subprocess.STDOUT,text=True)
    assert "Apache Maven 3.10.0" in subprocess.check_output(["mvn","-version"],text=True)
    assert (jetty_home/"lib/jetty-server-12.1.14.jar").is_file()
    container = volume = None
    server = None
    sampling_stop = threading.Event()
    samples = []

    def run(argv, cwd=None, env=None, data=None, log="setup.log", timeout=None):
        with (evidence/log).open("a") as output:
            subprocess.run(argv,cwd=cwd,env=env,input=data,text=True,stdout=output,
                           stderr=subprocess.STDOUT,check=True,timeout=timeout)

    def docker(*args):
        return subprocess.check_output(["docker",*args],text=True).strip()

    def psql(sql):
        run(["docker","exec","-i",container,"psql","-X","-v","ON_ERROR_STOP=1",
             "-U","postgres","-d","finance_lab"],data=sql)

    def wait_database():
        until=time.monotonic()+60
        while time.monotonic()<until:
            if subprocess.run(["docker","exec",container,"pg_isready","-h","127.0.0.1","-U","postgres","-d","finance_lab"],
                              stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:
                return
            time.sleep(.2)
        raise RuntimeError("Disposable database did not become ready")

    with tempfile.TemporaryDirectory(prefix="finance-lab-step-7-") as temporary:
        work=Path(temporary).resolve()
        app=work/"app"
        try:
            run(["git","clone","--quiet","--no-hardlinks","--local","--no-checkout",str(source),str(app)])
            run(["git","-c","advice.detachedHead=false","checkout","--quiet",REF],cwd=app)
            for name in EXPECTED:
                if "/target/" in name:
                    destination=app/name
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    shutil.copyfile(source/name,destination)
            expanded=work/"expanded"
            with zipfile.ZipFile(app/"application/target/finance-lab.war") as archive:
                archive.extractall(expanded)
            identifier=uuid.uuid4().hex
            volume="finance-lab-step7-"+identifier
            docker("volume","create","--label","org.financelab.review=step-7",volume)
            reservation=socket.socket();reservation.bind(("127.0.0.1",0))
            fixed_db_port=reservation.getsockname()[1];reservation.close()
            container=docker("run","-d","--name","finance-lab-step7-"+identifier,
                "--label","org.financelab.review=step-7","--memory=256m",
                "--mount","type=volume,source="+volume+",target=/var/lib/postgresql",
                "-e","POSTGRES_PASSWORD=synthetic-admin","-e","POSTGRES_DB=finance_lab",
                "-p","127.0.0.1:"+str(fixed_db_port)+":5432","postgres:18.6","-c","timezone=UTC")
            wait_database()
            db_port=docker("port",container,"5432/tcp").rsplit(":",1)[1]
            assert int(db_port)==fixed_db_port
            psql((app/"ops/single-vm/roles.sql").read_text()+"\n"
                 "ALTER DATABASE finance_lab OWNER TO finance_owner;\n"
                 "ALTER ROLE finance_app PASSWORD 'synthetic-runtime';\n"
                 "ALTER ROLE finance_migrate PASSWORD 'synthetic-migration';\n")
            env=dict(os.environ)
            for key in ("APP_MODE","APP_PRIVACY_POLICY_FILE","APP_SESSION_IDLE_SECONDS","APP_SESSION_ABSOLUTE_SECONDS"):
                env.pop(key,None)
            sockets=[]
            for _ in range(2):
                sock=socket.socket();sock.bind(("127.0.0.1",0));sockets.append(sock)
            http_port,tls_port=[s.getsockname()[1] for s in sockets]
            for s in sockets:s.close()
            base=work/"jetty-base";base.mkdir()
            env.update({"APP_DB_URL":"jdbc:postgresql://127.0.0.1:"+db_port+"/finance_lab",
                "APP_DB_USER":"finance_app","APP_DB_PASSWORD":"synthetic-runtime",
                "FLYWAY_URL":"jdbc:postgresql://127.0.0.1:"+db_port+"/finance_lab",
                "FLYWAY_USER":"finance_migrate","FLYWAY_PASSWORD":"synthetic-migration",
                "APP_PUBLIC_ORIGIN":"https://localhost:"+str(tls_port),
                "APP_BASE_URL":"https://localhost:"+str(tls_port)+"/",
                "APP_HTTP_PORT":str(http_port),"APP_JETTY_BASE":str(base),
                "APP_ACCOUNT_FILE":str(work/"account.properties"),
                "PLAYWRIGHT_BROWSERS_PATH":str(source/"application/.cache/playwright"),
                "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD":"1","PLAYWRIGHT_SKIP_BROWSER_GC":"1",
                "REVIEW_APP_DIR":str(app),"REVIEW_DATABASE_CONTAINER":container,
                "REVIEW_EVIDENCE_DIR":str(evidence)})
            migration_cp=str(expanded/"WEB-INF/classes")+os.pathsep+str(expanded/"WEB-INF/lib/*")
            run([java,"-cp",migration_cp,"org.financelab.app.BudgetMigration"],env=env)
            with zipfile.ZipFile(expanded/"WEB-INF/lib/budget-0.2.0.jar") as budget:
                permissions=budget.read("db/permissions/runtime.sql").decode()
            psql(permissions.replace(':"runtime_role"','"finance_app"'))
            classpath_file=evidence/"test-classpath.txt"
            run(["mvn","-B","-ntp","-f",str(app/"application/pom.xml"),
                 "org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath",
                 "-Dmdep.includeScope=test","-Dmdep.outputFile="+str(classpath_file),
                 "-Dsource.revision="+REF],env=env)
            dependency_cp=classpath_file.read_text().strip()
            for name in ("budget-0.2.0.jar","orm-0.1.0.jar"):
                files=[Path(x) for x in dependency_cp.split(os.pathsep) if Path(x).name==name]
                assert len(files)==1 and files[0].read_bytes()==(expanded/"WEB-INF/lib"/name).read_bytes(),name
            slf4j=next((jetty_home/"lib").rglob("slf4j-api-*.jar"))
            cp=os.pathsep.join([dependency_cp,str(app/"authentication/target/authentication-0.1.0.jar"),
                str(app/"authentication/target/lib/password4j-1.8.4.jar"),str(slf4j)])
            classes=work/"review-classes";classes.mkdir()
            run([javac,"-cp",cp,"-d",str(classes),str(here/"PerformanceReviewMain.java"),
                 str(app/"authentication/src/test/java/org/financelab/auth/SyntheticAccountFile.java")])
            cp=str(classes)+os.pathsep+cp
            run([java,"-cp",cp,"org.financelab.auth.SyntheticAccountFile",env["APP_ACCOUNT_FILE"]],env=env)
            keystore=work/"test.p12"
            run([str(java_home/"bin/keytool"),"-genkeypair","-alias","synthetic-test","-keyalg","RSA",
                 "-keysize","2048","-validity","1","-dname","CN=localhost",
                 "-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12",
                 "-keystore",str(keystore),"-storepass","synthetic-test-only","-noprompt"])
            run([java,"-jar",str(jetty_home/"start.jar"),"--add-modules=https"],cwd=base,env=env)
            (base/"start.d/test-tls.ini").write_text("jetty.ssl.host=127.0.0.1\njetty.ssl.port="+str(tls_port)+
                "\njetty.sslContext.keyStorePath="+str(keystore)+
                "\njetty.sslContext.keyStorePassword=synthetic-test-only\n"
                "jetty.sslContext.keyManagerPassword=synthetic-test-only\n")
            server_log=(evidence/"jetty.log").open("w")
            server=subprocess.Popen(["bash",str(app/"scripts/run.sh")],cwd=app,env=env,
                                    stdout=server_log,stderr=subprocess.STDOUT)
            env["REVIEW_JETTY_PID"]=str(server.pid)
            until=time.monotonic()+90
            while time.monotonic()<until:
                if server.poll() is not None:raise RuntimeError("Disposable Jetty exited")
                try:
                    with urllib.request.urlopen(env["APP_BASE_URL"]+"login",
                            context=ssl._create_unverified_context(),timeout=2) as response:
                        if response.status==200:break
                except Exception:time.sleep(.2)
            else:raise RuntimeError("Disposable Jetty did not become ready")
            start=time.monotonic()

            def sample():
                while not sampling_stop.is_set():
                    server.poll()  # Reap an exited child so the sibling Java probe can observe its exit.
                    pid=server.pid
                    newer=evidence/"restarted-jetty.pid"
                    if newer.exists():
                        try:pid=int(newer.read_text())
                        except ValueError:pass
                    try:
                        rss=subprocess.check_output(["ps","-p",str(pid),"-o","rss="],text=True,stderr=subprocess.DEVNULL).strip()
                        samples.append({"elapsed_s":round(time.monotonic()-start,3),"jetty_rss_kib":int(rss)})
                    except (subprocess.CalledProcessError,ValueError):pass
                    sampling_stop.wait(.5)
            sampler=threading.Thread(target=sample,daemon=True);sampler.start()
            run([java,"-Xmx384m","-cp",cp,"org.financelab.review.PerformanceReviewMain"],env=env,log="probe.log",timeout=600)
            sampling_stop.set();sampler.join(2)
            result=json.loads((evidence/"measurements.json").read_text())
            result["accepted_sha256"]=EXPECTED
            result["host"]={"os":platform.system(),"architecture":platform.machine()}
            for key in ("hw.physicalcpu","hw.logicalcpu","hw.memsize"):
                try:result["host"][key]=int(subprocess.check_output(["sysctl","-n",key],text=True).strip())
                except Exception:result["host"][key]="unavailable"
            info=json.loads(docker("info","--format","{{json .}}"))
            result["docker_host"]={k:info.get(k) for k in ("NCPU","MemTotal","OSType","Architecture")}
            result["resources"]={"jetty_max_heap_mib":256,"postgres_container_memory_mib":256,
                "jetty_rss_samples":samples,
                "jetty_rss_peak_kib":max(x["jetty_rss_kib"] for x in samples) if samples else None,
                "scope":"Jetty RSS only; excludes browser, harness, Docker VM, database, proxy and host overhead."}
            (evidence/"results.json").write_text(json.dumps(result,indent=2)+"\n")
            assert not subprocess.check_output(["git","status","--porcelain"],cwd=source,text=True).strip()
            print("Completed local performance profiles and controlled resilience probes.")
        finally:
            sampling_stop.set()
            if server is not None and server.poll() is None:
                server.terminate()
                try:server.wait(15)
                except subprocess.TimeoutExpired:server.kill();server.wait()
            # The harness owns any restarted JVM; validate its unique fixture path before fallback cleanup.
            newer=evidence/"restarted-jetty.pid"
            if newer.exists():
                try:
                    pid=int(newer.read_text())
                    command=subprocess.check_output(["ps","-p",str(pid),"-o","command="],text=True)
                    if str(app/"config/jetty-auth.xml") in command:
                        os.kill(pid,signal.SIGTERM)
                except (ValueError,ProcessLookupError,subprocess.CalledProcessError):pass
            if container:subprocess.run(["docker","rm","-f",container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            if volume:subprocess.run(["docker","volume","rm",volume],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)


if __name__=="__main__":
    main()
