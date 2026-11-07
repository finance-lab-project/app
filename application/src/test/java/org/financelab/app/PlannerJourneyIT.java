package org.financelab.app;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    private static void saveLocally(Page page, String income) {
        page.locator("#income").fill(income);
        button(page, "Сохранить").click();
        assertThat(page.locator("#status")).hasText("Сохранено в этом браузере.");
    }

    @Test void guestToCloudAndSecondBrowser() throws Exception {
        boolean testTls = !"true".equals(System.getenv("APP_TRUSTED_TLS"));
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch();
             var guest = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 880).setIgnoreHTTPSErrors(testTls));
             var other = browser.newContext(new Browser.NewContextOptions().setViewportSize(390, 844).setIgnoreHTTPSErrors(testTls))) {
            var first = guest.newPage();
            first.navigate(URL);
            assertThat(first.locator("h1")).hasText("Бюджет на месяц");
            assertThat(button(first, "Сохранить")).isEnabled();
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
            var cloudIncome = first.locator("#income input");
            assertThat(cloudIncome).hasValue("12 345,67");
            assertThat(first.locator("#totals")).containsText("12 050,00");
            cloudIncome.fill("15000");
            button(first, "Сохранить").click();
            assertThat(first.locator("#save-status")).hasAttribute("data-state", "saved");
            capture(first, "account-desktop.png");

            login(second);
            button(second, "Перенести месяцы").click();
            assertThat(second.locator("#months")).containsText("15 000,00");
            assertThat(second.locator("#months")).containsText("14 000,00");
            assertNotNull(second.evaluate("month => localStorage.getItem('finance-lab.guest.v1.' + month)", month));
            // Change the cloud revision while the second browser is reviewing the conflict.
            cloudIncome.fill("16000");
            button(first, "Сохранить").click();
            assertThat(first.locator("#save-status")).hasAttribute("data-state", "saved");
            button(second, "Подтвердить перенос").click();
            assertThat(second.locator("#months")).containsText("16 000,00");
            button(second, "Подтвердить перенос").click();
            assertThat(second.locator("#months")).containsText("Сохранено в аккаунте.");
            second.getByText("Открыть мой бюджет").click();
            assertThat(second.locator("#income input")).hasValue("14 000,00");
            // Blank guest fields preserve the cloud spending and investment.
            assertThat(second.locator("#totals")).containsText("13 704,33");
            onscreen(second);
            capture(second, "account-mobile.png");
            first.reload();
            assertThat(first.locator("#income input")).hasValue("14 000,00");
            button(first, "История").click();
            assertThat(first.locator("h2").first()).containsText("История");
            button(second, "Выйти").click();
            assertThat(second.locator("#income")).hasValue("");
            assertEquals(401, other.request().get(URL + "transfer").status());
            var privacy = other.request().get(URL + "privacy");
            assertEquals(200, privacy.status());
            assertTrue(privacy.text().contains("JSESSIONID"));
        }
    }
}
