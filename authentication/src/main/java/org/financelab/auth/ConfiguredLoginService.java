package org.financelab.auth;

import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Session;

import javax.security.auth.Subject;
import java.security.Principal;
import java.time.Clock;
import java.util.function.Function;

/** One JVM-wide account and failure budget, independent of attacker-supplied names/IPs. */
public final class ConfiguredLoginService implements LoginService {
    private final ConfiguredAccount account;
    private final Clock clock;
    private final Principal principal;
    private IdentityService identityService = new DefaultIdentityService();
    private int failures;
    private long windowStart;
    private long blockedUntil;

    public ConfiguredLoginService() { this(ConfiguredAccount.load(System.getenv()), Clock.systemUTC()); }

    ConfiguredLoginService(ConfiguredAccount account, Clock clock) {
        this.account = account;
        this.clock = clock;
        this.principal = () -> account.ownerId().toString();
    }

    @Override public String getName() { return "FinanceLab"; }
    @Override public IdentityService getIdentityService() { return identityService; }
    @Override public void setIdentityService(IdentityService service) { identityService = service; }
    @Override public void logout(UserIdentity user) { }
    @Override public boolean validate(UserIdentity user) {
        // A deserialized identity or identity from an earlier JVM cannot survive rotation.
        return user != null && user.getUserPrincipal() == principal;
    }

    @Override
    public synchronized UserIdentity login(String username, Object credentials, Request request,
                                           Function<Boolean, Session> getSession) {
        long now = clock.millis();
        if (now < blockedUntil) return null;
        if (now - windowStart >= 60_000) { failures = 0; windowStart = now; }
        if (!account.verifies(username, credentials)) {
            if (++failures >= 5) blockedUntil = now + 60_000;
            return null;
        }
        failures = 0;
        var subject = new Subject();
        subject.getPrincipals().add(principal);
        subject.setReadOnly();
        return identityService.newUserIdentity(subject, principal, new String[]{"owner"});
    }
}
