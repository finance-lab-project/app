-- Apply once as the cluster administrator on a new dedicated cluster.
CREATE ROLE finance_owner NOLOGIN;
CREATE ROLE finance_app LOGIN;
CREATE ROLE finance_backup LOGIN;
GRANT pg_read_all_data TO finance_backup;
