package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.time.Duration;
import java.time.Instant;
import java.util.prefs.Preferences;

/** Remembers recent detailed feedback so a split child can recover it if HOT omits it. */
final class SplitFeedbackCache {
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final int MAX_TEXT_LENGTH = 6000;
    private static final String PROJECT = PluginPreferences.PREFIX + "split-feedback.project";
    private static final String TASK = PluginPreferences.PREFIX + "split-feedback.task";
    private static final String FEEDBACK = PluginPreferences.PREFIX + "split-feedback.text";
    private static final String SAVED_AT = PluginPreferences.PREFIX + "split-feedback.saved-at";
    private static final String MIGRATED = PluginPreferences.PREFIX + "split-feedback.migrated-v1";

    private final PluginPreferences.Store preferences;

    SplitFeedbackCache() {
        this(PluginPreferences.josm(),
                Preferences.userNodeForPackage(SplitFeedbackCache.class).node("split-feedback"));
    }

    SplitFeedbackCache(PluginPreferences.Store preferences) {
        this(preferences, null);
    }

    private SplitFeedbackCache(PluginPreferences.Store preferences, Preferences legacy) {
        this.preferences = preferences;
        migrateLegacyPreferences(legacy);
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
        String feedback = context.getInheritableFeedback();
        if (feedback.length() > MAX_TEXT_LENGTH) {
            feedback = feedback.substring(0, MAX_TEXT_LENGTH) + "\n\n[Long feedback shortened by the companion.]";
        }
        try {
            preferences.put(PROJECT, Long.toString(reference.getProjectId()));
            preferences.put(TASK, Long.toString(reference.getTaskId()));
            preferences.put(FEEDBACK, feedback);
            preferences.put(SAVED_AT, Long.toString(Instant.now().toEpochMilli()));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // The live task still works when local preferences are unavailable.
        }
    }

    Entry recentForSplitChild(TaskReference child) {
        try {
            long project = parseLong(preferences.get(PROJECT, ""), -1);
            long task = parseLong(preferences.get(TASK, ""), -1);
            long savedAt = parseLong(preferences.get(SAVED_AT, ""), 0);
            String feedback = preferences.get(FEEDBACK, "");
            boolean fresh = savedAt > 0
                    && Duration.between(Instant.ofEpochMilli(savedAt), Instant.now()).compareTo(MAX_AGE) <= 0;
            if (fresh && project == child.getProjectId() && task > 0 && task != child.getTaskId()
                    && !feedback.trim().isEmpty()) {
                return new Entry(task, feedback);
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // Treat unavailable preferences as an empty cache.
        }
        return null;
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
                + "\nHOT did not return the source comment on this child task. The companion retained it locally. "
                + "Check which points apply inside the current child boundary.\n\n"
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
}
