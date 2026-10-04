package automation.api.common;

import com.microsoft.playwright.APIRequestContext;
import automation.config.ProjectConfig;

import java.util.Map;

public class AuthClient extends BaseApiClient {
    private final ProjectConfig config;

    public AuthClient(APIRequestContext ctx, ProjectConfig config) {
        super(ctx);
        this.config = config;
    }

    public ApiResult token(String email, String password) {
        String endpoint = config.api().auth().tokenEndpoint() != null ? config.api().auth().tokenEndpoint() : "/auth/token";
        return send("POST", endpoint, Map.of("email", email, "password", password), null);
    }

    /** token for the configured test account
     * throws if the login fails
    **/
    public String fetchToken() {
        ProjectConfig.Credentials credentials = config.requireCredentials();
        ApiResult res = token(credentials.email(), credentials.password());
        if (res.status() != 200) {
            throw new IllegalStateException("Could not obtain a token (HTTP " + res.status() + "): " + res.body());
        }
        return res.body().get("access_token").asText();
    }
}
