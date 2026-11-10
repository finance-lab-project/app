package org.financelab.app;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.financelab.budget.adapter.postgres.PostgresBudgetStore;
import org.financelab.budget.application.BudgetStore.*;
import org.financelab.budget.application.SaveResult;
import org.financelab.budget.domain.BudgetAmount;
import org.financelab.budget.domain.BudgetLine;
import org.financelab.orm.JdbcTransactions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Review-only probes against the frozen packaged WAR; no application instrumentation. */
class SecurityReviewIT {
    static final String URL = Objects.requireNonNull(System.getenv("APP_BASE_URL"));
    static final String ORIGIN = Objects.requireNonNull(System.getenv("APP_PUBLIC_ORIGIN"));
    static final UUID OWNER = UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final YearMonth MONTH = YearMonth.of(2052, 4);

    @Test void anonymousAliasesAssetsAndForgedIdentityRemainClosed() {
        try (var pw = Playwright.create()) {
            var api = api(pw);
            try {
                for (String path : List.of("/transfer", "/ui/transfer.js", "/history", "/api/budget",
                        "/download", "/?v-r=uidl", "/login;probe", "/local;probe",
                        "/privacy;probe", "/lo%63al", "/ui/local.js;probe",
                        "/WEB-INF/web.xml", "/META-INF/MANIFEST.MF")) {
                    var response = api.get(path, RequestOptions.create().setMaxRedirects(0)
                            .setHeader("X-Owner-ID", OWNER.toString()).setHeader("X-Remote-User", OWNER.toString()));
                    assertTrue(Set.of(400, 401, 403, 404).contains(response.status()), path + ": " + response.status());
                    assertTrue(Arrays.stream(response.headers().getOrDefault("cache-control", "").split(","))
                            .map(String::trim).anyMatch("no-store"::equalsIgnoreCase), path);
                }
                for (String path : List.of("/local", "/privacy", "/ui/local.js", "/ui/store.js")) {
                    assertEquals(200, api.get(path).status(), path);
                }
                for (String path : List.of("/local", "/privacy", "/ui/local.js")) {
                    assertEquals(401, api.post(path).status(), path);
                }
                System.out.println("STEP5 {\"case\":\"anonymousBoundary\",\"closedPaths\":13,"
                        + "\"forgedIdentityRejected\":true,\"publicReads\":4,\"publicWritesRejected\":3}");
            } finally { api.dispose(); }
        }
    }

    @Test void nativeCsrfTokensAreSessionBoundAndOriginsAndRequestShapeAreEnforced() {
        try (var pw = Playwright.create()) {
            var first = api(pw);
            var second = api(pw);
            try {
                String a = token(first), b = token(second);
                assertNotEquals(a, b);
                assertEquals(403, second.post("/login", credentials(a)).status());
                for (String origin : List.of("https://foreign.invalid", "null")) {
                    assertEquals(403, second.post("/login", credentials(b).setHeader("Origin", origin)).status());
                }
                String before = cookie(second);
                assertEquals(303, second.post("/login", credentials(b).setHeader("Origin", ORIGIN)).status());
                assertNotEquals(before, cookie(second));
                String current = transferToken(second);
                assertNotEquals(b, current);
                assertEquals(403, second.post("/transfer", RequestOptions.create().setHeader("Origin", ORIGIN)
                        .setHeader("X-CSRF-Token", b)).status());
                assertEquals(403, second.post("/transfer", RequestOptions.create()
                        .setHeader("X-CSRF-Token", current)).status());
                assertEquals(403, second.post("/transfer", RequestOptions.create()
                        .setHeader("Origin", "https://foreign.invalid").setHeader("X-CSRF-Token", current)).status());
                assertEquals(415, second.post("/transfer", RequestOptions.create().setHeader("Origin", ORIGIN)
                        .setHeader("X-CSRF-Token", current).setHeader("Content-Type", "application/json").setData("{}")).status());
                assertEquals(415, second.post("/transfer?owner_id=" + OWNER, RequestOptions.create()
                        .setHeader("Origin", ORIGIN).setHeader("X-CSRF-Token", current)
                        .setForm(FormData.create().set("month", "2052-04"))).status());
                System.out.println("STEP5 {\"case\":\"nativeCsrf\",\"crossSessionTokenRejected\":true,"
                        + "\"foreignAndNullOriginsRejected\":true,\"sessionAndTokenRotated\":true,"
                        + "\"staleTokenRejected\":true,\"missingOriginRejected\":true,\"invalidTransferShapeRejected\":true}");
            } finally { first.dispose(); second.dispose(); }
        }
    }

