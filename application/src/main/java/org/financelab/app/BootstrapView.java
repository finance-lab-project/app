package org.financelab.app;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.router.Route;

@Route("")
public class BootstrapView extends Div {
    public BootstrapView() {
        OwnerIdentity.requireCurrent();
        Span status = new Span("Готово");
        status.setId("status");
        add(new H1("Finance Lab"), new Span("Начальная сборка приложения"),
                new Button("Проверить соединение", event -> {
                    OwnerIdentity.requireCurrent();
                    status.setText("Соединение работает");
                }), status);
        var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        var logout = new Element("form");
        logout.setAttribute("method", "post");
        logout.setAttribute("action", request.getContextPath() + "/logout");
        var csrf = new Input();
        csrf.setType("hidden");
        csrf.getElement().setAttribute("name", "csrf");
        csrf.setValue(AccountServlet.csrf(request.getSession()));
        var submit = new NativeButton("Выйти");
        submit.getElement().setAttribute("type", "submit");
        logout.appendChild(csrf.getElement(), submit.getElement());
        getElement().appendChild(logout);
    }
}
