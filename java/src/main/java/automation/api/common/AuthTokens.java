package automation.api.common;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import automation.config.ProjectConfig;
import automation.core.PlaywrightManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* one token per project/environment for the whole run, shared by all test threads */
public final class AuthTokens {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private AuthTokens() {}

    public static String token(ProjectConfig config) {
        if ("none".equals(config.api().auth().type())) return "";
        return CACHE.computeIfAbsent(config.name() + ":" + config.env(), key -> fetch(config));
    }

    private static String fetch(ProjectConfig config) {
        APIRequestContext ctx = PlaywrightManager.playwright().request()
                .newContext(new APIRequest.NewContextOptions().setBaseURL(BaseApiClient.baseUrl(config)));
        try {
            return new AuthClient(ctx, config).fetchToken();
        } finally {
            ctx.dispose();
        }
    }
}
