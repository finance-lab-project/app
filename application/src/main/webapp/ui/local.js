import {key, validMonth, decode, kopecks, money, replaceMonth} from "./store.js";

const $ = selector => document.querySelector(selector);
const monthInput = $("#month"), yearInput = $("#year");
const monthNames = new Intl.DateTimeFormat("ru-RU", {month:"long", timeZone:"UTC"});
for (let i = 0; i < 12; i++) {
  const label = monthNames.format(new Date(Date.UTC(2000, i, 15)));
  monthInput.add(new Option(label[0].toUpperCase() + label.slice(1), String(i + 1).padStart(2, "0")));
}
const form = $("#plan"), account = $("#account-action");
const accountLabel = account.textContent;
const fields = [...form.querySelectorAll("input")];
const spending = fields.filter(f => f.id.startsWith("spend-"));
const now = new Intl.DateTimeFormat("sv-SE", {timeZone:"Europe/Moscow", year:"numeric", month:"2-digit"}).format(new Date());
let month, original, baseline, readable = false;

function draft() {
  return {income: $("#income").value, investment: $("#investment").value, spending: spending.map(f => f.value)};
}
function status(text) { $("#status").textContent = text; }
function dirty() { return readable && JSON.stringify(draft()) !== baseline; }
function totals(report) {
  let invalid;
  const values = new Map();
  for (const field of fields) {
    const error = document.getElementById(field.id + "-error");
    try {
      values.set(field, kopecks(field.value) ?? 0n);
      error.textContent = ""; field.removeAttribute("aria-invalid");
    } catch (e) {
      invalid ??= field;
      if (report || field === document.activeElement || field.hasAttribute("aria-invalid")) {
        field.setAttribute("aria-invalid", "true"); error.textContent = e.message;
      }
    }
  }
  const spent = spending.reduce((sum, f) => sum + (values.get(f) ?? 0n), 0n);
  const income = values.get($("#income")) ?? 0n, investment = values.get($("#investment")) ?? 0n;
  for (const [id, amount] of [["balance", income - spent - investment], ["income-total", income],
      ["spending-total", spent], ["investment-total", investment]]) {
    document.getElementById(id).textContent = invalid ? "—" : money(amount);
  }
  $("#balance").classList.toggle("negative", !invalid && income - spent - investment < 0n);
  if (invalid && report) invalid.focus();
  return !invalid;
}
function loadMonth(next) {
  account.textContent = accountLabel;
  month = next;
  monthInput.value = next.slice(5);
  yearInput.value = String(Number(next.slice(0,4)));
  fields.forEach(f => { f.value = ""; f.removeAttribute("aria-invalid"); });
  readable = false;
  try {
    original = localStorage.getItem(key(month));
    if (original !== null) {
      const saved = decode(original, spending.length);
      $("#income").value = saved.income; $("#investment").value = saved.investment;
      spending.forEach((f, i) => f.value = saved.spending[i]);
    }
    baseline = JSON.stringify(draft()); readable = true;
    status(original === null ? "Пустой месяц. Сохраните план после ввода сумм." : "Сохранено в этом браузере.");
  } catch {
    status("Не удалось прочитать данные браузера. Разрешите локальное хранение или откройте другой месяц.");
  }
  $("#save").disabled = !readable;
  $("#remove").hidden = original === null || !readable;
  totals(false);
}
form.addEventListener("input", () => {
  totals(false);
  if (readable) {
    status(dirty() ? "Есть несохранённые изменения." : "Изменений нет.");
    account.textContent = dirty() ? (accountLabel === "Войти" ? "Сохранить и войти" : "Сохранить и перенести") : accountLabel;
  }
});
async function save() {
  if (!readable || !totals(true)) { if (readable) status("Проверьте отмеченные суммы."); return false; }
  const selected = month, next = JSON.stringify(draft()), before = original;
  try {
    if (!await replaceMonth(selected, before, next)) {
      status("Месяц изменён в другой вкладке. Ваш ввод остался на экране. Откройте сохранённую версию перед повторным сохранением.");
      return false;
    }
    if (month !== selected) return false;
    original = next; baseline = next; $("#remove").hidden = false;
    if (dirty()) { status("Есть новые несохранённые изменения."); return false; }
    account.textContent = accountLabel;
    status("Сохранено в этом браузере.");
    return true;
  } catch { status("Не удалось сохранить месяц в браузере. Проверьте разрешения и свободное место."); return false; }
}
form.addEventListener("submit", event => { event.preventDefault(); void save(); });
account.addEventListener("click", async event => {
  if (!dirty()) return;
  event.preventDefault();
  if (await save()) location.assign(account.href);
});
function selectMonth() {
  const next = yearInput.value.padStart(4, "0") + "-" + monthInput.value;
  if (!yearInput.validity.valid || !validMonth(next) || (dirty() && !confirm("Сменить месяц и отменить несохранённые изменения?"))) {
    monthInput.value = month.slice(5); yearInput.value = String(Number(month.slice(0,4)));
  } else loadMonth(next);
}
monthInput.addEventListener("change", selectMonth);
yearInput.addEventListener("change", selectMonth);
$("#remove").addEventListener("click", async () => {
  if (!confirm("Удалить этот месяц из браузера?")) return;
  try {
    if (await replaceMonth(month, original, null)) { loadMonth(month); status("Месяц удалён из браузера."); }
    else status("Месяц изменился в другой вкладке. Обновите страницу.");
  } catch { status("Не удалось удалить месяц."); }
});
window.addEventListener("beforeunload", e => { if (dirty()) { e.preventDefault(); e.returnValue = ""; } });
window.addEventListener("storage", e => {
  if (e.key === null || e.key === key(month)) {
    if (!dirty()) loadMonth(month);
    else status("Данные в другой вкладке изменились. Ваш ввод остался на экране.");
  }
});
loadMonth(now);