    @Test void browserOwnerFieldsCannotSelectForeignBudgetsOrCategories() {
        try (var pool = pool(); var pw = Playwright.create(); var browser = pw.chromium().launch();
             var session = context(browser)) {
            var store = new PostgresBudgetStore(new JdbcTransactions(pool), f -> fail("Unexpected persistence failure"));
            UUID foreign = UUID.randomUUID();
            store.initializeCatalog(OWNER);
            assertInstanceOf(SaveResult.Saved.class, store.initializeCatalog(foreign));
            saveMonth(store, OWNER, "123.45");
            saveMonth(store, foreign, "987.65");
            var foreignMonth = store.current(foreign, MONTH);
            var p = session.newPage(); login(p);
            p.navigate(URL + "?owner_id=" + foreign + "&budget_id=" + foreignMonth.budget().id());
            assertThat(p.locator("#save")).isEnabled();
            var data = new LinkedHashMap<String, Object>();
            data.put("month", MONTH.toString()); data.put("ownerId", foreign.toString());
            data.put("owner_id", foreign.toString()); data.put("budgetId", foreignMonth.budget().id().toString());
            Map<String, Object> own = object(rpc(p, "month", data));
            assertEquals("123,45", own.get("income"));
            data.put("version", 1);
            assertEquals("123,45", object(rpc(p, "revision", data)).get("income"));
            var lines = new ArrayList<Map<String, Object>>();
            for (Object value : (List<?>) own.get("lines")) {
                var row = object(value);
                lines.add(new LinkedHashMap<>(Map.of("id", row.get("id"), "amount", row.get("amount"))));
            }
            lines.getFirst().put("id", foreignMonth.catalog().catalog().categories().getFirst().id().toString());
            data.put("version", own.get("version")); data.put("catalogVersion", own.get("catalogVersion"));
            data.put("income", "999"); data.put("investment", ""); data.put("lines", lines);
            assertEquals("invalid", object(rpc(p, "save", data)).get("error"));
            assertEquals(new BigDecimal("123.45"), store.current(OWNER, MONTH).budget().income().amount().value().orElseThrow());
            assertEquals(new BigDecimal("987.65"), store.current(foreign, MONTH).budget().income().amount().value().orElseThrow());
            assertEquals(1, store.revisions(OWNER, store.current(OWNER, MONTH).budget().id()).size());
            assertEquals(1, store.revisions(foreign, foreignMonth.budget().id()).size());
            System.out.println("STEP5 {\"case\":\"ownerIsolation\",\"forgedOwnerReadReturnedOwnData\":true,"
                    + "\"foreignCategorySaveRejected\":true,\"bothBudgetValuesAndRevisionCountsUnchanged\":true}");
        }
    }

    @Test void storedHtmlAndSqlLookingCategoryLabelRemainsLiteral() {
        try (var pw = Playwright.create(); var browser = pw.chromium().launch(); var session = context(browser)) {
            var p = session.newPage(); login(p);
            var catalog = object(rpc(p, "catalog", Map.of()));
            var original = new ArrayList<Map<String, Object>>();
            for (Object row : (List<?>) catalog.get("categories")) original.add(new LinkedHashMap<>(object(row)));
            var changed = new ArrayList<Map<String, Object>>();
            for (var row : original) changed.add(new LinkedHashMap<>(row));
            String label = "<img id=review-injected src=x onerror=window.reviewInjected=1>'); DROP TABLE budget.category;--";
            int index = 0;
            while (Boolean.TRUE.equals(changed.get(index).get("archived"))) index++;
            changed.get(index).put("label", label);
            try {
                assertEquals(true, object(rpc(p, "saveCatalog",
                        Map.of("version", catalog.get("version"), "categories", changed))).get("saved"));
                p.reload(); assertThat(p.locator("#save")).isEnabled();
                assertThat(p.locator("#planner")).containsText(label);
                assertEquals(0, p.locator("#review-injected").count());
                assertNull(p.evaluate("window.reviewInjected"));
                var loaded = object(rpc(p, "catalog", Map.of()));
                assertEquals(original.size(), ((List<?>) loaded.get("categories")).size());
                assertEquals(label, object(((List<?>) loaded.get("categories")).get(index)).get("label"));
                System.out.println("STEP5 {\"case\":\"literalCategoryRendering\",\"labelRoundTrippedExactly\":true,"
                        + "\"htmlNotCreated\":true,\"scriptNotExecuted\":true,\"catalogStillReadable\":true}");
            } finally {
                var latest = object(rpc(p, "catalog", Map.of()));
                assertEquals(true, object(rpc(p, "saveCatalog",
                        Map.of("version", latest.get("version"), "categories", original))).get("saved"));
            }
        }
    }

