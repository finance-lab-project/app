package org.financelab.app;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.financelab.budget.application.BudgetStore;
import org.financelab.budget.application.SaveResult;
import org.financelab.budget.domain.Budget;
import org.financelab.budget.domain.BudgetAmount;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.YearMonth;
import java.util.*;
import static org.financelab.budget.adapter.presentation.RussianBudgetFormat.parseAmount;

/** Import one explicitly selected local month using the existing versioned Budget save. */
public final class TransferServlet extends HttpServlet {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PlannerPages.page(response, "Перенос в аккаунт", "<a href=\"./\">Мой бюджет</a>", """
                <main class="reading"><h1>Из браузера в аккаунт</h1>
                <p>Перенесите сохранённые месяцы, чтобы открыть их на другом устройстве.
                Если месяц уже есть в аккаунте, вы сможете сравнить суммы.</p>
                <div id="transfer" data-count="%d"><input type="hidden" id="csrf" value="%s">
                <div id="months"></div><div class="actions"><button class="primary" id="send" hidden>Перенести месяцы</button>
                <a href="./">Открыть мой бюджет</a></div><p role="status" id="status"></p></div></main>"""
                .formatted(PlannerPages.STARTERS.size(), AccountServlet.csrf(request.getSession())), "transfer");
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        var config = (ApplicationConfig) getServletContext().getAttribute(ApplicationLifecycle.CONFIG_ATTRIBUTE);
        if (!config.publicOrigin().equals(request.getHeader("Origin"))
                || !AccountServlet.csrf(request.getSession(false)).equals(request.getHeader("X-CSRF-Token"))) {
            response.sendError(403);
            return;
        }
        if (request.getQueryString() != null || request.getContentType() == null
                || !request.getContentType().split(";", 2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) {
            response.sendError(415);
            return;
        }
        try {
            importMonth(request, response);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            reply(response, 400, Map.of("message", "Проверьте суммы и выбранные категории. Месяц остался в браузере."));
        } catch (RuntimeException e) {
            getServletContext().log("Budget transfer could not be confirmed");
            reply(response, 503, Map.of("message", "Результат переноса не подтверждён. Проверьте месяц в аккаунте. Локальная копия сохранена."));
        }
    }

    private record Option(String id, String label) { }
    private record Difference(String label, String local, String cloud) { }

    private static void importMonth(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String month = required(req, "month");
        if (!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException();
        var service = ApplicationLifecycle.service(req, () -> OwnerIdentity.require(req));
        var view = service.current(YearMonth.parse(month));
        Budget current = view.budget();
        var catalog = view.catalog().catalog();
        var lines = current.spending(catalog);
        var options = lines.stream().map(s -> new Option(s.category().id().toString(), s.category().label())).toList();
        var changes = new ArrayList<Difference>();
        var unresolved = new LinkedHashMap<String, String>();
        var assigned = new HashSet<UUID>();
        Budget draft = current;
        var income = amount(req, "income");
        var investment = amount(req, "investment");
        if (income.value().isPresent()) {
            difference(changes, "Доход", current.income().amount(), income);
            draft = draft.withIncome(income);
        }
        if (investment.value().isPresent()) {
            difference(changes, "Инвестиции", current.investment().amount(), investment);
            draft = draft.withInvestment(investment);
        }
        for (int i = 0; i < PlannerPages.STARTERS.size(); i++) {
            var value = amount(req, "spend-" + i);
            if (value.value().isEmpty()) continue;
            String label = PlannerPages.STARTERS.get(i);
            String target = req.getParameter("map-" + i);
            var matches = lines.stream().filter(s -> target == null ? label.equals(s.category().label())
                    : s.category().id().toString().equals(target)).toList();
            if (matches.size() != 1) { unresolved.put("map-" + i, label); continue; }
            var line = matches.getFirst();
            if (!assigned.add(line.category().id())) throw new IllegalArgumentException();
            difference(changes, line.category().label(), line.line().amount(), value);
            draft = draft.withSpending(line.category().id(), value);
        }
        String version = Long.toString(view.version());
        String catalogVersion = Long.toString(view.catalog().revision().version());
        boolean approved = version.equals(req.getParameter("version")) && catalogVersion.equals(req.getParameter("catalog"));
        // Catalog edits can rename or reuse a starter label. Let the owner confirm the mapping in that case.
        boolean mappingReview = view.catalog().revision().version() > 1 && !assigned.isEmpty();
        if (!unresolved.isEmpty() || !approved && (view.version() > 0 && !changes.isEmpty() || mappingReview)) {
            reply(res, 409, Map.of("version", version, "catalog", catalogVersion, "changes", changes,
                    "unresolved", unresolved, "options", options));
            return;
        }
        var command = new BudgetStore.SaveBudget(draft.month(), view.version(), view.catalog().revision().version(),
                draft.income().amount(), draft.spending(catalog).stream()
                .map(s -> new BudgetStore.SpendingValue(s.category().id(), s.line().amount())).toList(),
                draft.investment().amount());
        var result = service.save(command);
        if (result instanceof SaveResult.Saved<?> || result instanceof SaveResult.Unchanged<?>) {
            reply(res, 200, Map.of("saved", true));
        } else if (result instanceof SaveResult.Conflict<?>) {
            reply(res, 409, Map.of("message", "Месяц или категории изменились. Проверьте актуальные данные и повторите перенос."));
        } else if (result instanceof SaveResult.Invalid<?>) {
            reply(res, 400, Map.of("message", "Месяц не прошёл проверку. Данные сохранены в браузере."));
        } else {
            reply(res, 503, Map.of("message", "Сохранение не подтверждено. Откройте бюджет и проверьте историю. Месяц остался в браузере."));
        }
    }

    private static void difference(List<Difference> result, String label, BudgetAmount old, BudgetAmount next) {
        if (!old.equals(next)) result.add(new Difference(label, next.value().orElseThrow().toPlainString(),
                old.value().map(java.math.BigDecimal::toPlainString).orElse("")));
    }

    private static String required(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        if (value == null || value.length() > 80) throw new IllegalArgumentException();
        return value;
    }
    private static BudgetAmount amount(HttpServletRequest req, String name) { return parseAmount(required(req, name)); }
    private static void reply(HttpServletResponse res, int status, Object data) throws IOException {
        res.setStatus(status);
        res.getWriter().write(JSON.writeValueAsString(data));
    }
}
