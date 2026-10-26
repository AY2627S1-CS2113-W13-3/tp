package lablog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses booking fields without accepting duplicates, unknown prefixes or trailing bare arguments. */
final class BookingArguments {
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)([^\\s/]+)/");

    private BookingArguments() {
    }

    /** Reads space-containing field values while retaining exact identifiers and rejecting ambiguity. */
    static Map<String, String> fields(String input, Set<String> allowed) {
        Matcher matcher = PREFIX.matcher(input);
        Map<String, String> result = new HashMap<>();
        if (!matcher.find() || !input.substring(0, matcher.start()).isBlank()) {
            throw new IllegalArgumentException("Supply prefixed booking fields.");
        }
        String key = matcher.group(1);
        int start = matcher.end();
        while (matcher.find()) {
            add(result, allowed, key, input.substring(start, matcher.start()).strip());
            key = matcher.group(1);
            start = matcher.end();
        }
        add(result, allowed, key, input.substring(start).strip());
        return result;
    }

    private static void add(Map<String, String> result, Set<String> allowed, String key, String value) {
        if (!allowed.contains(key) || result.containsKey(key) || value.isBlank()
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use each supported booking field once with a non-empty value.");
        }
        result.put(key, value);
    }
}
