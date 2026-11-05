package org.financelab.app;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public final class ApplicationLifecycle implements ServletContextListener {
    static final String CONFIG_ATTRIBUTE = ApplicationConfig.class.getName();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        var configuration = ApplicationConfig.from(System.getenv());
        event.getServletContext().setAttribute(CONFIG_ATTRIBUTE, configuration);
        event.getServletContext().log("Finance Lab bootstrap started");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        event.getServletContext().removeAttribute(CONFIG_ATTRIBUTE);
        event.getServletContext().log("Finance Lab bootstrap stopped");
    }
}
