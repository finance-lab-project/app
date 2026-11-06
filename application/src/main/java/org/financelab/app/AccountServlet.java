package org.financelab.app;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Plain forms keep all application resources behind the authentication boundary. */
public final class AccountServlet extends HttpServlet {
    private static final String CSRF = AccountServlet.class.getName() + ".csrf";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/login".equals(request.getServletPath())) { response.sendError(405); return; }
        if (request.getUserPrincipal() != null) { redirect(request, response, "/"); return; }
        form(request, response, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        var session = request.getSession(false);
        var config = (ApplicationConfig) getServletContext().getAttribute(ApplicationLifecycle.CONFIG_ATTRIBUTE);
        String token = request.getParameter("csrf");
        if (session == null || token == null || token.length() > 100
                || !(session.getAttribute(CSRF) instanceof String expected)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))
                || request.getHeader("Origin") != null && !config.publicOrigin().equals(request.getHeader("Origin"))) {
            response.sendError(403);
            return;
        }
        if ("/logout".equals(request.getServletPath())) {
            request.logout();
            session.invalidate();
            // Same attributes as the live cookie; remove it from the browser too.
            response.addHeader("Set-Cookie", "JSESSIONID=; Path=" + cookiePath(request)
                    + "; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
            redirect(request, response, "/login");
            return;
        }
        if (request.getUserPrincipal() != null) { response.sendError(409); return; }
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || username.length() > 128 || password == null || password.length() > 1024) {
            response.sendError(400);
            return;
        }
        try {
            request.login(username, password);
        } catch (ServletException ignored) {
            response.setStatus(401);
            form(request, response, true);
            return;
        }
        request.changeSessionId();
        session.setMaxInactiveInterval(config.idleSeconds());
        session.setAttribute(AuthenticationFilter.AUTHENTICATED_AT, System.currentTimeMillis());
        session.setAttribute(CSRF, newToken());
        redirect(request, response, "/");
    }

    static String csrf(HttpSession session) {
        var token = session.getAttribute(CSRF);
        if (token == null) { token = newToken(); session.setAttribute(CSRF, token); }
        return (String) token;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String cookiePath(HttpServletRequest request) {
        return request.getContextPath().isEmpty() ? "/" : request.getContextPath();
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path) {
        response.setStatus(303);
        response.setHeader("Location", request.getContextPath() + path);
    }

    private static void form(HttpServletRequest request, HttpServletResponse response, boolean error) throws IOException {
        var session = request.getSession(true);
        var config = (ApplicationConfig) request.getServletContext().getAttribute(ApplicationLifecycle.CONFIG_ATTRIBUTE);
        session.setMaxInactiveInterval(config.idleSeconds());
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        response.getWriter().printf("""
                <!doctype html><html lang="ru"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Вход — Finance Lab</title><h1>Вход в Finance Lab</h1>
                %s<form method="post">
                <input type="hidden" name="csrf" value="%s">
                <p><label>Имя пользователя <input name="username" autocomplete="username" maxlength="128" required></label></p>
                <p><label>Пароль <input type="password" name="password" autocomplete="current-password" maxlength="1024" required></label></p>
                <button type="submit">Войти</button></form></html>
                """, error ? "<p role=\"alert\">Не удалось войти. Проверьте данные или повторите попытку позже.</p>" : "", csrf(session));
    }
}
