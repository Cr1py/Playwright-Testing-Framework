package automation.api.common;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * validates API responses against an OpenAPI document
 *
 * <p>Covers the JSON Schema subset that OpenAPI generators emit: $ref to #/components, type (including
 * type arrays and nullable), required, properties, additionalProperties, items, enum, const, allOf/anyOf/oneOf,
 * string/number/array bounds, pattern, and the formats email, uuid, date, date-time and uri. Unsupported
 * keywords are ignored (the same lenient behaviour as ajv's strict:false in the TypeScript stack). If you need
 * complete JSON Schema coverage, swap the body of {@link #validate} for a library such as
 * networknt/json-schema-validator; the public API can stay the same.
 */
public final class ContractValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final JsonNode spec;

    public ContractValidator(Path specPath) {
        try {
            this.spec = new YAMLMapper().readTree(specPath.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read OpenAPI spec " + specPath, e);
        }
    }

    /**
     * throws AssertionError if the status is not declared for the operation 
     * or the body does not match the declared JSON schema. 
     * statuses without a body schema only check the status
     * @param pathTemplate the path as written in the spec, for example "/users/{id}"
     */
    public void assertResponse(String method, String pathTemplate, int status, JsonNode body) {
        String op = method.toUpperCase() + " " + pathTemplate;
        JsonNode operation = spec.path("paths").path(pathTemplate).path(method.toLowerCase());
        if (operation.isMissingNode()) throw new AssertionError("Contract has no operation: " + op);

        JsonNode responses = operation.path("responses");
        JsonNode response = responses.has(String.valueOf(status)) ? responses.get(String.valueOf(status)) : responses.path("default");
        if (response.isMissingNode()) {
            List<String> declared = new ArrayList<>();
            responses.fieldNames().forEachRemaining(declared::add);
            throw new AssertionError(op + ": status " + status + " is not in the contract (declared: " + String.join(", ", declared) + ")");
        }

        JsonNode schema = response.path("content").path("application/json").path("schema");
        if (schema.isMissingNode()) return;

        List<String> errors = new ArrayList<>();
        validate(schema, body == null ? NullNode.instance : body, "body", errors);
        if (!errors.isEmpty()) {
            throw new AssertionError(op + " " + status + " violates the contract: " + String.join("; ", errors));
        }
    }

    // JSON Schema subset

    private void validate(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.isBoolean()) {
            if (!schema.asBoolean()) errors.add(path + ": no value is allowed here");
            return;
        }
        if (schema.has("$ref")) validate(resolve(schema.get("$ref").asText()), value, path, errors);

        for (JsonNode sub : schema.path("allOf")) validate(sub, value, path, errors);
        if (schema.has("anyOf")) {
            if (matchingCount(schema.get("anyOf"), value, path) == 0) errors.add(path + ": does not match any of the allowed schemas");
        }
        if (schema.has("oneOf")) {
            int matches = matchingCount(schema.get("oneOf"), value, path);
            if (matches != 1) errors.add(path + ": must match exactly one of the allowed schemas but matches " + matches);
        }

        if (value.isNull() && schema.path("nullable").asBoolean(false)) return;
        if (schema.has("type") && !typeMatches(schema.get("type"), value)) {
            errors.add(path + ": expected type " + schema.get("type").toString().replace("\"", "") + " but was " + describe(value));
            return;
        }
        if (schema.has("const") && !schema.get("const").equals(value)) errors.add(path + ": must equal " + schema.get("const"));
        if (schema.has("enum") && !contains(schema.get("enum"), value)) errors.add(path + ": must be one of " + schema.get("enum"));

        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) validateNumber(schema, value, path, errors);
        if (value.isObject()) validateObject(schema, value, path, errors);
        if (value.isArray()) validateArray(schema, value, path, errors);
    }

    private void validateString(JsonNode schema, String text, String path, List<String> errors) {
        if (schema.has("minLength") && text.length() < schema.get("minLength").asInt()) errors.add(path + ": shorter than " + schema.get("minLength").asInt() + " characters");
        if (schema.has("maxLength") && text.length() > schema.get("maxLength").asInt()) errors.add(path + ": longer than " + schema.get("maxLength").asInt() + " characters");
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(text).find()) errors.add(path + ": does not match pattern " + schema.get("pattern").asText());
        String format = schema.path("format").asText("");
        if (!format.isEmpty() && !formatMatches(format, text)) errors.add(path + ": '" + text + "' is not a valid " + format);
    }

    private void validateNumber(JsonNode schema, JsonNode value, String path, List<String> errors) {
        double number = value.asDouble();
        if (schema.has("minimum") && number < schema.get("minimum").asDouble()) errors.add(path + ": below minimum " + schema.get("minimum"));
        if (schema.has("maximum") && number > schema.get("maximum").asDouble()) errors.add(path + ": above maximum " + schema.get("maximum"));
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors) {
        for (JsonNode required : schema.path("required")) {
            if (!value.has(required.asText())) errors.add(path + ": '" + required.asText() + "' is a required property");
        }
        JsonNode properties = schema.path("properties");
        JsonNode additional = schema.path("additionalProperties");
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (properties.has(name)) {
                validate(properties.get(name), value.get(name), path + "." + name, errors);
            } else if (additional.isBoolean() && !additional.asBoolean()) {
                errors.add(path + ": unexpected property '" + name + "'");
            } else if (additional.isObject()) {
                validate(additional, value.get(name), path + "." + name, errors);
            }
        }
    }

    private void validateArray(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) errors.add(path + ": fewer than " + schema.get("minItems").asInt() + " items");
        if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) errors.add(path + ": more than " + schema.get("maxItems").asInt() + " items");
        if (schema.has("items")) {
            for (int i = 0; i < value.size(); i++) validate(schema.get("items"), value.get(i), path + "[" + i + "]", errors);
        }
    }

    private int matchingCount(JsonNode candidates, JsonNode value, String path) {
        int matches = 0;
        for (JsonNode candidate : candidates) {
            List<String> scratch = new ArrayList<>();
            validate(candidate, value, path, scratch);
            if (scratch.isEmpty()) matches++;
        }
        return matches;
    }

    private JsonNode resolve(String ref) {
        if (!ref.startsWith("#/")) throw new AssertionError("Only local $ref values are supported, got " + ref);
        JsonNode target = spec.at(JsonPointer.compile(ref.substring(1)));
        if (target.isMissingNode()) throw new AssertionError("Unresolvable $ref " + ref);
        return target;
    }

    private static boolean typeMatches(JsonNode type, JsonNode value) {
        if (type.isArray()) {
            for (JsonNode t : type) if (typeMatches(t, value)) return true;
            return false;
        }
        return switch (type.asText()) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private static boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode candidate : array) if (candidate.equals(value)) return true;
        return false;
    }

    private static boolean formatMatches(String format, String text) {
        try {
            return switch (format) {
                case "email" -> EMAIL.matcher(text).matches();
                case "uuid" -> UUID.fromString(text).toString().equalsIgnoreCase(text);
                case "date" -> LocalDate.parse(text) != null;
                case "date-time" -> OffsetDateTime.parse(text) != null;
                case "uri" -> URI.create(text).isAbsolute();
                default -> true; // unknown formats are annotations only
            };
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String describe(JsonNode value) {
        return value.getNodeType().name().toLowerCase();
    }
}
