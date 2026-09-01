package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/** Persistent, local-only learning aggregates and bounded task history. */
final class LearningStore {
    private static final String PROFILE = "profile-v1";
    private static final String HISTORY = "history-v1";
    private static final int MAX_TASKS = 30;
    private static final int MAX_EXAMPLES_PER_CLASS_PER_TASK = 20;

    private final Preferences preferences;
    private LearningProfile profile;
    private final Map<String, TaskRecord> history;

    LearningStore() {
        this(Preferences.userNodeForPackage(LearningStore.class));
    }

    LearningStore(Preferences preferences) {
        this.preferences = preferences;
        profile = LearningProfile.decode(preferences.get(PROFILE, ""));
        history = decodeHistory(preferences.get(HISTORY, ""));
    }

    synchronized LearningProfile profile() {
        return profile;
    }

    synchronized boolean observe(TaskReference reference, BuildingCandidateScanner.Evidence evidence,
            boolean building, double weight, int direction) {
        return observe(reference, evidence, building, weight, direction, true);
    }

    synchronized boolean observe(TaskReference reference, BuildingCandidateScanner.Evidence evidence,
            boolean building, double weight, int direction, boolean markAwaitingValidation) {
        if (evidence == null || direction == 0) {
            return false;
        }
        TaskRecord record = null;
        if (reference != null) {
            record = history.computeIfAbsent(key(reference), ignored ->
                    new TaskRecord(reference.getProjectId(), reference.getTaskId()));
            int current = building ? record.mapped : record.rejected;
            if ((direction > 0 && current >= MAX_EXAMPLES_PER_CLASS_PER_TASK)
                    || (direction < 0 && current < 1)) {
                return false;
            }
        }
        profile.observe(evidence, building, weight * direction);
        if (record != null) {
            if (building) {
                record.mapped = Math.max(0, record.mapped + direction);
            } else {
                record.rejected = Math.max(0, record.rejected + direction);
            }
            record.updated = Instant.now().getEpochSecond();
            if (building && direction > 0 && markAwaitingValidation
                    && "LOCAL".equals(record.status)) {
                record.status = "AWAITING VALIDATION";
            }
        }
        save();
        return true;
    }

    synchronized void setTaskStatus(TaskReference reference, String status) {
        TaskRecord record = history.get(key(reference));
        if (record == null || status == null || status.trim().isEmpty()) {
            return;
        }
        record.status = status.trim().toUpperCase();
        record.updated = Instant.now().getEpochSecond();
        save();
    }

    synchronized List<TaskRecord> records() {
        List<TaskRecord> result = new ArrayList<>(history.values());
        result.sort(Comparator.comparingLong(TaskRecord::getUpdated).reversed());
        return result;
    }

    synchronized List<TaskRecord> recordsForSync() {
        long cutoff = Instant.now().minus(90, ChronoUnit.DAYS).getEpochSecond();
        List<TaskRecord> result = new ArrayList<>();
        for (TaskRecord record : records()) {
            boolean finalState = "VALIDATED".equals(record.status)
                    || "INVALIDATED".equals(record.status)
                    || "BADIMAGERY".equals(record.status)
                    || "SPLIT".equals(record.status);
            if (!finalState && record.updated >= cutoff) {
                result.add(record);
            }
        }
        return result;
    }

    synchronized int awaitingCount() {
        int count = 0;
        for (TaskRecord record : history.values()) {
            if (record.status.contains("AWAITING") || "MAPPED".equals(record.status)
                    || "LOCKED_FOR_VALIDATION".equals(record.status)) {
                count++;
            }
        }
        return count;
    }

    synchronized void reset() {
        profile = new LearningProfile();
        history.clear();
        save();
    }

    private void save() {
        trimHistory();
        preferences.put(PROFILE, profile.encode());
        preferences.put(HISTORY, encodeHistory(history));
    }

    private void trimHistory() {
        if (history.size() <= MAX_TASKS) {
            return;
        }
        List<TaskRecord> records = records();
        for (int index = MAX_TASKS; index < records.size(); index++) {
            history.remove(records.get(index).key());
        }
    }

    private static String key(TaskReference reference) {
        return reference.getProjectId() + ":" + reference.getTaskId();
    }

    private static String encodeHistory(Map<String, TaskRecord> records) {
        StringBuilder text = new StringBuilder();
        for (TaskRecord record : records.values()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(record.project).append(':').append(record.task).append('|')
                    .append(record.status.replace("|", "").replace(";", "")).append('|')
                    .append(record.mapped).append('|').append(record.rejected).append('|')
                    .append(record.updated);
        }
        return text.toString();
    }

    private static Map<String, TaskRecord> decodeHistory(String encoded) {
        Map<String, TaskRecord> records = new LinkedHashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return records;
        }
        for (String item : encoded.split(";")) {
            String[] fields = item.split("\\|", -1);
            String[] ids = fields.length == 5 ? fields[0].split(":", -1) : new String[0];
            if (ids.length != 2) {
                continue;
            }
            try {
                TaskRecord record = new TaskRecord(Long.parseLong(ids[0]), Long.parseLong(ids[1]));
                record.status = fields[1];
                record.mapped = Integer.parseInt(fields[2]);
                record.rejected = Integer.parseInt(fields[3]);
                record.updated = Long.parseLong(fields[4]);
                records.put(record.key(), record);
            } catch (NumberFormatException exception) {
                // Ignore a damaged record without discarding the remaining history.
            }
        }
        return records;
    }

    static final class TaskRecord {
        private final long project;
        private final long task;
        private String status = "LOCAL";
        private int mapped;
        private int rejected;
        private long updated = Instant.now().getEpochSecond();

        TaskRecord(long project, long task) {
            this.project = project;
            this.task = task;
        }

        long getProject() { return project; }
        long getTask() { return task; }
        String getStatus() { return status; }
        int getMapped() { return mapped; }
        int getRejected() { return rejected; }
        long getUpdated() { return updated; }
        String key() { return project + ":" + task; }
    }
}
