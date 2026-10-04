package automation.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * resolution order (see README "Shared Configuration"):
 * base.json -> projects/PROJECT/project.json -> projects/PROJECT/ENV.json -> environment variables
*/
public final class ConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper(); // strict: unknown config keys are errors
    private static final Map<String, ProjectConfig> CACHE = new ConcurrentHashMap<>();

    private ConfigLoader() {}

    /** config for the PROJECT and ENV environment variables (defaults: project-a, dev) */
    public static ProjectConfig load() {
        return load(Env.get("PROJECT").orElse("project-a"), Env.get("ENV").orElse("dev"));
    }

    public static ProjectConfig load(String project, String env) {
        return CACHE.computeIfAbsent(project + ":" + env, key -> build(project, env));
    }

    private static ProjectConfig build(String project, String env) {
        Path root = Env.repoRoot();
        Path configDir = root.resolve("shared").resolve("config");
        Path projectDir = configDir.resolve("projects").resolve(project);
        if (!Files.isDirectory(projectDir)) {
            throw new IllegalArgumentException("Unknown PROJECT \"" + project + "\". Known projects: " + knownProjects(configDir));
        }

        ObjectNode merged = deepMerge(readJson(configDir.resolve("base.json")), readJson(projectDir.resolve("project.json")));
        merged = deepMerge(merged, readJson(projectDir.resolve(env + ".json")));

        Optional<String> frontendUrl = Env.get("FRONTEND_BASE_URL");
        if (frontendUrl.isPresent()) merged.withObject("/frontend").put("baseURL", frontendUrl.get());
        Optional<String> apiUrl = Env.get("API_BASE_URL");
        if (apiUrl.isPresent()) merged.withObject("/api").put("baseURL", apiUrl.get());

        merged.put("env", env);
        JsonNode contract = merged.get("contract");
        if (contract != null) merged.put("contractPath", root.resolve(contract.asText()).normalize().toString());
        ObjectNode credentials = merged.putObject("credentials");
        Env.get("TEST_USER_EMAIL").ifPresent(v -> credentials.put("email", v));
        Env.get("TEST_USER_PASSWORD").ifPresent(v -> credentials.put("password", v));

        try {
            return MAPPER.treeToValue(merged, ProjectConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid config for " + project + "/" + env + ": " + e.getOriginalMessage(), e);
        }
    }

    private static String knownProjects(Path configDir) {
        try (Stream<Path> dirs = Files.list(configDir.resolve("projects"))) {
            return String.join(", ", dirs.map(p -> p.getFileName().toString()).sorted().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode readJson(Path file) {
        if (!Files.exists(file)) throw new IllegalArgumentException("Config file not found: " + file);
        try {
            return (ObjectNode) MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static ObjectNode deepMerge(ObjectNode target, ObjectNode source) {
        ObjectNode out = target.deepCopy();
        source.fields().forEachRemaining(entry -> {
            JsonNode current = out.get(entry.getKey());
            if (entry.getValue().isObject() && current != null && current.isObject()) {
                out.set(entry.getKey(), deepMerge((ObjectNode) current, (ObjectNode) entry.getValue()));
            } else {
                out.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
        return out;
    }
}
