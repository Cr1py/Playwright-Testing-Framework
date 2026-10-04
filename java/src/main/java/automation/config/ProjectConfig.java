package automation.config;

import java.util.Arrays;

/* typed view of shared/config for one PROJECT and ENV (mirrors the TypeScript and Python models) */
public record ProjectConfig(
        String name,
        String browser,
        boolean headless,
        Viewport viewport,
        Timeouts timeouts,
        int retries,
        String video,
        String screenshot,
        String trace,
        Backend backend,
        Frontend frontend,
        Api api,
        String contract,
        String env,
        String contractPath,
        Credentials credentials) {

    public record Viewport(int width, int height) {
        public Viewport {
            positive("viewport.width", width);
            positive("viewport.height", height);
        }
    }

    public record Timeouts(int action, int navigation, int expect) {
        public Timeouts {
            positive("timeouts.action", action);
            positive("timeouts.navigation", navigation);
            positive("timeouts.expect", expect);
        }
    }

    public record Backend(String language, String framework) {
        public Backend {
            oneOf("backend.language", language, "java", "python", "typescript");
        }
    }

    public record Frontend(String framework, String baseURL) {
        public Frontend {
            notBlank("frontend.framework", framework);
            httpUrl("frontend.baseURL", baseURL);
        }
    }

    public record Api(String baseURL, ApiTimeouts timeouts, ApiAuth auth) {
        public Api {
            httpUrl("api.baseURL", baseURL);
            notNull("api.timeouts", timeouts);
            notNull("api.auth", auth);
        }
    }

    public record ApiTimeouts(int request) {
        public ApiTimeouts {
            positive("api.timeouts.request", request);
        }
    }

    public record ApiAuth(String type, String tokenEndpoint) {
        public ApiAuth {
            oneOf("api.auth.type", type, "bearer", "none");
        }
    }

    public record Credentials(String email, String password) {}

    public ProjectConfig {
        notBlank("name", name);
        oneOf("browser", browser, "chromium", "firefox", "webkit");
        notNull("viewport", viewport);
        notNull("timeouts", timeouts);
        if (retries < 0) throw new IllegalArgumentException("retries must be >= 0");
        oneOf("video", video, "off", "on", "retain-on-failure", "on-first-retry");
        oneOf("screenshot", screenshot, "off", "on", "only-on-failure");
        oneOf("trace", trace, "off", "on", "retain-on-failure", "on-first-retry", "on-all-retries");
        notNull("backend", backend);
        notNull("frontend", frontend);
        notNull("api", api);
        notBlank("contract", contract);
    }

    /* the configured test account
     * Throws if TEST_USER_EMAIL / TEST_USER_PASSWORD are not set
    */
    public Credentials requireCredentials() {
        if (credentials == null || credentials.email() == null || credentials.password() == null) {
            throw new IllegalStateException("TEST_USER_EMAIL and TEST_USER_PASSWORD must be set (see .env.example).");
        }
        return credentials;
    }

    private static void notNull(String field, Object value) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private static void notBlank(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static void positive(String field, int value) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be a positive number");
    }

    private static void httpUrl(String field, String value) {
        notBlank(field, value);
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(field + " must start with http:// or https://");
        }
    }

    private static void oneOf(String field, String value, String... allowed) {
        if (value == null || !Arrays.asList(allowed).contains(value)) {
            throw new IllegalArgumentException(field + " must be one of " + Arrays.toString(allowed) + " but was " + value);
        }
    }
}
