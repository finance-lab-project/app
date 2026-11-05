package org.financelab.app;

import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.zip.ZipFile;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WarIT {
    @Test
    void standaloneProductionWarHasProvenanceAndHandlesServerEvents() throws Exception {
        try (var war = new ZipFile(Path.of("target/finance-lab.war").toFile())) {
            var entries = war.stream().map(entry -> entry.getName()).toList();
            assertTrue(entries.stream().anyMatch(name -> name.contains("VAADIN/build/") && name.endsWith(".js")));
            assertTrue(entries.contains("WEB-INF/lib/postgresql-42.7.14.jar"));
            assertFalse(entries.stream().anyMatch(name -> name.matches(
                    "WEB-INF/lib/(spring-.*|jetty-.*|jakarta.servlet-api-.*|junit-.*|playwright-.*|portfolio-.*|fire-.*|camunda-.*|artemis-.*|flyway-.*)\\.jar")));
            var config = war.getEntry("WEB-INF/classes/META-INF/VAADIN/config/flow-build-info.json");
            assertNotNull(config);
            try (var input = war.getInputStream(config)) {
                assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .matches("(?s).*\"productionMode\"\\s*:\\s*true.*"));
            }
            var provenance = new Properties();
            try (var input = war.getInputStream(war.getEntry("WEB-INF/classes/META-INF/finance-lab/build.properties"))) {
                provenance.load(input);
            }
            assertEquals(System.getenv("BUILD_SOURCE_REVISION"), provenance.getProperty("source.revision"));
            assertEquals("https://github.com/finance-lab-project/app", provenance.getProperty("source.repository"));
            assertEquals("0.1.0", provenance.getProperty("version"));
        }
        var errors = new ArrayList<String>();
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch()) {
            var page = browser.newPage();
            page.onPageError(errors::add);
            page.onResponse(response -> {
                if (response.status() >= 400) errors.add("HTTP " + response.status());
            });
            assertEquals(200, page.navigate(System.getenv("APP_BASE_URL")).status());
            assertThat(page.locator("h1")).hasText("Finance Lab");
            assertThat(page.locator("#status")).hasText("Готово");
            page.getByText("Проверить соединение").click();
            assertThat(page.locator("#status")).hasText("Соединение работает");
            page.reload();
            assertThat(page.locator("h1")).hasText("Finance Lab");
            assertTrue(errors.isEmpty(), errors.toString());
        }
        System.out.println("Application WAR OK: production assets, JDBC, source provenance, browser/server event and reload");
    }
}
