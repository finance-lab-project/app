package org.financelab.app;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.financelab.budget.adapter.ui.BudgetPlanner;
import org.financelab.budget.adapter.ui.PlannerTransfer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/** Import one explicitly selected local month using the existing versioned Budget save. */
public final class TransferServlet extends HttpServlet {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AppPages.page(response, "Перенос в аккаунт", "<a href=\"./\">Мой бюджет</a>",
                BudgetPlanner.transfer(AccountServlet.csrf(request.getSession())), "transfer");
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
            var service = ApplicationLifecycle.service(request, () -> OwnerIdentity.require(request));
            var result = PlannerTransfer.submit(service, request.getParameterMap());
            reply(response, result.status(), result.body());
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            reply(response, 400, Map.of("message", "Проверьте суммы и выбранные категории. Месяц остался в браузере."));
        } catch (RuntimeException e) {
            getServletContext().log("Budget transfer could not be confirmed");
            reply(response, 503, Map.of("message", "Результат переноса не подтверждён. Проверьте месяц в аккаунте. Локальная копия сохранена."));
        }
    }

    private static void reply(HttpServletResponse res, int status, Object data) throws IOException {
        res.setStatus(status);
        res.getWriter().write(JSON.writeValueAsString(data));
    }
}