    @Test void vaadinRpcRequiresItsCsrfTokenAndOldSessionDiesAtLogout() {
        try (var pw = Playwright.create(); var browser = pw.chromium().launch(); var session = context(browser)) {
            var p = session.newPage(); login(p);
            Request captured = p.waitForRequest(r -> r.method().equals("POST") && r.url().contains("v-r=uidl"),
                    () -> rpc(p, "month", Map.of("month", "2052-06")));
            String body = captured.postData();
            assertNotNull(body);
            assertEquals(true, p.evaluate("s => typeof JSON.parse(s).csrfToken === 'string'", body));
            String missing = (String) p.evaluate("s => { const v=JSON.parse(s); delete v.csrfToken; return JSON.stringify(v); }", body);
            var rejected = session.request().post(captured.url(), json(missing));
            assertEquals(200, rejected.status());
            assertTrue(rejected.text().contains("\"appError\""), "Expected framework security refresh response");
            String old = session.cookies(URL).stream().filter(c -> c.name.equals("JSESSIONID"))
                    .map(c -> "JSESSIONID=" + c.value).findFirst().orElseThrow();
            String csrf = p.locator("form[action='logout'] input[name='csrf']").getAttribute("value");
            var logout = session.request().post(URL + "logout", RequestOptions.create().setMaxRedirects(0)
                    .setHeader("Origin", ORIGIN).setForm(FormData.create().set("csrf", csrf)));
            assertEquals(303, logout.status());
            assertTrue(logout.headers().get("set-cookie").contains("Max-Age=0"));
            assertEquals(401, session.request().post(captured.url(), json(body).setHeader("Cookie", old)).status());
            assertEquals("no-store", logout.headers().get("cache-control"));
            System.out.println("STEP5 {\"case\":\"vaadinCsrfAndLogoutReplay\",\"missingRpcTokenRejected\":true,"
                    + "\"logoutClearsCookie\":true,\"oldSessionRpcRejected\":true}");
        }
    }

    static APIRequestContext api(Playwright pw) {
        return pw.request().newContext(new APIRequest.NewContextOptions().setBaseURL(URL).setIgnoreHTTPSErrors(true));
    }
    static BrowserContext context(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true).setLocale("en-US"));
    }
    static void login(Page p) {
        p.navigate(URL + "login");
        p.locator("#username").fill("synthetic-user"); p.locator("#password").fill("synthetic-test-password-only");
        p.locator("button[type=submit]").click();
        assertThat(p.locator("#planner[data-mode='cloud']")).isVisible();
        assertThat(p.locator("#save")).isEnabled();
    }
    static RequestOptions credentials(String token) {
        return RequestOptions.create().setMaxRedirects(0).setForm(FormData.create().set("csrf", token)
                .set("username", "synthetic-user").set("password", "synthetic-test-password-only"));
    }
    static String token(APIRequestContext api) { return match(api.get("/login").text(), "name=\"csrf\" value=\"([^\"]+)\""); }
    static String transferToken(APIRequestContext api) {
        return match(api.get("/transfer").text(), "id=\"csrf\" value=\"([^\"]+)\"");
    }
    static String match(String text, String pattern) {
        var matcher = Pattern.compile(pattern).matcher(text); assertTrue(matcher.find(), "Expected CSRF field"); return matcher.group(1);
    }
    static String cookie(APIRequestContext api) {
        return api.cookies(URL).stream().filter(c -> c.name.equals("JSESSIONID")).map(c -> c.value).findFirst().orElseThrow();
    }
    static RequestOptions json(String body) {
        return RequestOptions.create().setHeader("Origin", ORIGIN).setHeader("Content-Type", "application/json").setData(body);
    }
    @SuppressWarnings("unchecked") static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
    static Object rpc(Page p, String action, Map<String, ?> payload) {
        return p.evaluate("async args => { let r=document.querySelector('#planner');"
                + "while(r && typeof r.$server?.planner!=='function') r=r.parentElement;"
                + "if(!r) throw Error('Planner bridge unavailable');"
                + "return JSON.parse(await r.$server.planner(args[0],JSON.stringify(args[1]))); }", List.of(action, payload));
    }
    static HikariDataSource pool() {
        var c = new HikariConfig(); c.setJdbcUrl(System.getenv("APP_DB_URL"));
        c.setUsername(System.getenv("APP_DB_USER")); c.setPassword(System.getenv("APP_DB_PASSWORD"));
        c.setMaximumPoolSize(2); return new HikariDataSource(c);
    }
    static void saveMonth(PostgresBudgetStore store, UUID owner, String income) {
        var view = store.current(owner, MONTH); var b = view.budget();
        var result = store.save(owner, new SaveBudget(MONTH, view.version(), view.catalog().revision().version(),
                BudgetAmount.set(new BigDecimal(income)), b.lines().stream().filter(l -> l.kind() == BudgetLine.Kind.SPENDING)
                .map(l -> new SpendingValue(l.categoryId().orElseThrow(), l.amount())).toList(), b.investment().amount()));
        assertInstanceOf(SaveResult.Saved.class, result);
    }
}
