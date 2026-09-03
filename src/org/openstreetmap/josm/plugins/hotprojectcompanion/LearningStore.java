package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/** Persistent, local-only learning aggregates and bounded task history. */
final class LearningStore {
    private static final String PROFILE = PluginPreferences.PREFIX + "learning.profile-v1";
    private static final String HISTORY = PluginPreferences.PREFIX + "learning.history-v1";
    private static final String GEOMETRY_PROFILES = PluginPreferences.PREFIX
            + "learning.geometry-profiles-v1";
    private static final String MIGRATED = PluginPreferences.PREFIX + "learning.migrated-v1";
    private static final String LEGACY_PROFILE = "profile-v1";
    private static final String LEGACY_HISTORY = "history-v1";
    private static final int MAX_AWAITING_TASKS = 100;
    private static final int MAX_OTHER_TASKS = 30;
    private static final int AWAITING_RETENTION_DAYS = 365;
    private static final int MAX_EXAMPLES_PER_CLASS_PER_TASK = 20;

    private final PluginPreferences.Store preferences;
    private LearningProfile profile;
    private final Map<String, TaskRecord> history;
    private final Map<String, GeometryLearningProfile> geometryProfiles;

    LearningStore() {
        this(PluginPreferences.josm(), Preferences.userNodeForPackage(LearningStore.class));
    }

    LearningStore(PluginPreferences.Store preferences) {
        this(preferences, null);
    }

    LearningStore(PluginPreferences.Store preferences, Preferences legacyPreferences) {
        this.preferences = preferences;
        migrateLegacyPreferences(legacyPreferences);
        profile = LearningProfile.decode(preferences.get(PROFILE, ""));
        history = decodeHistory(preferences.get(HISTORY, ""));
        geometryProfiles = decodeGeometryProfiles(preferences.get(GEOMETRY_PROFILES, ""));
    }

    private void migrateLegacyPreferences(Preferences legacy) {
        if (legacy == null || "true".equals(preferences.get(MIGRATED, ""))) {
            return;
        }
        try {
            if (preferences.get(PROFILE, "").isEmpty()) {
                String value = legacy.get(LEGACY_PROFILE, "");
                if (!value.isEmpty()) {
                    preferences.put(PROFILE, value);
                }
            }
            if (preferences.get(HISTORY, "").isEmpty()) {
                String value = legacy.get(LEGACY_HISTORY, "");
                if (!value.isEmpty()) {
                    preferences.put(HISTORY, value);
                }
            }
            preferences.put(MIGRATED, "true");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            // Continue with JOSM preferences if the former Java store is unavailable.
        }
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
        String updatedStatus = status.trim().toUpperCase();
        if (updatedStatus.equals(record.status)) {
            return;
        }
        record.status = updatedStatus;
        record.updated = Instant.now().getEpochSecond();
        save();
    }

    synchronized void replaceGeometryEdits(TaskReference reference,
            EnumSet<GeometryEditOutcome> previous, EnumSet<GeometryEditOutcome> current) {
        replaceGeometryEdits(reference, "", previous, null, current, null);
    }

    synchronized void replaceGeometryEdits(TaskReference reference, String imagery,
            EnumSet<GeometryEditOutcome> previous, GeometryMeasurement previousMeasurement,
            EnumSet<GeometryEditOutcome> current, GeometryMeasurement currentMeasurement) {
        if (reference == null || previous == null || current == null || previous.equals(current)) {
            return;
        }
        TaskRecord record = history.computeIfAbsent(key(reference), ignored ->
                new TaskRecord(reference.getProjectId(), reference.getTaskId()));
        record.moved = updatedCount(record.moved, previous, current, GeometryEditOutcome.MOVED);
        record.rotated = updatedCount(record.rotated, previous, current, GeometryEditOutcome.ROTATED);
        record.reshaped = updatedCount(record.reshaped, previous, current, GeometryEditOutcome.RESHAPED);
        record.resized = updatedCount(record.resized, previous, current, GeometryEditOutcome.RESIZED);
        record.updated = Instant.now().getEpochSecond();
        if (previousMeasurement != null && !previous.isEmpty()) {
            geometryProfile(imagery).observe(previousMeasurement, previous, -1);
        }
        if (currentMeasurement != null && !current.isEmpty()) {
            geometryProfile(imagery).observe(currentMeasurement, current, 1);
        }
        save();
    }

    synchronized GeometryLearningProfile geometryProfile(String imagery) {
        return geometryProfiles.computeIfAbsent(imageryKey(imagery),
                ignored -> new GeometryLearningProfile());
    }

    synchronized int[] geometryTotals() {
        int[] totals = new int[4];
        for (GeometryLearningProfile geometry : geometryProfiles.values()) {
            totals[0] += geometry.getMovedCount();
            totals[1] += geometry.getRotatedCount();
            totals[2] += geometry.getReshapedCount();
            totals[3] += geometry.getResizedCount();
        }
        return totals;
    }

