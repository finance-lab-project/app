package org.financelab.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredAccountTest {
    @TempDir Path directory;
    @Test void rejectsMissingMalformedWeakOrExposedConfigurationWithoutLeakingValues() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredAccount.load(Map.of()));
        var path = SyntheticAccountFile.write(directory.resolve("account"), SyntheticAccountFile.PASSWORD);
        String valid = Files.readString(path);
        assertDoesNotThrow(() -> load(path));
        for (String invalid : new String[]{valid.replace("argon2id", "argon2i"), valid.replace("19456", "16384"),
                valid.replace("owner-id=" + SyntheticAccountFile.OWNER, "owner-id=invalid-private-value"),
                valid + "\nextra=value\n", valid.replace("password-hash=", "plaintext=")}) {
            Files.writeString(path, invalid);
            var error = assertThrows(IllegalArgumentException.class, () -> load(path));
            assertNull(error.getCause());
            assertFalse(error.toString().contains("private-value"));
        }
        Files.writeString(path, valid);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));
        assertThrows(IllegalArgumentException.class, () -> load(path));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        var link = directory.resolve("link");
        Files.createSymbolicLink(link, path);
        assertThrows(IllegalArgumentException.class, () -> load(link));
    }
    @Test void verifiesOnlyConfiguredAccountAndSuppliesStableOwner() throws Exception {
        var account = load(SyntheticAccountFile.write(directory.resolve("account"), SyntheticAccountFile.PASSWORD));
        var service = new ConfiguredLoginService(account, Clock.systemUTC());
        assertNull(service.login("other-user", SyntheticAccountFile.PASSWORD, null, null));
        assertNull(service.login(SyntheticAccountFile.USERNAME, "incorrect", null, null));
        var identity = service.login(SyntheticAccountFile.USERNAME, SyntheticAccountFile.PASSWORD, null, null);
        assertNotNull(identity);
        assertEquals(SyntheticAccountFile.OWNER, identity.getUserPrincipal().getName());
        assertTrue(identity.isUserInRole("owner"));
        assertTrue(service.validate(identity));
        assertFalse(new ConfiguredLoginService(account, Clock.systemUTC()).validate(identity));
        var rotated = load(SyntheticAccountFile.write(directory.resolve("rotated"), "rotated-synthetic-password"));
        assertEquals(account.ownerId(), rotated.ownerId());
        assertFalse(rotated.verifies(SyntheticAccountFile.USERNAME, SyntheticAccountFile.PASSWORD));
        assertTrue(rotated.verifies(SyntheticAccountFile.USERNAME, "rotated-synthetic-password"));
    }
    @Test void throttlesAcrossUsernamesAndRecoversAfterCooldown() throws Exception {
        var clock = new MutableClock();
        var service = new ConfiguredLoginService(load(SyntheticAccountFile.write(directory.resolve("account"), SyntheticAccountFile.PASSWORD)), clock);
        for (int attempt = 0; attempt < 5; attempt++) assertNull(service.login("unknown-" + attempt, "incorrect", null, null));
        assertNull(service.login(SyntheticAccountFile.USERNAME, SyntheticAccountFile.PASSWORD, null, null));
        clock.millis += 60_001;
        assertNotNull(service.login(SyntheticAccountFile.USERNAME, SyntheticAccountFile.PASSWORD, null, null));
    }
    @Test void disablesContainerFormBypass() {
        var authenticator = new ProgrammaticFormAuthenticator();
        for (String path : new String[]{"/j_security_check", "/other/j_security_check;param", "/j_security_check/"}) {
            assertFalse(authenticator.isJSecurityCheck(path));
        }
    }
    private ConfiguredAccount load(Path path) { return ConfiguredAccount.load(Map.of("APP_ACCOUNT_FILE", path.toString())); }
    private static class MutableClock extends Clock {
        long millis = 100_000;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
