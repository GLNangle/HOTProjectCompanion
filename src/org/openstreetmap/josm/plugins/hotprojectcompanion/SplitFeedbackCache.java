package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/** Remembers recent detailed feedback so a split child can recover it if HOT omits it. */
final class SplitFeedbackCache {
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final int MAX_TEXT_LENGTH = 6000;
    private static final int MAX_SOURCE_ENTRIES_PER_PROJECT = 10;
    private static final int MAX_RECORDS = 60;
    private static final String PROJECT = PluginPreferences.PREFIX + "split-feedback.project";
    private static final String TASK = PluginPreferences.PREFIX + "split-feedback.task";
    private static final String FEEDBACK = PluginPreferences.PREFIX + "split-feedback.text";
    private static final String SAVED_AT = PluginPreferences.PREFIX + "split-feedback.saved-at";
    private static final String HISTORY = PluginPreferences.PREFIX + "split-feedback.history-v2";
    private static final String MIGRATED = PluginPreferences.PREFIX + "split-feedback.migrated-v1";
    private static final String HISTORY_MIGRATED = PluginPreferences.PREFIX + "split-feedback.migrated-v2";

    private final PluginPreferences.Store preferences;
    private final Clock clock;

    SplitFeedbackCache() {
        this(PluginPreferences.josm(),
                Preferences.userNodeForPackage(SplitFeedbackCache.class).node("split-feedback"),
                Clock.systemUTC());
    }

    SplitFeedbackCache(PluginPreferences.Store preferences) {
        this(preferences, null, Clock.systemUTC());
    }

    SplitFeedbackCache(PluginPreferences.Store preferences, Clock clock) {
        this(preferences, null, clock);
    }

    private SplitFeedbackCache(PluginPreferences.Store preferences, Preferences legacy, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
        migrateLegacyPreferences(legacy);
        migrateSingleEntry();
    }

    private void migrateLegacyPreferences(Preferences legacy) {
        if (legacy == null || "true".equals(preferences.get(MIGRATED, ""))) {
            return;
        }
        try {
            copyIfMissing(PROJECT, Long.toString(legacy.getLong("project", -1)));
            copyIfMissing(TASK, Long.toString(legacy.getLong("task", -1)));
            copyIfMissing(FEEDBACK, legacy.get("feedback", ""));
            copyIfMissing(SAVED_AT, Long.toString(legacy.getLong("savedAt", 0)));
            preferences.put(MIGRATED, "true");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // The live task still works when the former Java store is unavailable.
        }
    }

    private void migrateSingleEntry() {
        if ("true".equals(preferences.get(HISTORY_MIGRATED, ""))) {
            return;
        }
        try {
            List<Record> records = loadRecords();
            if (records.isEmpty()) {
                long project = parseLong(preferences.get(PROJECT, ""), -1);
                long task = parseLong(preferences.get(TASK, ""), -1);
                long savedAt = parseLong(preferences.get(SAVED_AT, ""), 0);
                String feedback = preferences.get(FEEDBACK, "");
                if (project > 0 && task > 0 && savedAt > 0 && !feedback.trim().isEmpty()) {
                    records.add(Record.source(project, task, savedAt, feedback));
                    saveRecords(records);
                }
            }
            preferences.put(HISTORY_MIGRATED, "true");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // The live task still works when local preferences are unavailable.
        }
    }

    private void copyIfMissing(String key, String legacyValue) {
        if (preferences.get(key, "").isEmpty() && legacyValue != null && !legacyValue.isEmpty()
                && !"-1".equals(legacyValue) && !"0".equals(legacyValue)) {
            preferences.put(key, legacyValue);
        }
    }

    void remember(TaskReference reference, TaskContext context) {
        if (context.isSplitTask() || !context.hasDetailedFeedback()
                || context.getInheritableFeedback().trim().isEmpty()) {
            return;
        }
        try {
            List<Record> records = loadRecords();
            records.removeIf(record -> !record.childBinding
                    && record.projectId == reference.getProjectId()
                    && record.taskId == reference.getTaskId());
            records.add(Record.source(reference.getProjectId(), reference.getTaskId(),
                    clock.instant().toEpochMilli(), shorten(context.getInheritableFeedback())));
            saveRecords(records);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // The live task still works when local preferences are unavailable.
        }
    }

    Entry recentForSplitChild(TaskReference child) {
        try {
            List<Record> records = loadRecords();
            Record previousBinding = newest(records, child.getProjectId(), child.getTaskId(), true);
            if (previousBinding != null && !isFresh(previousBinding.savedAt, clock.instant())) {
                prune(records);
                saveRecords(records);
                return null;
            }
            prune(records);
            Record bound = newest(records, child.getProjectId(), child.getTaskId(), true);
            if (bound != null) {
                saveRecords(records);
                return new Entry(bound.sourceTaskId, bound.feedback);
            }
            Record source = newestSource(records, child);
            if (source != null) {
                records.add(Record.child(child.getProjectId(), child.getTaskId(), source));
                saveRecords(records);
                return new Entry(source.sourceTaskId, source.feedback);
            }
            saveRecords(records);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // Treat unavailable preferences as an empty cache.
        }
        return null;
    }

