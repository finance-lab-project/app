package org.financelab.app;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.financelab.budget.domain.Category;
import org.financelab.budget.domain.CategoryCatalog;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** These public documents never read account data or create a session. */
public final class PlannerPages extends HttpServlet {
    static final List<String> STARTERS = CategoryCatalog.initialize(new UUID(0, 0), Optional.empty())
            .categories().stream().map(Category::label).toList();

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
                <title>%s · Finance Lab</title><link rel="stylesheet" href="ui/planner.css"></head>
                <body><header class="appbar"><nav aria-label="Основная навигация">
                <a class="brand" href="./">Finance Lab</a>%s</nav></header>%s
                <footer><a href="privacy">Данные и cookie</a></footer>%s</body></html>
                """, title, header, body, script.isEmpty() ? "" : "<script type=\"module\" src=\"ui/" + script + ".js\"></script>");
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/privacy".equals(request.getServletPath())) {
            page(response, "Данные и cookie", "", """
                    <main class="reading"><h1>Данные и cookie</h1>
                    <h2>Бюджет в браузере</h2>
                    <p>Когда вы нажимаете «Сохранить», месяцы и суммы остаются в хранилище этого браузера.
                    Их могут увидеть другие люди с доступом к этому профилю. Удалить локальный месяц можно
                    в планировщике. Очистка данных сайта в браузере удалит все его локальные месяцы.</p>
                    <h2>Вход и перенос</h2>
                    <p>При открытии формы входа сайт устанавливает технический cookie JSESSIONID.
                    Он нужен для защиты формы и поддержания сеанса. В cookie хранится идентификатор сеанса.
                    При выходе сервер завершает сеанс и удаляет cookie. Рекламные и аналитические cookie не используются.</p>
                    <p>После входа сохранённые месяцы из этого браузера можно перенести в личный аккаунт.
                    Подтверждённые суммы и история изменений сохраняются в базе данных. Они доступны
                    из других браузеров после входа. Выход из аккаунта сохраняет эти данные на сервере.</p>
                    <h2>Личный аккаунт</h2>
                    <p>Облачный бюджет предназначен для одного владельца. Для входа используются имя пользователя,
                    защищённый хеш пароля и внутренний идентификатор. Соединение защищено HTTPS.
                    При обычном открытии страниц сервер также получает сетевой адрес и технические сведения о запросе.</p>
                    %s<p><a href="./">К бюджету</a></p></main>"""
                    .formatted(request.getServletContext().getAttribute("privacyPolicy")), "");
            return;
        }
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < STARTERS.size(); i++) fields.append(amount("spend-" + i, STARTERS.get(i)));
        boolean signedIn = request.getUserPrincipal() != null;
        String action = signedIn ? "<a class=\"button secondary\" href=\"transfer\">Перенести в аккаунт</a>"
                : "<a class=\"button secondary\" href=\"login\">Войти</a>";
        page(response, "План месяца", action, """
                <main class="planner"><div class="page-title"><div><h1>Бюджет на месяц</h1>
                <p>Сохранение в этом браузере</p></div><label class="month">Месяц
                <input id="month" type="month" required min="0001-01" max="9999-12"></label></div>
                <noscript>Для сохранения в браузере включите JavaScript.</noscript>
                <div class="planner-grid"><aside class="summary" aria-labelledby="balance-label">
                <h2 id="balance-label">Остаток</h2><output id="balance">—</output>
                <dl><dt>Доход</dt><dd id="income-total">—</dd><dt>Расходы</dt><dd id="spending-total">—</dd>
                <dt>Инвестиции</dt><dd id="investment-total">—</dd></dl></aside>
                <form id="plan" class="surface" novalidate><h2>Доход и инвестиции</h2><div class="field-grid">%s%s</div>
                <h2>Расходы</h2><div class="field-grid">%s</div>
                <div class="actions"><button id="save" class="primary" type="submit" disabled>Сохранить</button>
                <button type="button" id="remove" class="subtle" hidden>Удалить месяц</button></div>
                <p id="status" role="status" aria-live="polite"></p></form></div></main>"""
                .formatted(amount("income", "Доход"), amount("investment", "Инвестиции"), fields), "local");
    }

    private static String amount(String id, String label) {
        return """
                <label class="amount" for="%1$s">%2$s<span class="amount-box">
                <input id="%1$s" name="%1$s" type="text" inputmode="decimal" autocomplete="off" maxlength="80"
                aria-describedby="%1$s-error"><span aria-hidden="true">₽</span></span>
                <small id="%1$s-error"></small></label>""".formatted(id, escape(label));
    }
}
