package org.financelab.spike;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;

/** Synthetic compatibility probe; no application features or authentication. */
@Route("")
public class SmokeView extends Div {
    public SmokeView() {
        Span result = new Span("Ready");
        result.setId("result");
        add(new H1("Runtime compatibility spike"),
                new Button("Round trip", event -> result.setText("Server round trip OK")),
                result);
    }
}