    private Record newestSource(List<Record> records, TaskReference child) {
        Record newest = null;
        for (Record record : records) {
            if (!record.childBinding && record.projectId == child.getProjectId()
                    && record.taskId != child.getTaskId()
                    && (newest == null || record.savedAt > newest.savedAt)) {
                newest = record;
            }
        }
        return newest;
    }

    private static Record newest(List<Record> records, long projectId, long taskId, boolean childBinding) {
        Record newest = null;
        for (Record record : records) {
            if (record.childBinding == childBinding && record.projectId == projectId && record.taskId == taskId
                    && (newest == null || record.savedAt > newest.savedAt)) {
                newest = record;
            }
        }
        return newest;
    }

    private List<Record> loadRecords() {
        List<Record> records = new ArrayList<>();
        for (String line : preferences.get(HISTORY, "").split("\\R")) {
            Record record = Record.decode(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private void saveRecords(List<Record> records) {
        prune(records);
        StringBuilder encoded = new StringBuilder();
        for (Record record : records) {
            if (encoded.length() > 0) {
                encoded.append('\n');
            }
            encoded.append(record.encode());
        }
        preferences.put(HISTORY, encoded.toString());
    }

    private void prune(List<Record> records) {
        Instant now = clock.instant();
        records.removeIf(record -> record.feedback.trim().isEmpty() || !isFresh(record.savedAt, now));
        records.sort(Comparator.comparingLong((Record record) -> record.savedAt).reversed());
        Map<Long, Integer> sourceCounts = new HashMap<>();
        Iterator<Record> iterator = records.iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next();
            if (!record.childBinding) {
                int count = sourceCounts.getOrDefault(record.projectId, 0);
                if (count >= MAX_SOURCE_ENTRIES_PER_PROJECT) {
                    iterator.remove();
                } else {
                    sourceCounts.put(record.projectId, count + 1);
                }
            }
        }
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
    }

    static boolean isFresh(long savedAt, Instant now) {
        if (savedAt <= 0) {
            return false;
        }
        Duration age = Duration.between(Instant.ofEpochMilli(savedAt), now);
        return !age.isNegative() && age.compareTo(MAX_AGE) <= 0;
    }

    private static String shorten(String feedback) {
        if (feedback.length() <= MAX_TEXT_LENGTH) {
            return feedback;
        }
        return feedback.substring(0, MAX_TEXT_LENGTH) + "\n\n[Long feedback shortened by the companion.]";
    }

    private static long parseLong(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    static String appendInherited(String currentFeedback, Entry entry) {
        if (entry == null) {
            return currentFeedback;
        }
        return currentFeedback.trim()
                + "\n\nINHERITED FROM TASK " + entry.sourceTaskId + " BEFORE IT WAS SPLIT"
                + "\nHOT did not return the source comment on this child task. The companion retained it locally "
                + "for up to 30 days. Check which points apply inside the current child boundary.\n\n"
                + entry.feedback;
    }

    static final class Entry {
        private final long sourceTaskId;
        private final String feedback;

        Entry(long sourceTaskId, String feedback) {
            this.sourceTaskId = sourceTaskId;
            this.feedback = feedback;
        }
    }

    private static final class Record {
        private final boolean childBinding;
        private final long projectId;
        private final long taskId;
        private final long sourceTaskId;
        private final long savedAt;
        private final String feedback;

        private Record(boolean childBinding, long projectId, long taskId, long sourceTaskId,
                long savedAt, String feedback) {
            this.childBinding = childBinding;
            this.projectId = projectId;
            this.taskId = taskId;
            this.sourceTaskId = sourceTaskId;
            this.savedAt = savedAt;
            this.feedback = feedback;
        }

        private static Record source(long projectId, long taskId, long savedAt, String feedback) {
            return new Record(false, projectId, taskId, taskId, savedAt, feedback);
        }

        private static Record child(long projectId, long childTaskId, Record source) {
            return new Record(true, projectId, childTaskId, source.sourceTaskId,
                    source.savedAt, source.feedback);
        }

        private String encode() {
            String text = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(feedback.getBytes(StandardCharsets.UTF_8));
            return (childBinding ? "C" : "S") + "|" + projectId + "|" + taskId + "|"
                    + sourceTaskId + "|" + savedAt + "|" + text;
        }

        private static Record decode(String line) {
            String[] fields = line.split("\\|", 6);
            if (fields.length != 6 || !("C".equals(fields[0]) || "S".equals(fields[0]))) {
                return null;
            }
            try {
                String feedback = new String(Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
                return new Record("C".equals(fields[0]), Long.parseLong(fields[1]),
                        Long.parseLong(fields[2]), Long.parseLong(fields[3]),
                        Long.parseLong(fields[4]), feedback);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
