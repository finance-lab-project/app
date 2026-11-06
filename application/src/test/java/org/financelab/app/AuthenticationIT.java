package org.financelab.app;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationIT {
    private static final String PASSWORD = "synthetic-test-password-only";
    private static final String ROTATED = "synthetic-rotated-password-only";

    @Test void packagedSessionBoundary() throws Exception {
        String phase = System.getenv().getOrDefault("AUTH_TEST_PHASE", "boundary");
        try (var playwright = Playwright.create()) {
            var client = client(playwright);
            try {
                if (phase.equals("rotated")) {
                    var stolen = client.get("/", RequestOptions.create().setHeader("Cookie", Files.readString(Path.of("target/old-session-cookie"))));
                    assertEquals(401, stolen.status());
                    assertEquals(401, login(client, PASSWORD).status());
                    assertEquals(303, login(client, ROTATED).status());
                    assertEquals(200, client.get("/").status());
                    return;
                }
                if (phase.equals("idle") || phase.equals("absolute")) {
                    assertEquals(303, login(client, PASSWORD).status());
                    long until = System.nanoTime() + 3_000_000_000L;
                    if (phase.equals("absolute")) {
                        while (System.nanoTime() < until) { client.get("/"); Thread.sleep(200); }
                    } else Thread.sleep(3000);
                    assertEquals(401, client.get("/").status());
                    return;
                }
                for (String path : new String[]{"/", "/history", "/api/budget", "/download", "/VAADIN/build/test.js", "/?v-r=uidl", "/login;alias"}) {
                    assertEquals(401, client.get(path).status(), path);
                }
                assertEquals(403, client.get(System.getenv("APP_HTTP_BASE_URL") + "login").status());
                assertEquals(200, client.get(System.getenv("APP_HTTP_BASE_URL") + "login",
                        RequestOptions.create().setHeader("X-Forwarded-Proto", "https")).status());
                String token = token(client);
                String anonymousCookie = cookie(client);
                for (String mutation : new String[]{"/login", "/logout", "/j_security_check"}) {
                    int status = client.post(mutation, RequestOptions.create().setForm(FormData.create()
                            .set("username", "synthetic-user").set("password", PASSWORD))).status();
                    assertEquals(mutation.equals("/login") ? 403 : 401, status);
                }
                assertEquals(403, client.post("/login", credentials(token, PASSWORD)
                        .setHeader("Origin", "https://foreign.invalid")).status());
                assertEquals(401, login(client, "incorrect-synthetic-password").status());
                assertEquals(303, login(client, PASSWORD).status());
                String authenticatedCookie = cookie(client);
                assertNotEquals(anonymousCookie, authenticatedCookie);
                assertEquals(401, client.get("/", RequestOptions.create().setHeader("Cookie", anonymousCookie)).status());
                // Neither request parameters nor headers alter the server-side owner boundary.
                assertEquals(200, client.get("/?owner_id=20000000-0000-4000-8000-000000000002",
                        RequestOptions.create().setHeader("X-Owner-ID", "20000000-0000-4000-8000-000000000002")).status());
                assertEquals(403, client.post("/logout", RequestOptions.create().setForm(FormData.create().set("csrf", token))).status());
                assertEquals(405, client.get("/logout").status());
                // Keep an authenticated cookie for the stop/rotate/restart test.
                Files.createDirectories(Path.of("target"));
                Files.writeString(Path.of("target/old-session-cookie"), authenticatedCookie);
                // Repeated guesses use new sessions and varying forwarded addresses/names.
                for (int attempt = 0; attempt < 5; attempt++) {
                    var attacker = client(playwright);
                    try {
                        assertEquals(401, attacker.post("/login", RequestOptions.create().setMaxRedirects(0)
                                .setForm(FormData.create().set("csrf", token(attacker)).set("username", "unknown-" + attempt).set("password", "incorrect"))
                                .setHeader("X-Forwarded-For", "192.0.2." + (attempt + 1))).status());
                    } finally { attacker.dispose(); }
                }
                var fresh = client(playwright);
                try { assertEquals(401, login(fresh, PASSWORD).status()); }
                finally { fresh.dispose(); }
            } finally { client.dispose(); }
        }
    }

    private APIRequestContext client(Playwright playwright) {
        return playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(System.getenv("APP_BASE_URL")).setIgnoreHTTPSErrors(true));
    }

    private String token(APIRequestContext client) {
        var response = client.get("/login");
        assertEquals(200, response.status());
        assertEquals("no-store", response.headers().get("cache-control"));
        String cookie = response.headers().get("set-cookie");
        if (cookie != null) {
            assertTrue(cookie.toLowerCase().contains("secure"));
            assertTrue(cookie.toLowerCase().contains("httponly"));
            assertTrue(cookie.toLowerCase().contains("samesite=lax"));
        }
        var match = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(response.text());
        assertTrue(match.find());
        return match.group(1);
    }

    private com.microsoft.playwright.APIResponse login(APIRequestContext client, String password) {
        return client.post("/login", credentials(token(client), password));
    }

    private RequestOptions credentials(String token, String password) {
        return RequestOptions.create().setMaxRedirects(0).setForm(FormData.create()
                .set("csrf", token).set("username", "synthetic-user").set("password", password));
    }

    private String cookie(APIRequestContext client) {
        // The cookie is synthetic and stays in ignored test output.
        String state = client.storageState();
        var match = Pattern.compile("\"name\"\\s*:\\s*\"JSESSIONID\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"").matcher(state);
        assertTrue(match.find(), "Session cookie present");
        return "JSESSIONID=" + match.group(1);
    }
}
