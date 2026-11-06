package org.financelab.app;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Default-deny boundary for views, UIDL, history, downloads and future endpoints. */
public final class AuthenticationFilter implements Filter {
    static final String AUTHENTICATED_AT = AuthenticationFilter.class.getName() + ".authenticatedAt";

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain) throws IOException, ServletException {
        var request = (HttpServletRequest) input;
        var response = (HttpServletResponse) output;
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // no-referrer makes native form POSTs send Origin: null.
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("X-Frame-Options", "DENY");
        if (!request.isSecure()) { response.sendError(403); return; }
        var config = (ApplicationConfig) request.getServletContext().getAttribute(ApplicationLifecycle.CONFIG_ATTRIBUTE);
        var session = request.getSession(false);
        if (session != null) {
            var loggedInAt = session.getAttribute(AUTHENTICATED_AT);
            if (System.currentTimeMillis() - session.getLastAccessedTime() >= config.idleSeconds() * 1000L
                    || loggedInAt instanceof Long since && System.currentTimeMillis() - since >= config.absoluteSeconds() * 1000L) {
                request.logout();
                session.invalidate();
                session = null;
            }
        }
        // Compare the raw URI: path parameters, aliases and encoded variants are not anonymous.
        if (request.getRequestURI().equals(request.getContextPath() + "/login")) {
            chain.doFilter(request, response);
            return;
        }
        if (session == null || session.getAttribute(AUTHENTICATED_AT) == null || request.getUserPrincipal() == null) {
            if ("GET".equals(request.getMethod()) && request.getQueryString() == null
                    && "document".equals(request.getHeader("Sec-Fetch-Dest"))) {
                response.setStatus(303);
                response.setHeader("Location", request.getContextPath() + "/login");
            } else {
                response.sendError(401);
            }
            return;
        }
        OwnerIdentity.require(request);
        chain.doFilter(request, response);
    }
}
