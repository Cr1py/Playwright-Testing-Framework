package automation.core;

/**
 * Interprets the "video" / "screenshot" / "trace" config values:
 * - off keeps nothing
 * - on keeps always
 * - anything else ("retain-on-failure", "only-on-failure", "on-first-retry") keeps artifacts of failed tests only 
 */
public record ArtifactMode(boolean keepAlways, boolean keepOnFailure) {
    public static ArtifactMode of(String configValue) {
        return switch (configValue) {
            case "off" -> new ArtifactMode(false, false);
            case "on" -> new ArtifactMode(true, true);
            default -> new ArtifactMode(false, true);
        };
    }

    /* true if the artifact must be recorded while the test runs */
    public boolean enabled() {
        return keepAlways || keepOnFailure;
    }
}
