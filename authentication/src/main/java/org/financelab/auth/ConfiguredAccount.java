package org.financelab.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable startup configuration. Deliberately has no credential-bearing toString. */
public final class ConfiguredAccount {
    private static final Pattern HASH = Pattern.compile(
            "\\$argon2id\\$v=19\\$m=(\\d{5}),t=([2-6]),p=1\\$([A-Za-z0-9+/]{22,86})\\$([A-Za-z0-9+/]{43,86})");
    private final String username;
    private final String hash;
    private final UUID ownerId;

    private ConfiguredAccount(String username, String hash, UUID ownerId) {
        this.username = username;
        this.hash = hash;
        this.ownerId = ownerId;
    }

    public static ConfiguredAccount load(Map<String, String> environment) {
        try {
            var path = Path.of(environment.getOrDefault("APP_ACCOUNT_FILE", ""));
            var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 4096
                    || !Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE).containsAll(permissions)
                    || !permissions.contains(PosixFilePermission.OWNER_READ)) {
                throw new IllegalArgumentException();
            }
            var properties = new Properties();
            properties.load(new StringReader(Files.readString(path)));
            if (!properties.stringPropertyNames().equals(Set.of("username", "password-hash", "owner-id"))) {
                throw new IllegalArgumentException();
            }
            String username = properties.getProperty("username");
            String hash = properties.getProperty("password-hash");
            var match = HASH.matcher(hash);
            if (username.isBlank() || username.length() > 128 || username.chars().anyMatch(Character::isISOControl)
                    || !match.matches() || Integer.parseInt(match.group(1)) < 19456
                    || Integer.parseInt(match.group(1)) > 65536) {
                throw new IllegalArgumentException();
            }
            // Have the maintained verifier parse the full PHC representation at startup.
            Argon2Function.getInstanceFromHash(hash);
            String owner = properties.getProperty("owner-id");
            UUID ownerId = UUID.fromString(owner);
            if (!ownerId.toString().equals(owner) || ownerId.equals(new UUID(0, 0))) throw new IllegalArgumentException();
            return new ConfiguredAccount(username, hash, ownerId);
        } catch (Exception ignored) {
            // Never attach a cause: parser/I/O messages may contain account values or paths.
            throw new IllegalArgumentException("A valid owner-only APP_ACCOUNT_FILE is required");
        }
    }

    public UUID ownerId() { return ownerId; }

    boolean verifies(String candidateUsername, Object candidatePassword) {
        if (!(candidatePassword instanceof String password) || password.isEmpty() || password.length() > 1024) return false;
        boolean verified = Password.check(password, hash).with(Argon2Function.getInstanceFromHash(hash));
        return verified & username.equals(candidateUsername);
    }
}
