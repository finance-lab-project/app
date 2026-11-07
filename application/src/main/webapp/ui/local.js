import {key, validMonth, decode, kopecks, money, replaceMonth} from "./store.js";

const $ = selector => document.querySelector(selector);
const monthInput = $("#month");
const form = $("#plan");
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
  month = next; monthInput.value = next;
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
form.addEventListener("input", () => { totals(false); if (readable) status(dirty() ? "Есть несохранённые изменения." : "Изменений нет."); });
form.addEventListener("submit", async event => {
  event.preventDefault();
  if (!readable || !totals(true)) { if (readable) status("Проверьте отмеченные суммы."); return; }
  const selected = month, next = JSON.stringify(draft()), before = original;
  try {
    if (!await replaceMonth(selected, before, next)) { status("Месяц изменён в другой вкладке. Ваш ввод остался на экране. Откройте сохранённую версию перед повторным сохранением."); return; }
    if (month === selected) { original = next; baseline = next; $("#remove").hidden = false; status("Сохранено в этом браузере."); }
  } catch { status("Не удалось сохранить месяц в браузере. Проверьте разрешения и свободное место."); }
});
monthInput.addEventListener("change", () => {
  if (!validMonth(monthInput.value) || (dirty() && !confirm("Сменить месяц и отменить несохранённые изменения?"))) monthInput.value = month;
  else loadMonth(monthInput.value);
});
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
