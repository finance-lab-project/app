const dismissed = "finance-lab.cookie-notice.dismissed.v1";

export function showNotice(root) {
  const notice = root.querySelector("#cookie-notice");
  if (!notice || notice.dataset.ready) return;
  notice.dataset.ready = "true";
  const frame = notice.closest(".app-frame");
  let closed = false;
  const read = () => {
    try { return localStorage.getItem(dismissed) === "1"; } catch { return false; }
  };
  const layout = () => {
    notice.hidden = closed || read();
    frame.style.setProperty("--notice-space", notice.hidden ? "0px" : notice.offsetHeight + "px");
  };
  notice.querySelector("button").onclick = () => {
    closed = true;
    try { localStorage.setItem(dismissed, "1"); } catch { /* Dismiss for this page if storage is blocked. */ }
    layout();
  };
  new ResizeObserver(layout).observe(notice);
  window.addEventListener("storage", e => { if (e.key === dismissed || e.key === null) layout(); });
  layout();
}
showNotice(document);
