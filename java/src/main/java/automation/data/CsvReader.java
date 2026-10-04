package automation.data;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import automation.config.Env;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* reads shared/test-data/PROJECT/FILE with Univocity
 * {{VAR}} placeholders are replaced from the environment 
*/
public final class CsvReader {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private CsvReader() {}

    public static List<Map<String, String>> read(String project, String file) {
        Path path = Env.repoRoot().resolve("shared").resolve("test-data").resolve(project).resolve(file);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setNullValue("");
        settings.setEmptyValue("");

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new CsvParser(settings).parseAllRecords(reader).stream().map(CsvReader::interpolated).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    private static Map<String, String> interpolated(Record record) {
        Map<String, String> row = new LinkedHashMap<>();
        record.toFieldMap().forEach((key, value) -> row.put(key, interpolate(value)));
        return row;
    }

    private static String interpolate(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String resolved = Env.get(name).orElseThrow(() ->
                    new IllegalStateException("Test data references {{" + name + "}} but the env var is not set."));
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
