package org.financelab.app;

import java.time.ZoneId;
import java.util.Map;

/** Bootstrap only: the configured-account boundary is delivered by app #4. */
public record ApplicationConfig(ZoneId displayZone) {
    public static ApplicationConfig from(Map<String, String> environment) {
        if (!"bootstrap".equals(environment.get("APP_MODE"))) {
            throw new IllegalArgumentException("APP_MODE=bootstrap is required until configured-account login is implemented");
        }
        return new ApplicationConfig(ZoneId.of(environment.getOrDefault("APP_DISPLAY_ZONE", "Europe/Moscow")));
    }
}
