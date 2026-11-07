package org.financelab.app;

import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Browser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.zip.ZipFile;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WarIT {
    @Test
    void standaloneProductionWarHasProvenanceAndHandlesServerEvents() throws Exception {
        try (var war = new ZipFile(Path.of("target/finance-lab.war").toFile())) {
            var entries = war.stream().map(entry -> entry.getName()).toList();
            assertTrue(entries.stream().anyMatch(name -> name.contains("VAADIN/build/") && name.endsWith(".js")));
            assertTrue(entries.contains("WEB-INF/lib/postgresql-42.7.14.jar"));
            assertFalse(entries.contains("ui/local.js"), "The planner is served from the Budget JAR");
            assertFalse(entries.stream().anyMatch(name -> name.matches(
                    "WEB-INF/lib/(spring-.*|jetty-.*|authentication-.*|password4j-.*|jakarta.servlet-api-.*|junit-.*|playwright-.*|portfolio-.*|fire-.*|camunda-.*|artemis-.*)\\.jar")));
            for (String[] library : new String[][]{
                    {"budget-0.2.0.jar", "43d86d28965a241cd45f7938c1adfb2ec50f8132"},
                    {"orm-0.1.0.jar", "8daaf91d07d2e30b563322b22a0c6da7545578e5"}}) {
                var entry = war.getEntry("WEB-INF/lib/" + library[0]);
                assertNotNull(entry);
                try (var jar = new java.util.jar.JarInputStream(war.getInputStream(entry))) {
                    assertEquals(library[1], jar.getManifest().getMainAttributes().getValue("Build-Source-Revision"));
                }
            }
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
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch();
             var context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
            var page = context.newPage();
            assertEquals(200, page.navigate(System.getenv("APP_BASE_URL") + "login").status());
            page.locator("input[name=username]").fill("synthetic-user");
            page.locator("input[name=password]").fill("synthetic-test-password-only");
            page.getByText("Войти", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("h1")).hasText("Бюджет на месяц");
            page.onPageError(errors::add);
            page.onResponse(response -> {
                if (response.status() >= 400) errors.add("HTTP " + response.status());
            });
            assertEquals(200, page.navigate(System.getenv("APP_BASE_URL")).status());
            assertThat(page.locator("h1")).hasText("Бюджет на месяц");
            var uidl = new AtomicReference<com.microsoft.playwright.Request>();
            page.onRequest(request -> {
                if (request.method().equals("POST") && request.url().contains("v-r=uidl")) uidl.set(request);
            });
            page.getByText("Категории", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("#categories input").first()).isVisible();
            assertNotNull(uidl.get());
            String original = uidl.get().postData();
            String tampered = original.replaceFirst("\"csrfToken\"\\s*:\\s*\"[^\"]+\"", "\"csrfToken\":\"invalid\"");
            assertNotEquals(original, tampered);
            var rejected = context.request().post(uidl.get().url(), com.microsoft.playwright.options.RequestOptions.create()
                    .setHeader("Content-Type", "application/json").setData(tampered));
            assertTrue(rejected.text().contains("\"appError\""), "Vaadin rejects a forged UIDL CSRF token");
            page.reload();
            assertThat(page.locator("h1")).hasText("Бюджет на месяц");
            assertTrue(errors.isEmpty(), errors.toString());
            String oldCookie = context.cookies().stream().filter(cookie -> cookie.name.equals("JSESSIONID"))
                    .map(cookie -> "JSESSIONID=" + cookie.value).findFirst().orElseThrow();
            page.getByText("Выйти", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("h1")).hasText("Бюджет на месяц");
            assertEquals(401, context.request().get(System.getenv("APP_BASE_URL") + "transfer").status());
            assertEquals(401, context.request().get(System.getenv("APP_BASE_URL") + "transfer",
                    com.microsoft.playwright.options.RequestOptions.create().setHeader("Cookie", oldCookie)).status());
        }
        System.out.println("Application WAR OK: production assets, provenance, HTTPS browser login, server event, reload and logout");
    }
}
