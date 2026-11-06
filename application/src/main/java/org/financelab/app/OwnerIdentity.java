package org.financelab.app;

import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Server-only identity seam for owner-scoped business services. */
public final class OwnerIdentity {
    private OwnerIdentity() { }

    public static UUID requireCurrent() {
        var request = VaadinServletRequest.getCurrent();
        if (request == null) throw new SecurityException("Authentication required");
        return require(request.getHttpServletRequest());
    }

    public static UUID require(HttpServletRequest request) {
        if (request.getUserPrincipal() == null || !request.isUserInRole("owner")) {
            throw new SecurityException("Authentication required");
        }
        return UUID.fromString(request.getUserPrincipal().getName());
    }
}
