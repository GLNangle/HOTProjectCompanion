package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.Locale;

/** Classifies a public Tasking Manager status comparison without overstating it. */
final class TaskStatusTransition {
    enum Kind {
        UNCHANGED,
        INITIAL_STATUS,
        STATUS_CHANGE,
        VALIDATION_OUTCOME
    }

    private final long project;
    private final long task;
    private final String previous;
    private final String current;
    private final Kind kind;

    private TaskStatusTransition(long project, long task, String previous,
            String current, Kind kind) {
        this.project = project;
        this.task = task;
        this.previous = normalise(previous);
        this.current = normalise(current);
        this.kind = kind;
    }

    static TaskStatusTransition between(LearningStore.TaskRecord record, String currentStatus) {
        String previous = normalise(record.getStatus());
        String current = normalise(currentStatus);
        Kind kind;
        if (previous.equals(current)) {
            kind = Kind.UNCHANGED;
        } else if (isValidationOutcome(current)) {
            kind = Kind.VALIDATION_OUTCOME;
        } else if ("LOCAL".equals(previous) || "AWAITING VALIDATION".equals(previous)) {
            kind = Kind.INITIAL_STATUS;
        } else {
            kind = Kind.STATUS_CHANGE;
        }
        return new TaskStatusTransition(record.getProject(), record.getTask(),
                previous, current, kind);
    }

    long getProject() { return project; }
    long getTask() { return task; }
    String getPrevious() { return previous; }
    String getCurrent() { return current; }
    Kind getKind() { return kind; }

    static String display(String status) {
        String normalised = normalise(status).replace('_', ' ');
        if (normalised.isEmpty()) {
            return "Unknown";
        }
        String lower = normalised.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean isValidationOutcome(String status) {
        return "VALIDATED".equals(status) || "INVALIDATED".equals(status);
    }

    private static String normalise(String status) {
        return status == null ? "" : status.trim().replace('_', ' ')
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
