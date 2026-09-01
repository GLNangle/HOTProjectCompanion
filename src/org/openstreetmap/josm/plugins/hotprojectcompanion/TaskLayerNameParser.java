package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts HOT project and task identifiers from Tasking Manager boundary layer names. */
final class TaskLayerNameParser {
    private static final Pattern TASK_BOUNDARY = Pattern.compile(
            "(?i)boundary\\s+for\\s+task:\\s*(\\d+)\\s+of\\s+TM\\s+project\\s+#(\\d+)");

    private TaskLayerNameParser() {
    }

    static TaskReference parse(String layerName) {
        if (layerName == null) {
            return null;
        }

        Matcher matcher = TASK_BOUNDARY.matcher(layerName);
        if (!matcher.find()) {
            return null;
        }

        try {
            long taskId = Long.parseLong(matcher.group(1));
            long projectId = Long.parseLong(matcher.group(2));
            if (projectId < 1 || taskId < 1) {
                return null;
            }
            return TaskReference.forHotTask(projectId, taskId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
