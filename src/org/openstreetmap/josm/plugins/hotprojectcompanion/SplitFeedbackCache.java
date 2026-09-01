package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.time.Duration;
import java.time.Instant;
import java.util.prefs.Preferences;

/** Remembers recent detailed feedback so a split child can recover it if HOT omits it. */
final class SplitFeedbackCache {
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final int MAX_TEXT_LENGTH = 6000;
    private static final String PROJECT = "project";
    private static final String TASK = "task";
    private static final String FEEDBACK = "feedback";
    private static final String SAVED_AT = "savedAt";

    private final Preferences preferences;

    SplitFeedbackCache() {
        preferences = Preferences.userNodeForPackage(SplitFeedbackCache.class).node("split-feedback");
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
            preferences.putLong(PROJECT, reference.getProjectId());
            preferences.putLong(TASK, reference.getTaskId());
            preferences.put(FEEDBACK, feedback);
            preferences.putLong(SAVED_AT, Instant.now().toEpochMilli());
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // The live task still works when local preferences are unavailable.
        }
    }

    Entry recentForSplitChild(TaskReference child) {
        try {
            long project = preferences.getLong(PROJECT, -1);
            long task = preferences.getLong(TASK, -1);
            long savedAt = preferences.getLong(SAVED_AT, 0);
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
