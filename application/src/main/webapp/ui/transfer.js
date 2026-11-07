import {key, months, decode, replaceMonth, money, kopecks} from "./store.js";

const list = document.querySelector("#months"), status = document.querySelector("#status");
const send = document.querySelector("#send"), count = Number(document.querySelector("#transfer").dataset.count);
const elem = (tag, text) => { const el = document.createElement(tag); el.textContent = text; return el; };
const price = value => value === "" ? "не задано" : money(kopecks(value));
const pending = [];
try {
  for (const month of months()) {
    const raw = localStorage.getItem(key(month));
    const row = elem("section", ""), title = elem("h2", new Intl.DateTimeFormat("ru-RU",
      {month:"long",year:"numeric",timeZone:"UTC"}).format(new Date(month + "-01T00:00:00Z")));
    const detail = elem("div", "");
    row.className = "surface transfer-month"; row.append(title, detail); list.append(row);
    try { pending.push({month, raw, draft: decode(raw, count), detail, mappings: {}}); }
    catch { detail.textContent = "Формат месяца не распознан. Локальная копия сохранена."; }
  }
  if (!list.children.length) window.location.replace("./");
  else send.hidden = !pending.length;
} catch { status.textContent = "Хранилище браузера недоступно. Вы можете открыть бюджет аккаунта."; }

async function transfer(item, decision = {}) {
  const {month, raw, draft, detail} = item;
  const params = new URLSearchParams({month, income:draft.income, investment:draft.investment, ...item.mappings, ...decision});
  draft.spending.forEach((value, i) => params.set("spend-" + i, value));
  try {
    if (localStorage.getItem(key(month)) !== raw) throw Error("Месяц изменился в другой вкладке. Откройте перенос заново.");
    const response = await fetch("transfer", {method:"POST", body:params,
      headers:{"X-CSRF-Token":document.getElementById("csrf").value}, redirect:"error"});
    if (response.status === 401 || response.status === 403) throw Error("Сеанс завершён. Войдите снова. Локальная копия сохранена.");
    const reply = await response.json();
    detail.replaceChildren();
    if (response.ok && reply.saved) {
      detail.textContent = await replaceMonth(month, raw, null) ? "Сохранено в аккаунте."
        : "Сохранено в аккаунте. В браузере осталась более новая версия.";
    } else if (response.status === 409 && reply.version !== undefined) {
      detail.append(elem("p", "Проверьте соответствие категорий и суммы. Переносятся только заполненные поля."));
      for (const change of reply.changes) detail.append(elem("p", change.label + " · в аккаунте " + price(change.cloud)
          + " → из браузера " + price(change.local)));
      const fields = [];
      for (const [name, label] of Object.entries(reply.unresolved)) {
        const select = document.createElement("select");
        select.append(new Option("Выберите категорию", ""));
        for (const opt of reply.options) select.append(new Option(opt.label, opt.id));
        const field = elem("label", label); field.append(select); detail.append(field); fields.push([name, select]);
      }
      const apply = elem("button", "Подтвердить перенос"); apply.className = "secondary";
      apply.onclick = async () => {
        for (const [name, field] of fields) {
          if (!field.value) { field.focus(); return; }
          item.mappings[name] = field.value;
        }
        apply.disabled = true;
        await transfer(item, fields.length ? {} : {version:reply.version, catalog:reply.catalog});
      };
      detail.append(apply);
    } else throw Error(reply.message || "Перенос не подтверждён. Локальная копия сохранена.");
  } catch (err) {
    detail.textContent = err instanceof TypeError ? "Не удалось связаться с сервером. Локальная копия сохранена." : err.message;
  }
}
send.onclick = async () => {
  send.disabled = true;
  status.textContent = "Переносим выбранные месяцы…";
  for (const item of pending) await transfer(item);
  status.textContent = "Откройте бюджет или проверьте сообщения у каждого месяца.";
  send.hidden = true;
};
