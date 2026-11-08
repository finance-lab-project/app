package org.financelab.app;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.financelab.budget.adapter.postgres.PostgresBudgetStore;
import org.financelab.budget.adapter.ui.PlannerTransfer;
import org.financelab.budget.application.*;
import org.financelab.budget.domain.*;
import org.financelab.orm.JdbcTransactions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Review-only characterization checks. Passing reproductions do not mean the observed limitations are fixed. */
@Timeout(180)
class WorkflowReviewIT {
    private static final String URL = Objects.requireNonNull(System.getenv("APP_BASE_URL"));
    private static final Path EVIDENCE = Path.of(Objects.requireNonNull(System.getenv("REVIEW_EVIDENCE_DIR")));
    private static Locator button(Page p, String name) {
        return p.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }
    private static void period(Page p, String year, String month) {
        assertThat(p.locator("#save")).isEnabled();
        p.locator("#year").fill(year); p.locator("#year").press("Tab");
        p.locator("#month").selectOption(month);
        assertThat(p.locator("#year")).hasValue(year);
        assertThat(p.locator("#month")).hasValue(month);
    }
    private static void login(Page p) {
        p.navigate(URL + "login");
        p.locator("#username").fill("synthetic-user");
        p.locator("#password").fill("synthetic-test-password-only");
        button(p, "Войти").click();
        assertThat(p.locator("#planner[data-mode='cloud']")).isVisible();
        assertThat(p.locator("#save")).isEnabled();
    }
    private static BrowserContext context(Browser b) {
        return b.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true)
                .setLocale("en-US").setTimezoneId("Pacific/Honolulu"));
    }

    @Test void sharedGuestValidationNavigationAndHistory() throws Exception {
        try (var pw=Playwright.create(); var browser=pw.chromium().launch(); var c=context(browser)) {
            var p=c.newPage(); p.navigate(URL+"local"); period(p,"2094","04");
            p.evaluate("""
                    () => {
                      window.reviewNavigation=[];
                      document.querySelector('#leave-dialog').addEventListener('close', () => {
                        window.reviewNavigation.push({event:'close',value:document.querySelector('#leave-dialog').returnValue,
                          month:document.querySelector('#month').value,status:document.querySelector('#status').textContent});
                      });
                    }
                    """);
            p.locator("#income").fill("10,001"); p.locator("#save").click();
            assertThat(p.locator("#status")).hasText("Проверьте отмеченные суммы.");
            assertThat(p.locator("#income")).hasValue("10,001");
            assertNull(p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-04')"));
            p.locator("#income").fill("100"); p.locator("#investment").fill("120"); p.locator("#spend-0").fill("0");
            assertThat(p.locator("#balance")).hasText("−20,00 ₽");
            p.locator("#save").click(); assertThat(p.locator("#status")).hasText("Сохранено в этом браузере.");
            p.locator("#save").click(); assertThat(p.locator("#status")).hasText("Сохранено в этом браузере.");
            button(p,"История").click(); assertThat(p.locator("#revision option")).hasCount(1);
            @SuppressWarnings("unchecked")
            var detail=(Map<String,String>)p.evaluate("""
                    () => Object.fromEntries([...document.querySelectorAll('#revision-detail dt')]
                       .map(n => [n.textContent,n.nextElementSibling.textContent]))
                    """);
            assertEquals("0,00 ₽",detail.get("Жильё и коммунальные услуги"));
            assertEquals("Не задано",detail.get("Продукты"));
            button(p,"План месяца").click();
            p.locator("#income").fill("99999999999999999.99"); p.locator("#save").click();
            assertThat(p.locator("#status")).hasText("Сохранено в этом браузере.");
            assertThat(p.locator("#income")).hasValue("99 999 999 999 999 999,99");
            Object before=p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-04')");
            p.locator("#income").fill("100000000000000000"); p.locator("#save").click();
            assertThat(p.locator("#income-error")).hasText("Сумма превышает допустимый предел.");
            assertEquals(before,p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-04')"));
            p.locator("#income").fill("-1"); p.locator("#save").click();
            assertThat(p.locator("#status")).hasText("Проверьте отмеченные суммы.");
            assertEquals(before,p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-04')"));
            button(p,"История").click(); button(p,"Не сохранять").click();
            assertThat(p.locator("#revision option")).hasCount(2);
            button(p,"План месяца").click(); p.locator("#income").fill("125");
            button(p,"Категории").click(); assertThat(p.locator("#leave-dialog")).isVisible();
            button(p,"Остаться").click(); assertThat(p.locator("#income")).hasValue("125");
            button(p,"Категории").click(); button(p,"Сохранить").last().click();
            assertThat(p.locator("#catalog-panel")).isVisible();
            button(p,"План месяца").click(); assertThat(p.locator("#income")).hasValue("125,00");
            p.locator("#income").fill("130"); p.locator("#month").selectOption("05");
            assertThat(p.locator("#leave-dialog")).isVisible(); button(p,"Остаться").click();
            assertThat(p.locator("#month")).hasValue("04"); assertThat(p.locator("#income")).hasValue("130");
            assertThat(p.locator("#leave-dialog")).isHidden();
            p.waitForCondition(() -> ((Number)p.evaluate("window.reviewNavigation.length")).intValue()==4);
            p.locator("#month").selectOption("05"); button(p,"Не сохранять").click();
            try { assertThat(p.locator("#month")).hasValue("05"); }
            catch (AssertionError failure) {
                Files.createDirectories(EVIDENCE);
                Files.writeString(EVIDENCE.resolve("navigation-diagnostic.json"),JsonMapper.builder().build().writeValueAsString(
                        p.evaluate("({trace:window.reviewNavigation,month:document.querySelector('#month').value,status:document.querySelector('#status').textContent})")));
                throw failure;
            }
            assertThat(p.locator("#income")).hasValue("");
            assertEquals("125",p.evaluate("JSON.parse(localStorage.getItem('finance-lab.guest.v1.2094-04')).versions.at(-1).income"));
            p.locator("#income").fill("50"); button(p,"История").click(); button(p,"Не сохранять").click();
            assertThat(p.locator("#status")).hasText("Сохранённых версий пока нет.");
            assertNull(p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-05')"));
        }
    }

    @Test void rapidlyReopenedDialogConsumesThePreviousCloseEvent() throws Exception {
        try (var pw=Playwright.create(); var browser=pw.chromium().launch(); var c=context(browser)) {
            var p=c.newPage(); p.navigate(URL+"local"); period(p,"2096","01");
            p.locator("#income").fill("100"); p.locator("#save").click();
            assertThat(p.locator("#status")).hasText("Сохранено в этом браузере.");
            Object saved=p.evaluate("localStorage.getItem('finance-lab.guest.v1.2096-01')");
            p.locator("#income").fill("130");
            p.evaluate("""
                    () => {
                      const month=document.querySelector('#month'), dialog=document.querySelector('#leave-dialog');
                      window.reviewReentry=[];
                      dialog.addEventListener('close',()=>window.reviewReentry.push({value:dialog.returnValue,open:dialog.open}));
                      const choose=()=>{month.value='02';month.dispatchEvent(new Event('change',{bubbles:true}));};
                      choose();
                      dialog.querySelector('button[value=stay]').click();
                      choose();
                    }
                    """);
            p.waitForCondition(() -> ((Number)p.evaluate("window.reviewReentry.length")).intValue()==1);
            assertThat(p.locator("#leave-dialog")).isVisible();
            button(p,"Не сохранять").click();
            p.waitForCondition(() -> ((Number)p.evaluate("window.reviewReentry.length")).intValue()==2);
            assertThat(p.locator("#month")).hasValue("01");
            assertThat(p.locator("#income")).hasValue("130");
            assertThat(p.locator("#status")).hasText("Есть несохранённые изменения.");
            assertEquals(saved,p.evaluate("localStorage.getItem('finance-lab.guest.v1.2096-01')"));
            Object events=p.evaluate("window.reviewReentry");
            assertEquals(List.of(Map.of("value","stay","open",true),Map.of("value","discard","open",false)),events);
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("navigation-reentry.json"),JsonMapper.builder().build().writeValueAsString(events)+"\n");
            p.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE.resolve("dialog-reentry.png")).setFullPage(true));
        }
    }

    @Test void confirmedAccountSaveFollowedByReadFailureBlocksTheEditor() throws Exception {
        try (var pw=Playwright.create(); var browser=pw.chromium().launch();
             var first=context(browser); var second=context(browser)) {
            var p=first.newPage(); login(p); period(p,"2095","05");
            p.evaluate("""
                    () => {
                      let root=document.querySelector('#planner');
                      while (root && typeof root.$server?.planner !== 'function') root=root.parentElement;
                      if (!root) throw Error('Review setup: Vaadin planner bridge not found');
                      const original=root.$server.planner.bind(root.$server);
                      const state=window.reviewSave={writes:0,failedReads:0,committed:false};
                      const wrapped=async (action,payload) => {
                        if (action==='month' && state.committed) {
                          state.failedReads++; throw Error('synthetic refresh failure');
                        }
                        const result=await original(action,payload);
                        if (action==='save' && JSON.parse(result).saved) {
                          state.writes++; state.committed=true;
                        }
                        return result;
                      };
                      root.$server.planner=wrapped;
                      if (root.$server.planner!==wrapped) throw Error('Review setup: bridge could not be wrapped');
                    }
                    """);
            p.locator("#income").fill("1111,11"); button(p,"Категории").click();
            button(p,"Сохранить").last().click();
            assertThat(p.locator("#status")).hasText("Не удалось прочитать данные. Попробуйте загрузить их снова.");
            assertThat(p.locator("#save")).isDisabled();
            assertThat(p.locator("#month-panel")).isVisible();
            assertThat(p.locator("#catalog-panel")).isHidden();
            assertThat(p.locator("#income")).hasValue("1111,11");
            assertEquals(1,((Number)p.evaluate("window.reviewSave.writes")).intValue());
            assertEquals(1,((Number)p.evaluate("window.reviewSave.failedReads")).intValue());
            Files.createDirectories(EVIDENCE);
            p.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE.resolve("confirmed-save-refresh-failure.png")).setFullPage(true));
            var other=second.newPage(); login(other); period(other,"2095","05");
            assertThat(other.locator("#income")).hasValue("1 111,11");
            button(other,"История").click(); assertThat(other.locator("#revision option")).hasCount(1);
        }
    }

    @Test void localAcknowledgementFailureKeepsTheCopyAndRetryDoesNotDuplicateHistory() {
        try (var pw=Playwright.create(); var browser=pw.chromium().launch(); var c=context(browser)) {
            var p=c.newPage(); p.navigate(URL+"local"); period(p,"2094","06");
            p.locator("#income").fill("400"); p.locator("#save").click();
            assertThat(p.locator("#status")).hasText("Сохранено в этом браузере.");
            Object original=p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-06')");
            p.navigate(URL+"login"); p.locator("#username").fill("synthetic-user");
            p.locator("#password").fill("synthetic-test-password-only"); button(p,"Войти").click();
            assertThat(p.locator("#send")).isVisible();
            p.evaluate("""
                    () => {
                      const original=Storage.prototype.removeItem;
                      Storage.prototype.removeItem=function(key) {
                        if (key==='finance-lab.guest.v1.2094-06') throw Error('synthetic local acknowledgement failure');
                        return original.call(this,key);
                      };
                      window.restoreReviewRemove=() => { Storage.prototype.removeItem=original; };
                    }
                    """);
            button(p,"Перенести месяцы").click();
            assertThat(p.locator("#months")).containsText(java.util.regex.Pattern.compile("Подтвердить перенос|synthetic local acknowledgement failure"));
            if (button(p,"Подтвердить перенос").isVisible()) button(p,"Подтвердить перенос").click();
            assertThat(p.locator("#months")).containsText("synthetic local acknowledgement failure");
            assertEquals(original,p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-06')"));
            p.evaluate("window.restoreReviewRemove()");
            p.getByText("Открыть мой бюджет",new Page.GetByTextOptions().setExact(true)).click();
            period(p,"2094","06"); assertThat(p.locator("#income")).hasValue("400,00");
            button(p,"История").click(); assertThat(p.locator("#revision option")).hasCount(1);
            p.navigate(URL+"transfer"); button(p,"Перенести месяцы").click();
            assertThat(p.locator("#months")).containsText("Сохранено в аккаунте.");
            assertNull(p.evaluate("localStorage.getItem('finance-lab.guest.v1.2094-06')"));
            p.getByText("Открыть мой бюджет",new Page.GetByTextOptions().setExact(true)).click();
            period(p,"2094","06"); button(p,"История").click();
            assertThat(p.locator("#revision option")).hasCount(1);
        }
    }

    @Test void staleAccountAndCatalogSavesKeepDraftsWithoutAppendingHistory() {
        try (var pw=Playwright.create(); var browser=pw.chromium().launch();
             var one=context(browser); var two=context(browser)) {
            var a=one.newPage(); var b=two.newPage();
            login(a); login(b); period(a,"2095","07"); period(b,"2095","07");
            b.locator("#income").fill("200");
            a.locator("#income").fill("100"); a.locator("#save").click();
            assertThat(a.locator("#status")).hasText("Сохранено в аккаунте.");
            b.locator("#save").click();
            assertThat(b.locator("#status")).containsText("Сохранённые данные изменились");
            assertThat(b.locator("#income")).hasValue("200"); assertThat(b.locator("#save")).isDisabled();
            b.onceDialog(Dialog::accept); b.locator("#reload-plan").click();
            assertThat(b.locator("#income")).hasValue("100,00"); assertThat(b.locator("#save")).isEnabled();
            b.locator("#income").fill("300");
            button(a,"Категории").click();
            a.locator("#categories input:not([readonly])").first().fill("Проверка каталога");
            a.locator("#save-catalog").click();
            assertThat(a.locator("#status")).hasText("Категории сохранены.");
            b.locator("#save").click();
            assertThat(b.locator("#status")).containsText("Сохранённые данные изменились");
            assertThat(b.locator("#income")).hasValue("300"); assertThat(b.locator("#save")).isDisabled();
            b.onceDialog(Dialog::accept); b.locator("#reload-plan").click();
            assertThat(b.locator("#income")).hasValue("100,00");
            button(a,"История").click(); assertThat(a.locator("#revision option")).hasCount(1);
            assertThat(a.locator("#revision-detail")).containsText("100,00 ₽");
            assertThat(a.locator("#revision-detail")).not().containsText("Проверка каталога");
        }
    }

    enum Fault { FAILED_BEFORE, UNKNOWN_BEFORE, UNKNOWN_AFTER }
    @ParameterizedTest @EnumSource(Fault.class)
    void interruptedReplayAndUserConfirmedRetry(Fault fault) throws Exception {
        var configuration=new HikariConfig();
        configuration.setJdbcUrl(Objects.requireNonNull(System.getenv("APP_DB_URL")));
        configuration.setUsername(System.getenv("APP_DB_USER")); configuration.setPassword(System.getenv("APP_DB_PASSWORD"));
        configuration.setMaximumPoolSize(2);
        try (var pool=new HikariDataSource(configuration)) {
            var store=new PostgresBudgetStore(new JdbcTransactions(pool),failure -> {});
            UUID owner=UUID.randomUUID(); store.initializeCatalog(owner);
            var intercepted=new InterceptedStore(store,fault);
            var service=new BudgetService(intercepted,() -> owner);
            var month=YearMonth.of(2093,fault.ordinal()+1);
            var form=new LinkedHashMap<String,String[]>();
            form.put("month",new String[]{month.toString()});
            form.put("income",new String[]{"300"}); form.put("investment",new String[]{""});
            form.put("label",CategoryCatalog.starterLabels().toArray(String[]::new));
            for (int i=0;i<10;i++) form.put("spend-"+i,new String[]{""});
            String earlier=JsonMapper.builder().build().writeValueAsString(List.of(
                    Map.of("income","100","investment","","spending",Collections.nCopies(10,"")),
                    Map.of("income","200","investment","","spending",Collections.nCopies(10,""))));
            form.put("earlier",new String[]{earlier});
            var stopped=PlannerTransfer.submit(service,form);
            assertEquals(409,stopped.status());
            assertTrue(stopped.body().get("message").toString().contains("Перенос версий остановлен"));
            var prefix=store.current(owner,month);
            int prefixSize=fault==Fault.UNKNOWN_AFTER?2:1;
            assertEquals(prefixSize,prefix.version());
            assertEquals(prefixSize*100,prefix.budget().income().amount().value().orElseThrow().intValueExact());
            assertEquals(2,intercepted.attempts);
            var preview=PlannerTransfer.submit(service,form);
            assertEquals(409,preview.status()); assertTrue(preview.body().containsKey("version"));
            assertEquals(2,intercepted.attempts,"Preview must not automatically retry a write");
            var confirmed=new LinkedHashMap<>(form);
            confirmed.put("version",new String[]{preview.body().get("version").toString()});
            confirmed.put("catalog",new String[]{preview.body().get("catalog").toString()});
            var done=PlannerTransfer.submit(service,confirmed);
            assertEquals(200,done.status()); assertEquals(false,done.body().get("copiedHistory"));
            var current=store.current(owner,month);
            assertEquals(300,current.budget().income().amount().value().orElseThrow().intValueExact());
            var amounts=new ArrayList<Integer>();
            for (var revision:store.revisions(owner,current.budget().id()).reversed()) {
                amounts.add(store.revision(owner,current.budget().id(),revision.version()).orElseThrow()
                        .budget().income().amount().value().orElseThrow().intValueExact());
            }
            assertEquals(fault==Fault.UNKNOWN_AFTER?List.of(100,200,300):List.of(100,300),amounts);
            assertEquals(earlier,form.get("earlier")[0]);
            Files.createDirectories(EVIDENCE);
            Files.writeString(EVIDENCE.resolve("replay-"+fault.name().toLowerCase(Locale.ROOT)+".txt"),
                    "fault="+fault+"\nstatusAfterInterruption="+stopped.status()+"\ncommittedPrefix="+prefixSize+
                    "\nwriteAttemptsBeforeExplicitConfirmation=2\naccountIncomeHistoryAfterRetry="+amounts+
                    "\nlatestIncome=300\ncopiedHistoryOnRetry=false\n");
        }
    }

    private static final class InterceptedStore implements BudgetStore {
        private final BudgetStore delegate; private final Fault fault; private int attempts;
        InterceptedStore(BudgetStore delegate,Fault fault) { this.delegate=delegate; this.fault=fault; }
        public SaveResult<BudgetRevision> save(UUID owner,SaveBudget command) {
            attempts++;
            if (attempts==2) {
                if (fault==Fault.UNKNOWN_AFTER) assertInstanceOf(SaveResult.Saved.class,delegate.save(owner,command));
                return fault==Fault.FAILED_BEFORE?new SaveResult.Failed<>():new SaveResult.OutcomeUnknown<>();
            }
            return delegate.save(owner,command);
        }
        public SaveResult<CatalogRevision> initializeCatalog(UUID o) { return delegate.initializeCatalog(o); }
        public SaveResult<CatalogRevision> saveCatalog(UUID o,long v,CategoryCatalog c) { return delegate.saveCatalog(o,v,c); }
        public Optional<CatalogView> catalog(UUID o) { return delegate.catalog(o); }
        public BudgetView current(UUID o,YearMonth m) { return delegate.current(o,m); }
        public List<BudgetRevision> revisions(UUID o,UUID id) { return delegate.revisions(o,id); }
        public Optional<BudgetView> revision(UUID o,UUID id,long v) { return delegate.revision(o,id,v); }
    }
}
