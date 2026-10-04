package automation.data.models;

import java.util.Map;

public record LoginRow(String caseName, String email, String password, String expected, String message) {
    public static LoginRow from(Map<String, String> row) {
        return new LoginRow(row.get("case"), row.get("email"), row.get("password"), row.get("expected"), row.get("message"));
    }

    public boolean expectsSuccess() {
        return "success".equals(expected);
    }

    /* used as the display name of parameterized tests */
    @Override
    public String toString() {
        return caseName;
    }
}
