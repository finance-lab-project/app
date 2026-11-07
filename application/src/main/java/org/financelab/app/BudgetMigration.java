package org.financelab.app;

import org.flywaydb.core.Flyway;

/** Administrative entry point invoked before startup with separate migration credentials. */
public final class BudgetMigration {
    private BudgetMigration() { }

    public static void main(String[] args) {
        var database = System.getenv();
        Flyway.configure().dataSource(database.get("FLYWAY_URL"), database.get("FLYWAY_USER"), database.get("FLYWAY_PASSWORD"))
                .initSql("SET ROLE finance_owner").locations("classpath:db/migration").load().migrate();
    }
}
