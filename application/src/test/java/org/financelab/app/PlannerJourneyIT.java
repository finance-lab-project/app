package org.financelab.app;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class PlannerJourneyIT {
    private static final String URL = System.getenv("APP_BASE_URL");
    private static Locator button(Page page, String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }
    private static void login(Page page) {
        page.navigate(URL + "login");
        page.locator("#username").fill("synthetic-user");
        page.locator("#password").fill("synthetic-test-password-only");
        button(page, "Войти").click();
    }
    private static void capture(Page page, String file) throws Exception {
        if (System.getenv("APP_SCREENSHOT_DIR") == null) return;
        Path directory = Path.of(System.getenv("APP_SCREENSHOT_DIR"));
        Files.createDirectories(directory);
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(file)).setFullPage(true));
    }
    private static void onscreen(Page page) {
        assertEquals(true, page.evaluate("document.documentElement.scrollWidth <= innerWidth + 1"),
                "No horizontal overflow at this viewport");
    }
    private static Object layout(Page p) {
        return p.evaluate("() => [...document.querySelectorAll('.planner-nav button, #plan section h2, #plan label, #plan button, .summary dt')].map(e=>e.textContent.trim())");
    }
    private static void noticeAtBottom(Page p) {
        assertThat(p.locator("#cookie-notice")).isVisible();
        assertEquals(true, p.evaluate("Math.abs(document.querySelector('#cookie-notice').getBoundingClientRect().bottom - innerHeight) <= 1"));
    }
    private static void saveLocally(Page page, String income) {
        page.locator("#income").fill(income);
        page.locator("#save").click();
        assertThat(page.locator("#status")).hasText("Сохранено в этом браузере.");
    }

    @Test @org.junit.jupiter.api.Order(2) void sameCategoryAndHistoryToolsInGuestAndAccount() throws Exception {
        try (var pw = Playwright.create(); var browser = pw.chromium().launch();
             var session = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
            var page = session.newPage(); page.navigate(URL);
            assertThat(page.locator("#save")).isEnabled();
            // An older browser budget opens under the new catalog without rewriting its saved data.
            page.evaluate("localStorage.setItem('finance-lab.guest.v1.2091-03',JSON.stringify({income:'300',investment:'0',spending:Array(10).fill('')}))");
            period(page, "2091","03");
            assertThat(page.locator("#income")).hasValue("300");
            button(page,"Категории").click(); button(page,"Добавить категорию").click();
            page.locator("#categories input").last().fill("Учебная поездка");
            button(page,"Сохранить категории").click();
            assertThat(page.locator("#status")).hasText("Категории сохранены.");
            button(page,"План месяца").click();
            page.getByLabel("Учебная поездка").fill("25,50");
            page.locator("#save").click();
            assertThat(page.locator("#balance")).hasText("274,50 ₽");
            button(page,"История").click();
            assertThat(page.locator("#revision-detail")).containsText("Учебная поездка");
            page.locator("#revision").selectOption("1");
            assertThat(page.locator("#revision-detail")).not().containsText("Учебная поездка");
            login(page);
            button(page,"Перенести категории").click();
            button(page,"Подтвердить категории").click();
            assertThat(page.locator("#catalog-review")).containsText("Категории сохранены в аккаунте.");
            button(page,"Перенести месяцы").click();
            button(page,"Подтвердить перенос").click();
            assertThat(page.locator("#months")).containsText("Сохранено в аккаунте.");
            page.getByText("Открыть мой бюджет").click();
            period(page,"2091","03");
            assertThat(page.locator("#balance")).hasText("274,50 ₽");
            button(page,"Категории").click();
            page.locator("#categories input").last().fill("Поездки");
            button(page,"Сохранить категории").click();
            assertThat(page.locator("#status")).hasText("Категории сохранены.");
            button(page,"План месяца").click();
            assertThat(page.getByLabel("Поездки")).hasValue("25,50");
            page.getByLabel("Поездки").fill("51"); page.locator("#save").click();
            assertThat(page.locator("#status")).hasText("Сохранено в аккаунте.");
            button(page,"История").click();
            assertThat(page.locator("#revision-detail")).containsText("Поездки");
            page.locator("#revision").selectOption("1");
            assertThat(page.locator("#revision-detail")).containsText("Учебная поездка");
            button(page,"Выйти").click(); period(page,"2091","03"); button(page,"История").click();
            assertThat(page.locator("#revision-detail")).containsText("Учебная поездка");
        }
    }
    private static void period(Page p, String year, String month) {
        assertThat(p.locator("#save")).isEnabled();
        p.locator("#year").fill(year); p.locator("#year").press("Tab");
        p.locator("#month").selectOption(month);
        assertThat(p.locator("#year")).hasValue(year);
        assertThat(p.locator("#month")).hasValue(month);
    }

    @Test @org.junit.jupiter.api.Order(1) void guestToCloudAndSecondBrowser() throws Exception {
        boolean testTls = !"true".equals(System.getenv("APP_TRUSTED_TLS"));
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch();
             var guest = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 880).setIgnoreHTTPSErrors(testTls));
             var other = browser.newContext(new Browser.NewContextOptions().setViewportSize(390, 844).setIgnoreHTTPSErrors(testTls))) {
            var first = guest.newPage();
            first.navigate(URL);
            assertThat(first.locator("h1")).hasText("Бюджет на месяц");
            assertThat(first.locator("#save")).isEnabled();
            var guestLayout = layout(first);
            assertEquals(java.util.List.of("Доход","Инвестиции","Расходы"), first.locator("#plan section h2").allTextContents());
            noticeAtBottom(first);
            first.reload();
            noticeAtBottom(first);
            button(first,"Закрыть").click();
            first.reload();
            assertThat(first.locator("#cookie-notice")).isHidden();
            assertTrue(guest.cookies().isEmpty(), "Guest pages do not set a session cookie");
            assertThat(first.locator("#month option:checked")).hasText(java.util.regex.Pattern.compile("[А-Яа-яЁё]+"));
            String month = first.locator("#year").inputValue() + "-" + first.locator("#month").inputValue();
            guest.setOffline(true);
            first.locator("#spend-1").fill("45,67");
            first.locator("#investment").fill("250");
            saveLocally(first, "12 345,67");
            assertThat(first.locator("#balance")).hasText("12 050,00 ₽");
            guest.setOffline(false);
            first.reload();
            assertThat(first.locator("#income")).hasValue("12 345,67");
            onscreen(first);
            capture(first, "guest-desktop.png");

            var second = other.newPage();
            second.navigate(URL);
            assertThat(second.locator("#income")).hasValue("");
            noticeAtBottom(second);
            // Same month, independent local draft for the conflict path.
            saveLocally(second, "14000");
            onscreen(second);
            capture(second, "guest-mobile.png");
            // Save-and-sign-in protects edits made since the last explicit save.
            first.locator("#income").fill("12345,67");
            first.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Сохранить и войти")).click();
            assertThat(first.locator("h1")).hasText("Вход в Finance Lab");
            capture(first, "login.png");
            login(first);
            assertThat(button(first, "Перенести месяцы")).isVisible();
            button(first, "Перенести месяцы").click();
            assertThat(first.locator("#months")).containsText("Сохранено в аккаунте.");
            assertNull(first.evaluate("month => localStorage.getItem('finance-lab.guest.v1.' + month)", month));
            first.getByText("Открыть мой бюджет").click();
            assertThat(first.locator("#save")).isEnabled();
            assertEquals(guestLayout, layout(first));
            assertThat(first.locator("#cookie-notice")).isHidden();
            var cloudIncome = first.locator("#income");
            assertThat(cloudIncome).hasValue("12 345,67");
            assertThat(first.locator("#balance")).containsText("12 050,00");
            cloudIncome.fill("15000");
            button(first, "Сохранить").click();
            assertThat(first.locator("#status")).hasText("Сохранено в аккаунте.");
            capture(first, "account-desktop.png");

            login(second);
            button(second, "Перенести месяцы").click();
            assertThat(second.locator("#months")).containsText("15 000,00");
            assertThat(second.locator("#months")).containsText("14 000,00");
            assertNotNull(second.evaluate("month => localStorage.getItem('finance-lab.guest.v1.' + month)", month));
            // Change the cloud revision while the second browser is reviewing the conflict.
            cloudIncome.fill("16000");
            button(first, "Сохранить").click();
            assertThat(first.locator("#status")).hasText("Сохранено в аккаунте.");
            button(second, "Подтвердить перенос").click();
            assertThat(second.locator("#months")).containsText("16 000,00");
            button(second, "Подтвердить перенос").click();
            assertThat(second.locator("#months")).containsText("Сохранено в аккаунте.");
            second.getByText("Открыть мой бюджет").click();
            assertThat(second.locator("#income")).hasValue("14 000,00");
            // Blank guest fields preserve the cloud spending and investment.
            assertThat(second.locator("#balance")).containsText("13 704,33");
            onscreen(second);
            capture(second, "account-mobile.png");
            first.reload();
            assertThat(first.locator("#income")).hasValue("14 000,00");
            button(first, "История").click();
            assertThat(first.locator("#revision-detail")).containsText("14 000,00");
            noticeAtBottom(second);
            button(second,"Закрыть").click();
            button(second, "Выйти").click();
            assertThat(second.locator("#income")).hasValue("");
            assertEquals(401, other.request().get(URL + "transfer").status());
            var privacy = other.request().get(URL + "privacy");
            assertEquals(200, privacy.status());
            assertTrue(privacy.text().contains("JSESSIONID"));
        }
    }
}
