package org.financelab.app;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpServletRequest;
import org.financelab.budget.adapter.postgres.PostgresBudgetStore;
import org.financelab.budget.application.BudgetService;
import org.financelab.budget.application.SaveResult;
import org.financelab.orm.JdbcTransactions;

public final class ApplicationLifecycle implements ServletContextListener {
    static final String CONFIG_ATTRIBUTE = ApplicationConfig.class.getName();
    private HikariDataSource pool;

    @Override public void contextInitialized(ServletContextEvent event) {
        var configuration = ApplicationConfig.from(System.getenv());
        ServletContext context = event.getServletContext();
        context.setAttribute(CONFIG_ATTRIBUTE, configuration);
        String policyFile = System.getenv("APP_PRIVACY_POLICY_FILE");
        try {
            String policy = policyFile == null ? "" : java.nio.file.Files.readString(java.nio.file.Path.of(policyFile));
            context.setAttribute("privacyPolicy", policy.isBlank() ? "" : "<h2>Политика оператора</h2><div class=\"policy\">"
                    + AppPages.escape(policy) + "</div>");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read configured privacy policy");
        }
        context.setSessionTimeout((configuration.idleSeconds() + 59) / 60);
        context.getSessionCookieConfig().setAttribute("SameSite", "Lax");
        var database = new HikariConfig();
        database.setJdbcUrl(required("APP_DB_URL"));
        database.setUsername(required("APP_DB_USER"));
        database.setPassword(required("APP_DB_PASSWORD"));
        database.setMaximumPoolSize(4);
        database.setConnectionTimeout(5000);
        pool = new HikariDataSource(database);
        try {
            var store = new PostgresBudgetStore(new JdbcTransactions(pool), e -> context.log("Budget database operation failed"));
            store.verifyMappings();
            context.setAttribute(PostgresBudgetStore.class.getName(), store);
        } catch (RuntimeException e) {
            pool.close();
            throw e;
        }
        context.log("Finance Lab started");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    static BudgetService service(HttpServletRequest request, java.util.function.Supplier<java.util.UUID> identity) {
        var store = (PostgresBudgetStore) request.getServletContext().getAttribute(PostgresBudgetStore.class.getName());
        var service = new BudgetService(store, identity);
        var result = store.initializeCatalog(identity.get());
        if (!(result instanceof SaveResult.Saved<?> || result instanceof SaveResult.Unchanged<?>)) {
            throw new IllegalStateException("Cannot open Budget catalog");
        }
        return service;
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        if (pool != null) pool.close();
        event.getServletContext().log("Finance Lab stopped");
    }
}
