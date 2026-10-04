package automation.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/* environment lookup: real environment variables win, then the repo-root .env file */
public final class Env {
    private static final Map<String, String> DOTENV = readDotenv();

    private Env() {}

    public static Optional<String> get(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) return Optional.of(value);
        return Optional.ofNullable(DOTENV.get(key)).filter(v -> !v.isBlank());
    }

    public static boolean flag(String key) {
        return get(key).map(v -> v.equals("1") || v.equalsIgnoreCase("true")).orElse(false);
    }

    /* the monorepo root: the nearest parent directory that contains shared/config */
    public static Path repoRoot() {
        String override = System.getProperty("repo.root");
        if (override != null) return Path.of(override).toAbsolutePath();
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("shared").resolve("config"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find the repo root (a parent directory containing shared/config).");
    }

    private static Map<String, String> readDotenv() {
        Map<String, String> values = new HashMap<>();
        Path file = repoRoot().resolve(".env");
        if (!Files.exists(file)) return values;
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 1) continue;
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(line.substring(0, eq).trim(), value);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return values;
    }
}
