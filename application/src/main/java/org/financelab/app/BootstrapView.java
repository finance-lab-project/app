package org.financelab.app;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import org.financelab.budget.adapter.ui.BudgetWorkspace;

@Route("")
@PageTitle("Мой бюджет · Finance Lab")
@StyleSheet("context://ui/planner.css")
public final class BootstrapView extends Div {
    public BootstrapView() {
        var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        var service = ApplicationLifecycle.service(request, OwnerIdentity::requireCurrent);
        setClassName("cloud");
        var header = new Element("header");
        header.setAttribute("class", "appbar");
        var nav = new Element("nav");
        nav.setAttribute("aria-label", "Основная навигация");
        var brand = new Anchor("./", "Finance Lab");
        brand.setClassName("brand");
        var transfer = new Anchor("transfer", "Из браузера");
        var logout = new Element("form");
        logout.setAttribute("method", "post");
        logout.setAttribute("action", request.getContextPath() + "/logout");
        var csrf = new Input();
        csrf.setType("hidden");
        csrf.getElement().setAttribute("name", "csrf");
        csrf.setValue(AccountServlet.csrf(request.getSession()));
        var submit = new NativeButton("Выйти");
        submit.setClassName("button secondary");
        submit.getElement().setAttribute("type", "submit");
        logout.appendChild(csrf.getElement(), submit.getElement());
        nav.appendChild(brand.getElement(), transfer.getElement(), logout);
        header.appendChild(nav);
        getElement().appendChild(header);
        var workspace = new BudgetWorkspace(service);
        workspace.addClassName("workspace");
        workspace.getElement().setAttribute("role", "main");
        add(workspace);
        var footer = new Element("footer");
        footer.appendChild(new Anchor("privacy", "Данные и cookie").getElement());
        getElement().appendChild(footer);
    }
}
