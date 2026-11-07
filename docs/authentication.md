# Configured account and session boundary

Implementation brief for [app #4](https://github.com/finance-lab-project/app/issues/4),
selected on 2026-11-06 before implementation.

Use Jetty 12.1.14's `LoginService` and `FormAuthenticator` with Servlet
`request.login`/`logout`. A small server-classpath adapter loads the configured
account and verifies Argon2id using Password4j 1.8.4. Disable Jetty's separate
`j_security_check` entry point so all browser login mutations pass through the
application's CSRF checks. Cache the container identity without the password.
The WAR stays an ordinary Vaadin production WAR; no Spring or identity service.

Protect every request by default, with only the exact login endpoint anonymous.
The plain Russian HTML login form needs no anonymous Vaadin resources. Existing
Vaadin request/CSRF protection stays enabled. The Servlet principal is the stable
owner UUID and supplies identity to server-side business services.

One owner-only configuration file supplies `username`, `password-hash` and
`owner-id`. Missing/invalid configuration rejects startup. No bootstrap bypass,
default account, registration, recovery endpoint, or client-selected owner.
Use cookies only, `Secure`, `HttpOnly`, `SameSite=Lax`, a 30-minute idle limit
and an eight-hour absolute authenticated lifetime. Shorter operator-set limits
are supported. Require a configured HTTPS public origin and secure requests.
Only a loopback reverse proxy may supply forwarded headers.

Serialize and rate-limit verification globally for this one account: five failed
attempts in a minute cause a one-minute cooldown, regardless of username, IP,
forwarded headers or browser session. This deliberately trades availability
under attack for bounded guessing and password-hash resource use.

Credential rotation/recovery is an operator action: stop the JVM, replace the
protected configuration while retaining the owner UUID, restart without session
persistence. This invalidates every existing session and preserves data ownership.

References reviewed: [Jetty security integration](https://jetty.org/docs/jetty/12.1/programming-guide/security/index.html),
[Vaadin plain Java security](https://vaadin.com/docs/latest/flow/security/advanced-topics/securing-plain-java-app),
[Password4j](https://github.com/Password4j/password4j), and
[OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Verification must exercise the packaged WAR and container with synthetic account
configuration: login, logout, expiry, unauthorized URLs/requests, server owner
identity, CSRF, throttling and restart rotation. Proxy-header simulation and
cookie-attribute inspection do not establish real TLS/proxy deployment behavior;
record that remaining deployment check separately.

## Provisioning, rotation and recovery

Build the reactor first. In an interactive operator terminal, use the offline
tool to create a **new**, uncommitted account file. The only command argument is
the destination; the tool prompts for account values and reads the password
without echo. Use a private directory owned by the runtime operator. Retain a
protected off-VM copy of the file and its stable owner UUID.

```sh
umask 077
java -cp "authentication/target/authentication-0.1.0.jar:authentication/target/lib/*:$JETTY_HOME/lib/*:$JETTY_HOME/lib/logging/*" \
  org.financelab.auth.AccountFileTool /run/finance-lab/account.next.properties
```

For initial provisioning, generate a fresh UUID using the operator's UUID tool.
For rotation or recovery, read the existing owner UUID from the protected copy
and enter **that same UUID**. The tool uses Argon2id v19, 19 MiB, two iterations,
one lane, a random 16-byte salt and a 32-byte hash. The loader accepts 19–64 MiB,
two–six iterations and one lane; benchmark within those bounds on the target VM.
File permission must be `0600` or `0400`, and the file itself cannot be a symlink.
Do not put its values in shell arguments, environment variables, tickets or logs.

1. Stop the application JVM and confirm its listeners have stopped.
2. Create/validate the replacement as above, preserving `owner-id`; atomically
   replace the protected file and update its protected recovery copy.
3. Start a fresh JVM using the configuration below. The supplied launcher uses
   in-memory sessions only. Do not enable Jetty session persistence.
4. Verify the new credential works, the old one fails, old cookies are rejected,
   and the existing owner's data remains accessible. A lost password uses this
   same operator procedure; there is no public reset endpoint.

```sh
export APP_ACCOUNT_FILE=/run/finance-lab/account.properties
export APP_PUBLIC_ORIGIN=https://finance.example.invalid
scripts/run.sh
```

`APP_PUBLIC_ORIGIN` is the exact HTTPS origin, without a trailing slash/path.
`APP_DISPLAY_ZONE` defaults to `Europe/Moscow`. Optional
`APP_SESSION_IDLE_SECONDS` (1–1800) and `APP_SESSION_ABSOLUTE_SECONDS` (1–28800)
can shorten session lifetimes. `APP_MODE`, including the former `bootstrap`
mode, is rejected. The account adapter lives in `authentication/target/` and its
`lib/` directory on Jetty's server classpath; ship these alongside the WAR and
`config/jetty-auth.xml`. No account values are embedded in these artifacts.

The backend binds only to `127.0.0.1`. Terminate HTTPS at the trusted local proxy;
overwrite, rather than append untrusted `Forwarded`/`X-Forwarded-*` headers. Strip
client-supplied forwarded metadata and supply the real HTTPS scheme/host. Block
external access to the backend port. Plain HTTP requests without trusted secure
scheme metadata receive 403. Use the configured origin for the proxy host and
restrict hostnames there. Keep request-body, query-string and cookie logging off
for authentication. No username, hash, password, owner ID or token is logged by
the application. A failed login always uses the same generic Russian response;
the global cooldown can also reject the legitimate account for one minute.

## Verification boundary

`scripts/verify.sh` uses only generated synthetic account files and a disposable
self-signed localhost certificate. It checks production Vaadin login/events/
reload/logout, Servlet endpoint gates, cookie attributes, CSRF, throttle bypass
attempts, session ID rotation, stale cookies after restart/credential rotation,
and shortened idle/absolute expiry. The verifier clock is controlled only in
unit tests; runtime throttling has no test override. The test TLS connector uses
a private temporary Jetty base removed on exit. The verifier also removes its
test keystore and checks that normal startup still succeeds with valid account
configuration.

Business modules are not assembled yet: the owner test verifies the trusted
identity seam against a foreign browser owner value; actual owner-scoped
database reads/writes remain part of Budget integration. Real certificate trust,
public proxy/header sanitization, network isolation, and deployed HTTPS behavior
must be checked before deployment. No deployment is performed by these checks.
