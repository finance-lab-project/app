package org.financelab.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationConfigTest {
    @Test
    void requiresHttpsOriginAndRejectsBootstrapBypass() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of()));
        for (String origin : new String[]{"http://example.invalid", "https://example.invalid/", "https://a:b@example.invalid", "https://example.invalid?q=x"}) {
            assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", origin)));
        }
        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", "https://example.invalid", "APP_MODE", "bootstrap")));
    }

    @Test
    void validatesDisplayZoneWithoutDependingOnHostLocale() {
        var defaults = ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", "https://example.invalid"));
        assertEquals("Europe/Moscow", defaults.displayZone().getId());
        assertEquals(1800, defaults.idleSeconds());
        assertEquals(28800, defaults.absoluteSeconds());
        assertEquals("https://example.invalid", ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", "https://EXAMPLE.invalid:443")).publicOrigin());
        assertEquals("UTC", ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", "https://example.invalid", "APP_DISPLAY_ZONE", "UTC")).displayZone().getId());
        for (var invalid : Map.of("APP_DISPLAY_ZONE", "invalid", "APP_SESSION_IDLE_SECONDS", "1801", "APP_SESSION_ABSOLUTE_SECONDS", "0").entrySet()) {
            assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of("APP_PUBLIC_ORIGIN", "https://example.invalid", invalid.getKey(), invalid.getValue())));
        }
    }
}
