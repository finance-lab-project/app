package org.financelab.app;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;

@Route("")
public class BootstrapView extends Div {
    public BootstrapView() {
        Span status = new Span("Готово");
        status.setId("status");
        add(new H1("Finance Lab"), new Span("Начальная сборка приложения"),
                new Button("Проверить соединение", event -> status.setText("Соединение работает")), status);
    }
}
