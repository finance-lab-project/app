package org.financelab.app;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import org.financelab.budget.application.BudgetService;
import tools.jackson.databind.json.JsonMapper;

@Route("")
@PageTitle("Бюджет на месяц · Finance Lab")
@StyleSheet("context://ui/planner.css")
public final class BootstrapView extends Div {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final BudgetService service;

    public BootstrapView() {
        var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        service = ApplicationLifecycle.service(request, OwnerIdentity::requireCurrent);
        var actions = """
                <a id="account-action" router-ignore href="transfer">Из браузера</a>
                <form method="post" action="logout"><input type="hidden" name="csrf" value="%s">
                <button type="submit" class="secondary">Выйти</button></form>"""
                .formatted(AccountServlet.csrf(request.getSession()));
        add(new Html(PlannerPages.frame(actions, PlannerPages.planner(true))));
    }

    @Override protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        event.getUI().getPage().executeJs("document.documentElement.lang = 'ru'");
        getElement().executeJs("""
                const root = this;
                Promise.all([import(new URL('ui/local.js', document.baseURI)),
                             import(new URL('ui/notice.js', document.baseURI))]).then(([ui, notice]) => {
                  notice.showNotice(root);
                  ui.startPlanner(root, async (action, data) =>
                    JSON.parse(await root.$server.planner(action, JSON.stringify(data))));
                });
                """);
    }

    /** Vaadin checks session and CSRF before dispatch; owner identity is rechecked by the service. */
    @ClientCallable
    public String planner(String action, String payload) {
        try {
            return JSON.writeValueAsString(PlannerCommands.run(service, action, JSON.readTree(payload)));
        } catch (IllegalArgumentException | java.time.DateTimeException | tools.jackson.core.JacksonException invalid) {
            return "{\"error\":\"invalid\"}";
        } catch (RuntimeException failure) {
            // A disconnected client must not assume a write rolled back or retry it automatically.
            return "{\"error\":\"unknown\"}";
        }
    }
}