    private static int updatedCount(int value, EnumSet<GeometryEditOutcome> previous,
            EnumSet<GeometryEditOutcome> current, GeometryEditOutcome outcome) {
        int delta = (current.contains(outcome) ? 1 : 0) - (previous.contains(outcome) ? 1 : 0);
        return Math.max(0, value + delta);
    }

    synchronized List<TaskRecord> records() {
        List<TaskRecord> result = new ArrayList<>(history.values());
        result.sort(Comparator.comparingLong(TaskRecord::getUpdated).reversed());
        return result;
    }

    synchronized List<TaskRecord> recordsForSync() {
        long cutoff = Instant.now().minus(AWAITING_RETENTION_DAYS, ChronoUnit.DAYS)
                .getEpochSecond();
        List<TaskRecord> result = new ArrayList<>();
        for (TaskRecord record : records()) {
            if (!isFinalState(record) && record.updated >= cutoff) {
                result.add(record);
            }
        }
        return result;
    }

    synchronized int awaitingCount() {
        int count = 0;
        for (TaskRecord record : history.values()) {
            if (isAwaitingValidation(record)) {
                count++;
            }
        }
        return count;
    }

    synchronized void reset() {
        profile = new LearningProfile();
        history.clear();
        geometryProfiles.clear();
        save();
    }

    private void save() {
        trimHistory();
        preferences.put(PROFILE, profile.encode());
        preferences.put(HISTORY, encodeHistory(history));
        preferences.put(GEOMETRY_PROFILES, encodeGeometryProfiles(geometryProfiles));
    }

    private void trimHistory() {
        List<TaskRecord> records = records();
        int awaiting = 0;
        int other = 0;
        for (TaskRecord record : records) {
            if (isAwaitingValidation(record)) {
                awaiting++;
                if (awaiting > MAX_AWAITING_TASKS) {
                    history.remove(record.key());
                }
            } else {
                other++;
                if (other > MAX_OTHER_TASKS) {
                    history.remove(record.key());
                }
            }
        }
    }

    private static boolean isAwaitingValidation(TaskRecord record) {
        return record.status.contains("AWAITING") || "MAPPED".equals(record.status)
                || "LOCKED_FOR_VALIDATION".equals(record.status);
    }

    private static boolean isFinalState(TaskRecord record) {
        return "VALIDATED".equals(record.status) || "INVALIDATED".equals(record.status)
                || "BADIMAGERY".equals(record.status) || "SPLIT".equals(record.status);
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
                    .append(record.updated).append('|').append(record.moved).append('|')
                    .append(record.rotated).append('|').append(record.reshaped).append('|')
                    .append(record.resized);
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
            String[] ids = fields.length == 5 || fields.length == 9
                    ? fields[0].split(":", -1) : new String[0];
            if (ids.length != 2) {
                continue;
            }
            try {
                TaskRecord record = new TaskRecord(Long.parseLong(ids[0]), Long.parseLong(ids[1]));
                record.status = fields[1];
                record.mapped = Integer.parseInt(fields[2]);
                record.rejected = Integer.parseInt(fields[3]);
                record.updated = Long.parseLong(fields[4]);
                if (fields.length == 9) {
                    record.moved = Integer.parseInt(fields[5]);
                    record.rotated = Integer.parseInt(fields[6]);
                    record.reshaped = Integer.parseInt(fields[7]);
                    record.resized = Integer.parseInt(fields[8]);
                }
                records.put(record.key(), record);
            } catch (NumberFormatException exception) {
                // Ignore a damaged record without discarding the remaining history.
            }
        }
        return records;
    }

    private static String imageryKey(String imagery) {
        String normalised = imagery == null ? "" : imagery.trim().toLowerCase();
        return Integer.toHexString(normalised.hashCode());
    }

    private static String encodeGeometryProfiles(Map<String, GeometryLearningProfile> profiles) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, GeometryLearningProfile> entry : profiles.entrySet()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(entry.getKey()).append('|').append(entry.getValue().encode());
        }
        return text.toString();
    }

    private static Map<String, GeometryLearningProfile> decodeGeometryProfiles(String encoded) {
        Map<String, GeometryLearningProfile> profiles = new LinkedHashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return profiles;
        }
        for (String item : encoded.split(";")) {
            int separator = item.indexOf('|');
            if (separator > 0 && separator < item.length() - 1) {
                profiles.put(item.substring(0, separator),
                        GeometryLearningProfile.decode(item.substring(separator + 1)));
            }
        }
        return profiles;
    }

    static final class TaskRecord {
        private final long project;
        private final long task;
        private String status = "LOCAL";
        private int mapped;
        private int rejected;
        private int moved;
        private int rotated;
        private int reshaped;
        private int resized;
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
        int getMoved() { return moved; }
        int getRotated() { return rotated; }
        int getReshaped() { return reshaped; }
        int getResized() { return resized; }
        long getUpdated() { return updated; }
        String key() { return project + ":" + task; }
    }
}
