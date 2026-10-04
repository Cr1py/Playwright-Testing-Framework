package automation.api.common;

import com.fasterxml.jackson.databind.JsonNode;

/** status plus parsed body
 * {@code body} is null when the response has no content 
 * (ex: 204) 
**/
public record ApiResult(int status, boolean ok, JsonNode body) {
    public <T> T as(Class<T> type) {
        return Json.MAPPER.convertValue(body, type);
    }
}
