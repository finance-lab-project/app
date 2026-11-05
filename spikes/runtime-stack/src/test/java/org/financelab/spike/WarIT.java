package org.financelab.spike;

import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipFile;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WarIT {
    @Test
    void productionWarSupportsBrowserServerRoundTrip() throws Exception {
        try (var war = new ZipFile(Path.of("target/runtime-stack.war").toFile())) {
            var entries = war.stream().map(entry -> entry.getName()).toList();
            assertTrue(entries.stream().anyMatch(name -> name.contains("VAADIN/build/") && name.endsWith(".js")));
            assertFalse(entries.stream().anyMatch(name -> name.matches("WEB-INF/lib/(spring-.*|jetty-.*|jakarta.servlet-api-.*|postgresql-.*|junit-.*|playwright-.*)\\.jar")));
            var config = war.getEntry("WEB-INF/classes/META-INF/VAADIN/config/flow-build-info.json");
            assertNotNull(config);
            try (var input = war.getInputStream(config)) {
                assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .matches("(?s).*\"productionMode\"\\s*:\\s*true.*"));
            }
        }
        var errors = new ArrayList<String>();
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch()) {
            var page = browser.newPage();
            page.onPageError(errors::add);
            page.onResponse(response -> {
                if (response.status() >= 400) errors.add("HTTP " + response.status() + " " + response.url());
            });
            assertEquals(200, page.navigate(System.getenv("SPIKE_BASE_URL")).status());
            assertThat(page.locator("h1")).hasText("Runtime compatibility spike");
            assertThat(page.locator("#result")).hasText("Ready");
            page.getByText("Round trip", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("#result")).hasText("Server round trip OK");
            page.reload();
            assertThat(page.locator("h1")).hasText("Runtime compatibility spike");
            assertTrue(errors.isEmpty(), errors.toString());
            System.out.println("WAR OK: production assets; no Spring/container/test jars; Chromium " + browser.version()
                    + "; route, server event and reload passed; no browser errors");
        }
    }
}
