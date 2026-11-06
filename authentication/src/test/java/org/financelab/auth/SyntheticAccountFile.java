package org.financelab.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/** Synthetic test data only; never used as a runtime default. */
public final class SyntheticAccountFile {
    public static final String OWNER = "10000000-0000-4000-8000-000000000001";
    public static final String PASSWORD = "synthetic-test-password-only";
    public static final String USERNAME = "synthetic-user";
    public static Path write(Path path, String password) throws Exception {
        var properties = new Properties();
        properties.setProperty("username", USERNAME);
        properties.setProperty("owner-id", OWNER);
        properties.setProperty("password-hash", Password.hash(password).addRandomSalt(16)
                .with(Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID)).getResult());
        var content = new StringWriter();
        properties.store(content, "Synthetic test account");
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        return Files.writeString(path, content.toString());
    }
    public static void main(String[] args) throws Exception {
        write(Path.of(args[0]), args.length == 1 ? PASSWORD : "synthetic-rotated-password-only");
    }
}
