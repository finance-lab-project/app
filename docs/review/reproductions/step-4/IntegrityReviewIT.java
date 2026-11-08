package org.financelab.budget.adapter.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.financelab.budget.application.BudgetStore.*;
import org.financelab.budget.application.SaveResult;
import org.financelab.budget.domain.BudgetLine;
import org.financelab.orm.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Review-only characterization. A passing malformed-category case reproduces S4-01. */
class IntegrityReviewIT {
    static final String ROLE = "integrity_runtime";
    static final YearMonth MONTH = YearMonth.of(2026, 11);
    static String url, admin;
    static HikariDataSource pool;
    static Transactions tx;
    static PostgresBudgetStore store;

    @BeforeAll static void setup() throws Exception {
        url = System.getenv("BUDGET_TEST_URL");
        admin = System.getenv("BUDGET_TEST_USER");
        assertNotNull(url);
        assertNotNull(admin);
        var flyway = Flyway.configure().dataSource(url, admin, "")
                .locations("classpath:db/migration").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var c = DriverManager.getConnection(url, admin, ""); var s = c.createStatement()) {
            s.execute("CREATE ROLE " + ROLE + " LOGIN");
            s.execute(Files.readString(Path.of(System.getenv("BUDGET_RUNTIME_SQL")))
                    .replace(":\"runtime_role\"", "\"" + ROLE + "\""));
            s.execute("CREATE SCHEMA review_integrity");
            s.execute("CREATE TABLE review_integrity.commit_probe (id uuid PRIMARY KEY)");
            s.execute("GRANT USAGE ON SCHEMA review_integrity TO " + ROLE);
            s.execute("GRANT SELECT, INSERT ON review_integrity.commit_probe TO " + ROLE);
        }
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(ROLE);
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(5000);
        config.addDataSourceProperty("socketTimeout", "15");
        pool = new HikariDataSource(config);
        tx = new JdbcTransactions(pool);
        store = new PostgresBudgetStore(tx, failure -> fail("Unexpected store failure", failure));
        store.verifyMappings();
        assertTrue(scalar("SELECT current_setting('server_version')", Codecs.TEXT).startsWith("18.6"));
    }

    @AfterEach void noLeases() { assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections()); }
    @AfterAll static void close() { if (pool != null) pool.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"bare", "futureIncomplete", "differentInitialTimes"})
    void malformedInitialCategoryIsAcceptedOrHidden(String mode) {
        UUID owner = owner();
        var month = saveMonth(owner);
        Instant at = store.catalog(owner).orElseThrow().revision().changedAt();
        UUID category = UUID.randomUUID();
        tx.write(s -> {
            s.insertAnchor(BudgetMappings.CATEGORY, category);
            s.insertStatic(BudgetMappings.OWNER, category, owner);
            if (mode.equals("futureIncomplete")) {
                s.append(BudgetMappings.NAME, category, at.plusSeconds(86400), "Synthetic future");
                s.append(BudgetMappings.POSITION, category, at.plusSeconds(86400), 11);
            } else if (mode.equals("differentInitialTimes")) {
                s.append(BudgetMappings.NAME, category, at.minusNanos(2000), "Synthetic misaligned");
                s.append(BudgetMappings.POSITION, category, at.minusNanos(1000), 11);
                s.append(BudgetMappings.STATUS, category, at, "ACTIVE");
            }
            return null;
        });
        int expected = mode.equals("differentInitialTimes") ? 11 : 10;
        assertEquals(expected, store.catalog(owner).orElseThrow().catalog().categories().size());
        assertEquals(expected, store.current(owner, MONTH).catalog().catalog().categories().size());
        assertEquals(expected, store.revision(owner, month.budgetId(), month.version())
                .orElseThrow().catalog().catalog().categories().size());
        assertEquals(1, store.revisions(owner, month.budgetId()).size());
        System.out.printf("STEP4 {\"case\":\"%s\",\"finding\":\"S4-01\",\"committedFixture\":true,"
                + "\"currentCategories\":%d,\"historicalCategories\":%d,\"integrityError\":false}%n",
                mode, expected, expected);
    }

    @Test void visibleMissingStatusIsRejected() {
        UUID owner = owner();
        var month = saveMonth(owner);
        Instant at = store.catalog(owner).orElseThrow().revision().changedAt();
        tx.write(s -> {
            UUID category = UUID.randomUUID();
            s.insertAnchor(BudgetMappings.CATEGORY, category);
            s.insertStatic(BudgetMappings.OWNER, category, owner);
            s.append(BudgetMappings.NAME, category, at, "Synthetic missing status");
            s.append(BudgetMappings.POSITION, category, at, 11);
            return null;
        });
        var reported = new java.util.ArrayList<Throwable>();
        var rejecting = new PostgresBudgetStore(tx, reported::add);
        assertThrows(IllegalStateException.class, () -> rejecting.catalog(owner));
        assertThrows(IllegalStateException.class, () -> rejecting.current(owner, MONTH));
        assertThrows(IllegalStateException.class, () -> rejecting.revision(owner, month.budgetId(), 1));
        assertEquals(3, reported.size());
        reported.forEach(f -> assertInstanceOf(PersistenceFailure.class, f));
        System.out.println("STEP4 {\"case\":\"visibleMissingStatus\",\"integrityError\":true}");
    }

    @Test void legitimateLaterCategoryRemainsAbsentFromPinnedHistory() {
        UUID owner = owner();
        var month = saveMonth(owner);
        var first = store.catalog(owner).orElseThrow();
        assertInstanceOf(SaveResult.Saved.class,
                store.saveCatalog(owner, first.revision().version(), first.catalog().add("Synthetic later")));
        assertEquals(11, store.catalog(owner).orElseThrow().catalog().categories().size());
        assertEquals(10, store.revision(owner, month.budgetId(), 1).orElseThrow()
                .catalog().catalog().categories().size());
        System.out.println("STEP4 {\"case\":\"legitimateLaterCategory\",\"currentCategories\":11,"
                + "\"historicalCategories\":10}");
    }

    @Test void productionRuntimeGrantsDenyFactUpdateAndDelete() {
        UUID owner = owner();
        UUID category = store.catalog(owner).orElseThrow().catalog().categories().getFirst().id();
        for (String sql : List.of("UPDATE budget.category_name SET name = name WHERE category_id = ?",
                "DELETE FROM budget.category_name WHERE category_id = ?")) {
            var failure = assertThrows(PersistenceFailure.class, () -> tx.write(s ->
                    s.execute(new Sql(sql), List.of(Param.of(Codecs.UUID, category)))));
            assertEquals(WriteOutcome.NOT_COMMITTED, failure.outcome());
            assertEquals("42501", failure.sqlState().orElseThrow());
        }
        assertEquals(10, store.catalog(owner).orElseThrow().catalog().categories().size());
        System.out.println("STEP4 {\"case\":\"runtimeFactPermissions\",\"updateSqlState\":\"42501\","
                + "\"deleteSqlState\":\"42501\",\"outcome\":\"NOT_COMMITTED\"}");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminatedBackendIsClassifiedAndPoolRecovers(boolean failBeforeCommit) {
        UUID id = UUID.randomUUID();
        var calls = new AtomicInteger();
        var failure = assertThrows(PersistenceFailure.class, () -> tx.write(s -> {
            calls.incrementAndGet();
            s.execute(new Sql("INSERT INTO review_integrity.commit_probe (id) VALUES (?)"),
                    List.of(Param.of(Codecs.UUID, id)));
            int pid = s.query(new Sql("SELECT pg_backend_pid()"), List.of(),
                    r -> r.get(1, Codecs.INTEGER).orElseThrow()).getFirst();
            terminate(pid);
            if (failBeforeCommit) s.query(new Sql("SELECT 1"), List.of(),
                    r -> r.get(1, Codecs.INTEGER).orElseThrow());
            return null;
        }));
        assertEquals(failBeforeCommit ? WriteOutcome.NOT_COMMITTED : WriteOutcome.UNKNOWN, failure.outcome());
        assertEquals(1, calls.get());
        assertEquals(0, scalar("SELECT count(*) FROM review_integrity.commit_probe", Codecs.BIGINT));
        assertEquals(1, scalar("SELECT 1", Codecs.INTEGER));
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
        System.out.printf("STEP4 {\"case\":\"%s\",\"outcome\":\"%s\",\"sqlState\":\"%s\","
                + "\"persistedProbeRows\":0,\"callbackCalls\":1,\"poolRecovered\":true}%n",
                failBeforeCommit ? "terminateBeforeNextStatement" : "terminateBeforeCommit",
                failure.outcome(), failure.sqlState().orElse("none"));
    }

    static void terminate(int pid) throws Exception {
        try (var c = DriverManager.getConnection(url, admin, "");
             var s = c.prepareStatement("SELECT pg_terminate_backend(?, 5000)")) {
            s.setInt(1, pid);
            try (var rs = s.executeQuery()) { assertTrue(rs.next()); assertTrue(rs.getBoolean(1)); }
        }
    }

    static UUID owner() {
        UUID owner = UUID.randomUUID();
        assertInstanceOf(SaveResult.Saved.class, store.initializeCatalog(owner));
        return owner;
    }

    static BudgetRevision saveMonth(UUID owner) {
        var v = store.current(owner, MONTH);
        var b = v.budget();
        var command = new SaveBudget(b.month(), v.version(), v.catalog().revision().version(), b.income().amount(),
                b.lines().stream().filter(l -> l.kind() == BudgetLine.Kind.SPENDING)
                        .map(l -> new SpendingValue(l.categoryId().orElseThrow(), l.amount())).toList(),
                b.investment().amount());
        var result = assertInstanceOf(SaveResult.Saved.class, store.save(owner, command));
        return (BudgetRevision) result.revision();
    }

    static <T> T scalar(String sql, Codec<T> codec) {
        return tx.snapshot(s -> s.query(new Sql(sql), List.of(), r -> r.get(1, codec).orElseThrow())
                .getFirst()).value();
    }
}
