package org.financelab.auth;

import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.security.authentication.SessionAuthentication;

/** The only login entry point is the CSRF-protected Servlet form. */
public final class ProgrammaticFormAuthenticator extends FormAuthenticator {
    public ProgrammaticFormAuthenticator() { super("/login", "/login", false); }

    @Override public boolean isJSecurityCheck(String uri) { return false; }

    @Override
    public SessionAuthentication newSessionAuthentication(String method, UserIdentity user, Object credentials) {
        // No plaintext password in an authenticated session, even in memory.
        return super.newSessionAuthentication(method, user, null);
    }
}
