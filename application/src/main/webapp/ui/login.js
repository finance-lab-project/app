const password = document.querySelector("#password");
const visibility = document.querySelector("#visibility");
visibility.addEventListener("click", () => {
  const reveal = password.type === "password";
  password.type = reveal ? "text" : "password";
  visibility.textContent = reveal ? "Скрыть" : "Показать";
  visibility.setAttribute("aria-pressed", String(reveal));
});
