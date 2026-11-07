const prefix = "finance-lab.guest.v1.";
export const validMonth = value => /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(value);
export const key = month => prefix + month;
export function months() {
  return Object.keys(localStorage).filter(k => k.startsWith(prefix))
    .map(k => k.slice(prefix.length)).filter(validMonth).sort();
}

// Work in integer kopecks across the full NUMERIC(19,2) range.
export function kopecks(raw) {
  if (typeof raw !== "string" || raw.length > 80) throw Error("Проверьте сумму.");
  const value = raw.replace(/^[ \u00a0\u202f]+|[ \u00a0\u202f]+$/gu, "");
  if (!value) return null;
  if (!/^(?:(?:[0-9]+|[0-9]{1,3}(?:[ \u00a0\u202f][0-9]{3})+)(?:[.,][0-9]{1,2})?|[.,][0-9]{1,2})$/u.test(value)
      || /[\r\n]/u.test(value)) throw Error("Введите сумму с точностью до копеек.");
  const [whole, fraction = ""] = value.replace(/[ \u00a0\u202f]/gu, "").split(/[.,]/u);
  const amount = BigInt(whole || "0") * 100n + BigInt(fraction.padEnd(2, "0"));
  if (amount > 9999999999999999999n) throw Error("Сумма превышает допустимый предел.");
  return amount;
}
export function money(amount) {
  const absolute = amount < 0n ? -amount : amount;
  return (amount < 0n ? "−" : "") + (absolute / 100n).toLocaleString("ru-RU") + ","
    + String(absolute % 100n).padStart(2, "0") + "\u00a0₽";
}
export function decode(raw, count) {
  const data = JSON.parse(raw);
  if (!data || !Array.isArray(data.spending) || data.spending.length !== count)
    throw Error("Не удалось прочитать сохранённый месяц.");
  [data.income, data.investment, ...data.spending].forEach(kopecks);
  return data;
}
// Serialize changes to the same month between tabs, including a confirmed transfer.
export async function replaceMonth(month, expected, replacement) {
  return navigator.locks.request(key(month), () => {
    if (localStorage.getItem(key(month)) !== expected) return false;
    if (replacement === null) localStorage.removeItem(key(month));
    else localStorage.setItem(key(month), replacement);
    return true;
  });
}
