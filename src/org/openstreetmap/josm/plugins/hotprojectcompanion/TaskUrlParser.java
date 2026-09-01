package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses task links without making a network request. */
public final class TaskUrlParser {
    private static final Pattern PROJECT_PATH = Pattern.compile(
            "^/projects/(\\d+)(?:/tasks(?:/(\\d+))?/?|/?)$");

    private TaskUrlParser() {
    }

    public static TaskReference parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Paste a HOT Tasking Manager task URL.");
        }

        final URI uri;
        try {
            uri = new URI(input.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("This is not a valid URL.", exception);
        }

        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !isSupportedHost(host)) {
            throw new IllegalArgumentException("Use a task URL from tasks.hotosm.org.");
        }

        Matcher path = PROJECT_PATH.matcher(uri.getPath());
        if (!path.matches()) {
            throw new IllegalArgumentException("The URL must identify a HOT project and task.");
        }

        long projectId = positiveId(path.group(1), "project");
        String taskValue = path.group(2);
        if (taskValue == null) {
            Map<String, String> query = parseQuery(uri.getRawQuery());
            taskValue = firstPresent(query, "task", "taskid", "task_id");
        }
        if (taskValue == null) {
            throw new IllegalArgumentException("The URL does not contain a task number.");
        }

        URI instance = URI.create(uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT));
        return new TaskReference(instance, projectId, positiveId(taskValue, "task"));
    }

    private static boolean isSupportedHost(String host) {
        return "tasks.hotosm.org".equalsIgnoreCase(host) || "tasks-stage.hotosm.org".equalsIgnoreCase(host);
    }

    private static long positiveId(String value, String label) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The " + label + " number is invalid.", exception);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            String key = decode(pair[0]).toLowerCase(Locale.ROOT);
            String value = pair.length > 1 ? decode(pair[1]) : "";
            values.putIfAbsent(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstPresent(Map<String, String> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key) && !values.get(key).isEmpty()) {
                return values.get(key);
            }
        }
        return null;
    }
}
