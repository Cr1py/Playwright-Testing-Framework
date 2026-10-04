package automation.utils;

import java.util.UUID;

/* unique per call, so parallel tests never collide on records */
public final class Unique {
    private Unique() {}

    public static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static String email() {
        return "qa+" + suffix() + "@example.com";
    }
}
