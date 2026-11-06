package org.financelab.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

/** Offline operator tool. Account values never appear in process arguments or output. */
public final class AccountFileTool {
    public static void main(String[] args) throws Exception {
        try {
            create(args);
        } catch (Exception ignored) {
            System.err.println("Account file was not created or validated; check the private destination and entered values");
            System.exit(1);
        }
    }

    private static void create(String[] args) throws Exception {
        var console = System.console();
        if (args.length != 1 || console == null) throw new IllegalArgumentException("Use an interactive terminal and a new output file");
        String username = console.readLine("Username: ");
        UUID owner = UUID.fromString(console.readLine("Stable owner UUID (retain for rotation/recovery): "));
        char[] password = console.readPassword("Password: ");
        char[] confirmation = console.readPassword("Repeat password: ");
        try {
            if (password.length < 16 || password.length > 1024 || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Use matching passwords of 16–1024 characters");
            }
            var properties = new Properties();
            properties.setProperty("username", username);
            properties.setProperty("owner-id", owner.toString());
            properties.setProperty("password-hash", Password.hash(java.nio.CharBuffer.wrap(password)).addRandomSalt(16)
                    .with(Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID)).getResult());
            var content = new StringWriter();
            properties.store(content, "Configured account; keep private");
            var path = Files.createFile(Path.of(args[0]), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(path, content.toString());
            ConfiguredAccount.load(java.util.Map.of("APP_ACCOUNT_FILE", path.toString()));
            console.printf("Account file created. Keep it and its recovery copy private.%n");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }
}
