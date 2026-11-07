package org.financelab.app;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.financelab.budget.adapter.ui.BudgetPlanner;

import java.io.IOException;

/** Shared planner markup. Anonymous documents do not create sessions or read account budgets. */
public final class AppPages extends HttpServlet {
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static final java.util.Set<String> PUBLIC_ASSETS = java.util.Set.of("/local.js", "/store.js", "/notice.js",
            "/login.js", "/local-months.js", "/shell.css", "/planner.css");

    static String frame(String header, String body) {
        return """
                <div class="app-frame"><header class="appbar"><nav aria-label="Основная навигация">
                <a class="brand" router-ignore href="./">Finance Lab</a>%s</nav></header>%s
                <footer><a router-ignore href="privacy">Данные и cookie</a></footer>
                <aside id="cookie-notice" class="cookie-notice" aria-label="Уведомление о cookie" hidden>
                <div><p>Для входа нужен технический cookie. Рекламы и аналитики нет.
                <a router-ignore href="privacy">Подробнее о данных</a></p>
                <button type="button" class="secondary" id="close-notice">Закрыть</button></div></aside></div>"""
                .formatted(header, body);
    }

    static void page(HttpServletResponse response, String title, String header, String body, String script)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Content-Language", "ru");
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
                        + "form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        response.getWriter().printf("""
                <!doctype html><html lang="ru"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s · Finance Lab</title><link rel="stylesheet" href="ui/shell.css"><link rel="stylesheet" href="ui/planner.css"></head>
                <body>%s<script type="module" src="ui/notice.js"></script>%s</body></html>
                """, title, frame(header, body), script.isEmpty() ? "" : "<script type=\"module\" src=\"ui/" + script + ".js\"></script>");
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/ui".equals(request.getServletPath())) {
            String file = request.getPathInfo();
            if (file == null || !(PUBLIC_ASSETS.contains(file) || file.equals("/transfer.js"))) {
                response.sendError(404); return;
            }
            response.setContentType(file.endsWith(".css") ? "text/css;charset=UTF-8" : "text/javascript;charset=UTF-8");
            try (var stream = getServletContext().getResourceAsStream("/ui" + file)) {
                if (stream == null) response.sendError(404);
                else stream.transferTo(response.getOutputStream());
            }
            return;
        }
        if ("/privacy".equals(request.getServletPath())) {
            page(response, "Данные и cookie", "", """
                    <main class="reading"><h1>Данные и cookie</h1>
                    <h2>Бюджет в браузере</h2>
                    <p>Когда вы нажимаете «Сохранить», в гостевом режиме месяцы, версии и категории остаются в хранилище этого браузера.
                    Их могут увидеть другие люди с доступом к этому профилю. Удалить локальный месяц можно
                    ниже. Очистка данных сайта в браузере удалит все его локальные месяцы.</p>
                    <h2>Вход и перенос</h2>
                    <p>При открытии формы входа сайт устанавливает технический cookie JSESSIONID.
                    Он нужен для защиты формы и поддержания сеанса. В cookie хранится идентификатор сеанса.
                    При выходе сервер завершает сеанс и удаляет cookie. Рекламные и аналитические cookie не используются. Отметка о закрытии уведомления хранится в этом браузере.</p>
                    <p>После входа сохранённые месяцы из этого браузера можно перенести в личный аккаунт.
                    Подтверждённые суммы и история изменений сохраняются в базе данных. Они доступны
                    из других браузеров после входа. Выход из аккаунта сохраняет эти данные на сервере.</p>
                    <h2>Личный аккаунт</h2>
                    <p>Облачный бюджет предназначен для одного владельца. Для входа используются имя пользователя,
                    защищённый хеш пароля и внутренний идентификатор. Соединение защищено HTTPS.
                    При обычном открытии страниц сервер также получает сетевой адрес и технические сведения о запросе.</p>
                    %s%s<p><a href="./">К бюджету</a></p></main>"""
                    .formatted(BudgetPlanner.localMonthControls(), request.getServletContext().getAttribute("privacyPolicy")), "local-months");
            return;
        }
        if (request.getUserPrincipal() != null) {
            response.setStatus(303); response.setHeader("Location", request.getContextPath() + "/"); return;
        }
        page(response, "Бюджет на месяц", "<a data-save-before-leave=\"Сохранить и войти\" class=\"button secondary\" href=\"login\">Войти</a>",
                BudgetPlanner.month(false), "local");
    }

}
