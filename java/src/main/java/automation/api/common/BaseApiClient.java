package automation.api.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import automation.config.ProjectConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;

import java.util.Map;
import java.util.regex.Pattern;

/** base class for API clients
 * clients send requests and return results (they never assert) */
public class BaseApiClient {
    private static final Pattern SENSITIVE = Pattern.compile("password|token|authorization|secret", Pattern.CASE_INSENSITIVE);

    protected final APIRequestContext ctx;

    public BaseApiClient(APIRequestContext ctx) {
        this.ctx = ctx;
    }

    /** playwright keeps a baseURL's path only if it ends with "/" and request paths have no leading "/" */
    public static String baseUrl(ProjectConfig config) {
        return config.api().baseURL().replaceAll("/+$", "") + "/";
    }

    protected ApiResult send(String method, String path, Object data, Map<String, String> params) {
        String url = path.replaceFirst("^/+", "");
        RequestOptions options = RequestOptions.create().setMethod(method);
        // a Map (not a String) makes Playwright send JSON with the right content-type
        if (data != null) options.setData(data instanceof Map ? data : Json.toMap(data));
        if (params != null) params.forEach(options::setQueryParam);

        APIResponse response = ctx.fetch(url, options);
        String text = response.text();
        JsonNode body = text.isBlank() ? null : Json.parse(text);

        attachExchange(method, url, params, data, response.status(), body);
        return new ApiResult(response.status(), response.ok(), body);
    }

    private static void attachExchange(String method, String url, Map<String, String> params, Object data, int status, JsonNode body) {
        try {
            ObjectNode exchange = JsonNodeFactory.instance.objectNode();
            ObjectNode request = exchange.putObject("request");
            request.put("method", method).put("url", url);
            if (params != null) request.set("params", Json.MAPPER.valueToTree(params));
            if (data != null) request.set("data", Json.MAPPER.valueToTree(data));
            ObjectNode response = exchange.putObject("response");
            response.put("status", status);
            if (body != null) response.set("body", body);

            String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(redact(exchange));
            Allure.attachment(method + " /" + url + " -> " + status, "application/json", json, AttachmentOptions.withFileExtension("json"));
        } catch (Exception ignored) {
            // attaching is a convenience; never let it fail a test
        }
    }

    private static JsonNode redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(e -> out.set(e.getKey(),
                    SENSITIVE.matcher(e.getKey()).find() ? JsonNodeFactory.instance.textNode("[REDACTED]") : redact(e.getValue())));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> out.add(redact(item)));
            return out;
        }
        return node;
    }
}
