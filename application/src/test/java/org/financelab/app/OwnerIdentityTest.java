package org.financelab.app;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnerIdentityTest {
    @Test void derivesOwnerOnlyFromContainerPrincipal() {
        var owner = UUID.fromString("10000000-0000-4000-8000-000000000001");
        assertEquals(owner, OwnerIdentity.require(request(() -> owner.toString(), true)));
        assertThrows(SecurityException.class, () -> OwnerIdentity.require(request(null, true)));
        assertThrows(SecurityException.class, () -> OwnerIdentity.require(request(() -> owner.toString(), false)));
        assertThrows(SecurityException.class, OwnerIdentity::requireCurrent);
    }
    private HttpServletRequest request(Principal principal, boolean role) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUserPrincipal" -> principal;
                    case "isUserInRole" -> role;
                    // A browser-supplied foreign owner must never select identity.
                    case "getParameter", "getHeader" -> "20000000-0000-4000-8000-000000000002";
                    default -> null;
                });
    }
}
