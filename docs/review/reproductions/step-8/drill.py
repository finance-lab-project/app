#!/usr/bin/env python3
"""Run only in the disposable container created by run.py."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

OPS=Path("/ops")
WORK=Path("/recovery-review")
RESULTS=Path("/evidence/extended-drill.json")
ENV=dict(os.environ,PGHOST="/var/run/postgresql",PGDATABASE="finance_lab",DB_OWNER="finance_owner",
         AWS_ACCESS_KEY_ID="synthetic",AWS_SECRET_ACCESS_KEY="synthetic",AWS_EC2_METADATA_DISABLED="true",
         AWS_DEFAULT_REGION="ru-central1",BACKUP_BUCKET="synthetic-step8",S3_ENDPOINT="http://127.0.0.1:5000",
         RUNTIME_DIRECTORY=str(WORK/"receipt"),AGE_RECIPIENT_FILE="/drill/recipient",
         AGE_IDENTITY_FILE="/drill/recovery/key",REPORT_TO="operator@example.invalid",MSMTP_CONFIG="/drill/msmtprc")
ADMIN=["runuser","-u","postgres","--"]
BACKUP=["runuser","-u","finance_backup","--","env","PGUSER=finance_backup"]
running=None

def command(args, data=None, expected=0, env=None):
    result=subprocess.run(args,input=data,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                          env=env or ENV,timeout=90)
    if expected is not None and result.returncode!=expected:
        raise RuntimeError("Fixture command failed: "+str(args[0])+"; "+result.stderr[:700])
    return result

def sql(text, db="finance_lab"):
    return command(ADMIN+["psql","-X","-qAt","-v","ON_ERROR_STOP=1","-d",db],text).stdout.strip()

def start(name, roles):
    global running
    directory=WORK/name
    command(["install","-d","-m","0700","-o","postgres",str(directory)])
    command(ADMIN+["initdb","-D",str(directory),"--no-locale","--encoding=UTF8",
                   "--auth-local=peer","--auth-host=scram-sha-256"])
    with (directory/"postgresql.conf").open("a") as f:f.write((OPS/"postgresql.conf").read_text())
    (directory/"pg_hba.conf").write_text((OPS/"pg_hba.conf").read_text())
    command(ADMIN+["pg_ctl","-D",str(directory),"-l",str(directory/"server.log"),"-w","start"])
    running=directory
    assert sql("SHOW server_version_num","postgres")=="180006"
    if roles:sql((OPS/"roles.sql").read_text(),"postgres")

def stop():
    global running
    if running is not None:
        command(ADMIN+["pg_ctl","-D",str(running),"-m","fast","-w","stop"])
        running=None

def snapshot():
    tables=sql("SELECT tablename FROM pg_tables WHERE schemaname='budget' ORDER BY tablename").splitlines()
    assert len(tables)==24
    result=[]
    for table in tables:
        assert re.fullmatch("[a-z_]+",table)
        rows=sql('SELECT row_to_json(t)::text FROM budget."'+table+'" t ORDER BY row_to_json(t)::text')
        count=int(sql('SELECT count(*) FROM budget."'+table+'"'))
        result.append({"table":table,"rows":count,"sha256":hashlib.sha256(rows.encode()).hexdigest()})
    objects=sql("""SELECT c.relname, c.relkind, pg_get_userbyid(c.relowner),
        coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='budget' ORDER BY c.relname""")
    schema=sql("SELECT nspname,pg_get_userbyid(nspowner),coalesce(nspacl::text,'') FROM pg_namespace WHERE nspname='budget'")
    return {"tables":result,"owners_and_acl_sha256":hashlib.sha256((schema+"\n"+objects).encode()).hexdigest()}

def projection():
    return sql("""SET ROLE finance_app;
        SELECT r.version,n.name,k.kind_code,s.state_code,coalesce(a.amount::text,'unset')
        FROM budget.budget_revision r
        JOIN budget.catalog_revision cr ON cr.owner_id=r.owner_id AND cr.version=r.catalog_version
        CROSS JOIN LATERAL (
            SELECT name FROM budget.category_name WHERE changed_at<=cr.changed_at ORDER BY changed_at DESC LIMIT 1
        ) n
        JOIN budget.line_key k ON k.budget_id=r.budget_id
        JOIN LATERAL (
            SELECT changed_at,state_code FROM budget.line_amount_state
            WHERE line_id=k.line_id AND changed_at<=r.changed_at ORDER BY changed_at DESC LIMIT 1
        ) s ON true
        LEFT JOIN budget.line_amount a ON a.line_id=k.line_id AND a.changed_at=s.changed_at
        ORDER BY r.version,k.kind_code;
        RESET ROLE;""")

def main():
    assert Path("/.dockerenv").is_file() and not WORK.exists()
    subprocess.run(["bash",str(OPS/"checks/recovery.sh")],env=ENV,check=True,timeout=180)
    WORK.mkdir(mode=0o755)
    command(["install","-d","-m","0700","-o","finance_backup",str(WORK/"receipt")])
    s3=["aws","--endpoint-url",ENV["S3_ENDPOINT"],"s3api"]
    command(s3+["create-bucket","--bucket",ENV["BACKUP_BUCKET"],
                "--create-bucket-configuration","LocationConstraint=ru-central1"])
    versions={}
    for name,args in {
        "postgresql":["psql","--version"],"age":["age","--version"],"awscli":["aws","--version"],
        "bash":["bash","--version"],"caddy":["caddy","version"],
        "systemd":["systemd-analyze","--version"],"shellcheck":["shellcheck","--version"]
    }.items():
        result=command(args)
        versions[name]=(result.stdout or result.stderr).strip().splitlines()[:3]
    started=time.monotonic()
    try:
        start("source",True)
        command(ADMIN+["createdb","--template=template0","--owner=finance_owner","finance_lab"])
        sql("SET ROLE finance_owner;\n"+Path("/inputs/migration.sql").read_text())
        sql(Path("/inputs/runtime.sql").read_text().replace(':"runtime_role"','"finance_app"'))
        sql(Path("/review/seed.sql").read_text())
        before=snapshot();before_projection=projection()
        expected="\n".join([
            "1|Synthetic original|INCOME|UNSET|unset",
            "1|Synthetic original|INVESTMENT|SET|0.00",
            "1|Synthetic original|SPENDING|SET|0.00",
            "2|Synthetic renamed|INCOME|SET|123.45",
            "2|Synthetic renamed|INVESTMENT|UNSET|unset",
            "2|Synthetic renamed|SPENDING|SET|12.34"])
        assert before_projection==expected,before_projection
        assert command(BACKUP+["test","-r",ENV["AGE_IDENTITY_FILE"]],expected=None).returncode!=0
        command(BACKUP+["bash",str(OPS/"backup"),"pre-change"])
        receipt=Path(ENV["RUNTIME_DIRECTORY"],"verified").read_text()
        key=re.search(r"^Object key: (database/pre-change/[^\n]+)$",receipt,re.M).group(1)
        digest=re.search(r"^SHA-256: ([0-9a-f]{64})$",receipt,re.M).group(1)
        command(BACKUP+["env","SERVICE_RESULT=success","bash",str(OPS/"report-backup"),"pre-change"])
        # A provider/relay failure must remain a failure even with a verified receipt.
        dead_mail=WORK/"dead-msmtprc"
        dead_mail.write_text("account default\nhost 127.0.0.1\nport 8026\nfrom backup@example.invalid\n")
        dead_mail.chmod(0o644)
        mail=command(BACKUP+["env","SERVICE_RESULT=success","MSMTP_CONFIG="+str(dead_mail),
                             "bash",str(OPS/"report-backup"),"pre-change"],expected=None)
        assert mail.returncode!=0
        stop()
        recovery_start=time.monotonic()
        destination=WORK/"fetched"
        command(["bash",str(OPS/"fetch-backup"),key,digest,str(destination)])
        assert (destination/"database.dump").is_file()
        assert not (destination/"ciphertext").exists() and not (destination/"partial.dump").exists()
        start("replacement",False)
        archive=(destination/"database.dump").read_bytes()
        def restore(owner):
            return subprocess.run(ADMIN+["env","PGUSER=postgres","DB_OWNER="+owner,
                "bash",str(OPS/"restore"),"finance_lab"],input=archive,
                stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=ENV,timeout=90)
        missing=restore("postgres")
        assert missing.returncode!=0 and b"finance_owner" in missing.stderr
        assert sql("SELECT count(*) FROM pg_database WHERE datname='finance_lab'","postgres")=="1"
        assert sql("SELECT count(*) FROM pg_namespace WHERE nspname='budget'")=="0"
        existing=restore("postgres")
        assert existing.returncode!=0 and b"already exists" in existing.stderr
        # Only this owned, empty fixture target is dropped to follow the fresh-target retry procedure.
        command(ADMIN+["dropdb","finance_lab"])
        sql((OPS/"roles.sql").read_text(),"postgres")
        restored=restore("finance_owner")
        assert restored.returncode==0,restored.stderr.decode()[:500]
        after=snapshot();assert after==before
        assert projection()==expected
        result={"completed":True,"versions":versions,"checked_in_recovery_drill":"exit 0",
            "schema_source":"Accepted WAR nested Budget JAR V1 SQL and runtime grants",
            "seeding":"Direct SQL fixture; Flyway and Java/browser application not executed",
            "table_data_and_owner_grants_equal":True,"table_snapshots":before,
            "runtime_role_projection":expected.splitlines(),
            "backup_role_cannot_read_age_identity":True,
            "verified_pre_change_ciphertext_receipt":True,
            "failed_mail_submission_exit_code":mail.returncode,
            "missing_owner_restore_exit_code":missing.returncode,
            "failed_import_left_new_empty_database":True,
            "existing_empty_target_refused":True,
            "fetch_removed_ciphertext_and_partial":True,
            "fetch_new_cluster_and_validation_s":round(time.monotonic()-recovery_start,3),
            "extended_phase_s":round(time.monotonic()-started,3),
            "scope":"Local synthetic recovery pipeline, not service-manager/provider/end-to-end recovery time"}
        RESULTS.write_text(json.dumps(result,indent=2)+"\n")
        print("PASS: accepted Budget schema encrypted recovery, 24 table fingerprints, owners/grants, projections, missing-role rollback and mail-failure rejection.",flush=True)
    finally:stop()

if __name__=="__main__":main()
