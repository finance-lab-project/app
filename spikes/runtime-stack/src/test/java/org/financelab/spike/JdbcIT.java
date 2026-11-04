package org.financelab.spike;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class JdbcIT {
    @Test
    void commitRollbackAndReadAcrossConnections() throws Exception {
        try (Connection writer = connect(); Connection reader = connect()) {
            assertEquals("18.6", writer.getMetaData().getDatabaseProductVersion().split(" ")[0]);
            assertEquals("42.7.14", writer.getMetaData().getDriverVersion());
            try (var statement = writer.createStatement()) {
                statement.execute("CREATE TABLE runtime_probe (id integer PRIMARY KEY, amount numeric(12,2) NOT NULL)");
            }
            try {
                writer.setAutoCommit(false);
                try (var insert = writer.prepareStatement("INSERT INTO runtime_probe VALUES (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setBigDecimal(2, new BigDecimal("123.45"));
                    insert.executeUpdate();
                    assertEquals(0, count(reader));
                    writer.commit();
                    assertEquals(1, count(reader));

                    insert.setInt(1, 2);
                    insert.setBigDecimal(2, new BigDecimal("999.99"));
                    insert.executeUpdate();
                    SQLException duplicate = assertThrows(SQLException.class, insert::executeUpdate);
                    assertEquals("23505", duplicate.getSQLState());
                    writer.rollback();
                    assertEquals(1, count(reader));
                }
                try (Connection fresh = connect(); var statement = fresh.createStatement();
                     var result = statement.executeQuery("SELECT id, amount FROM runtime_probe")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt("id"));
                    assertEquals(new BigDecimal("123.45"), result.getBigDecimal("amount"));
                    assertFalse(result.next());
                }
                System.out.println("JDBC OK: PostgreSQL 18.6 / pgJDBC 42.7.14; uncommitted row invisible; commit visible; failed transaction rolled back; fresh read = 123.45");
            } finally {
                writer.rollback();
                writer.setAutoCommit(true);
                try (var statement = writer.createStatement()) {
                    statement.execute("DROP TABLE runtime_probe");
                }
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("SPIKE_JDBC_URL"), "spike", "");
    }

    private static int count(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM runtime_probe")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
