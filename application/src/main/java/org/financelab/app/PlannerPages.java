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

/** Shared planner markup. Anonymous documents do not create sessions or read account budgets. */
public final class PlannerPages extends HttpServlet {
    static final List<String> STARTERS = CategoryCatalog.initialize(new UUID(0, 0), Optional.empty())
            .categories().stream().map(Category::label).toList();

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static final java.util.Set<String> PUBLIC_ASSETS = java.util.Set.of("/local.js", "/store.js", "/notice.js",
            "/login.js", "/privacy.js", "/planner.css");

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
                <title>%s · Finance Lab</title><link rel="stylesheet" href="ui/planner.css"></head>
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
                    <div class="actions"><label>Локальный месяц<select id="local-months"></select></label>
                    <button type="button" id="delete-local" class="subtle" disabled>Удалить месяц из браузера</button></div>
                    <p role="status" id="local-status"></p>%s<p><a href="./">К бюджету</a></p></main>"""
                    .formatted(request.getServletContext().getAttribute("privacyPolicy")), "privacy");
            return;
        }
        if (request.getUserPrincipal() != null) {
            response.setStatus(303); response.setHeader("Location", request.getContextPath() + "/"); return;
        }
        page(response, "Бюджет на месяц", "<a id=\"account-action\" class=\"button secondary\" href=\"login\">Войти</a>",
                planner(false), "local");
    }

    static String planner(boolean cloud) {
        var starters = new StringBuilder();
        for (String label : STARTERS) starters.append("<li>").append(escape(label)).append("</li>");
        return """
                <main class="planner" id="planner" data-mode="%s"><div class="page-title"><div><h1>Бюджет на месяц</h1>
                <p id="storage-mode">%s</p></div><div class="date-controls">
                <label class="month">Месяц<select id="month"></select></label>
                <label class="month">Год<input id="year" type="number" min="1" max="9999" required></label></div></div>
                <nav class="planner-nav" aria-label="Разделы бюджета">
                <button type="button" data-panel="month" aria-current="page">План месяца</button>
                <button type="button" data-panel="catalog">Категории</button>
                <button type="button" data-panel="history">История</button></nav>
                <noscript>Для работы с планировщиком включите JavaScript.</noscript>
                <ul hidden id="starter-labels">%s</ul>
                <div id="month-panel" class="planner-grid"><aside class="summary" aria-labelledby="balance-label">
                <h2 id="balance-label">Остаток</h2><output id="balance">—</output>
                <dl><dt>Доход</dt><dd id="income-total">—</dd><dt>Инвестиции</dt><dd id="investment-total">—</dd>
                <dt>Расходы</dt><dd id="spending-total">—</dd></dl></aside>
                <form id="plan" class="surface" novalidate><section aria-labelledby="income-heading">
                <h2 id="income-heading">Доход</h2>%s</section><section aria-labelledby="investment-heading">
                <h2 id="investment-heading">Инвестиции</h2>%s</section><section aria-labelledby="spending-heading">
                <h2 id="spending-heading">Расходы</h2><div id="spending" class="field-grid"></div></section>
                <div class="actions"><button id="save" class="primary" type="submit" disabled>Сохранить</button>
                <button id="clear" type="button" class="subtle">Очистить суммы</button></div></form></div>
                <section id="catalog-panel" class="surface" hidden><h2>Категории расходов</h2>
                <p class="helper">Порядок и названия общие для всех месяцев. Архивные строки останутся в сохранённых планах.</p>
                <div id="categories"></div><div class="actions"><button class="secondary" id="add-category" type="button">Добавить категорию</button>
                <button class="primary" id="save-catalog" type="button">Сохранить категории</button></div></section>
                <section id="history-panel" class="surface" hidden><h2>История месяца</h2>
                <label class="month">Сохранённая версия<select id="revision"></select></label>
                <div id="revision-detail"></div></section>
                <p id="status" role="status" aria-live="polite"></p>
                <button type="button" id="reload-plan" class="secondary" hidden>Загрузить сохранённые данные</button>
                <dialog id="leave-dialog"><h2>Несохранённые изменения</h2><p>Сохранить изменения перед переходом?</p>
                <form method="dialog" class="actions"><button value="stay" class="secondary" autofocus>Остаться</button>
                <button value="discard" class="subtle">Не сохранять</button><button value="save" class="primary">Сохранить</button></form></dialog></main>"""
                .formatted(cloud ? "cloud" : "local", cloud ? "Сохранение в аккаунте" : "Сохранение в этом браузере",
                        starters, amount("income", "Сумма дохода"), amount("investment", "Сумма инвестиций"));
    }

    private static String amount(String id, String label) {
        return """
                <label class="amount" for="%1$s">%2$s<span class="amount-box">
                <input id="%1$s" name="%1$s" type="text" inputmode="decimal" autocomplete="off" maxlength="80"
                aria-describedby="%1$s-error"><span aria-hidden="true">₽</span></span>
                <small id="%1$s-error"></small></label>""".formatted(id, escape(label));
    }
}
