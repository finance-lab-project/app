package org.financelab.app;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationConfigTest {
    @Test
    void requiresExplicitBootstrapUntilLoginIsAvailable() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.from(Map.of("APP_MODE", "production")));
    }

    @Test
    void validatesDisplayZoneWithoutDependingOnHostLocale() {
        assertEquals("Europe/Moscow", ApplicationConfig.from(Map.of("APP_MODE", "bootstrap")).displayZone().getId());
        assertEquals("UTC", ApplicationConfig.from(Map.of("APP_MODE", "bootstrap", "APP_DISPLAY_ZONE", "UTC")).displayZone().getId());
        assertThrows(DateTimeException.class, () -> ApplicationConfig.from(Map.of("APP_MODE", "bootstrap", "APP_DISPLAY_ZONE", "invalid-zone")));
    }
}
