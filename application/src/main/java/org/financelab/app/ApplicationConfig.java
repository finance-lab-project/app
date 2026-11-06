package org.financelab.app;

import java.net.URI;
import java.time.ZoneId;
import java.util.Map;
import java.util.Locale;

public record ApplicationConfig(ZoneId displayZone, String publicOrigin, int idleSeconds, int absoluteSeconds) {
    public static ApplicationConfig from(Map<String, String> environment) {
        try {
            var origin = URI.create(environment.getOrDefault("APP_PUBLIC_ORIGIN", ""));
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                    || origin.getRawQuery() != null || origin.getRawFragment() != null || !origin.getRawPath().isEmpty()
                    || origin.getPort() == 0 || origin.getPort() > 65535 || environment.containsKey("APP_MODE")) {
                throw new IllegalArgumentException();
            }
            return new ApplicationConfig(ZoneId.of(environment.getOrDefault("APP_DISPLAY_ZONE", "Europe/Moscow")),
                    "https://" + origin.getHost().toLowerCase(Locale.ROOT)
                            + (origin.getPort() == -1 || origin.getPort() == 443 ? "" : ":" + origin.getPort()),
                    limit(environment, "APP_SESSION_IDLE_SECONDS", 1800),
                    limit(environment, "APP_SESSION_ABSOLUTE_SECONDS", 28800));
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("Valid HTTPS origin, display zone and session limits are required; APP_MODE is unsupported");
        }
    }

    private static int limit(Map<String, String> environment, String name, int maximum) {
        int value = Integer.parseInt(environment.getOrDefault(name, Integer.toString(maximum)));
        if (value < 1 || value > maximum) throw new IllegalArgumentException();
        return value;
    }
}
