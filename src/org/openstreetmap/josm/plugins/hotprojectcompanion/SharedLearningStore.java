package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent opt-in queue for anonymous shared-learning examples. */
final class SharedLearningStore {
    private static final String ENABLED = PluginPreferences.PREFIX + "shared.enabled-v1";
    private static final String INSTALLATION = PluginPreferences.PREFIX + "shared.installation-v1";
    private static final String WITHDRAWAL = PluginPreferences.PREFIX + "shared.withdrawal-v1";
    private static final String EXAMPLES = PluginPreferences.PREFIX + "shared.examples-v1";
    private static final String PROFILE = PluginPreferences.PREFIX + "shared.profile-v1";
    private static final int MAX_EXAMPLES = 100;

    private final PluginPreferences.Store preferences;
    private final List<Example> examples;
    private SharedLearningProfile profile;

    SharedLearningStore() {
        this(PluginPreferences.josm());
    }

    SharedLearningStore(PluginPreferences.Store preferences) {
        this.preferences = preferences;
        examples = decode(preferences.get(EXAMPLES, ""));
        profile = SharedLearningProfile.parse(preferences.get(PROFILE, ""));
    }

    synchronized boolean isEnabled() {
        return "true".equals(preferences.get(ENABLED, "false"));
    }

    synchronized void setEnabled(boolean enabled) {
        preferences.put(ENABLED, Boolean.toString(enabled));
    }

    synchronized String queue(TaskReference reference, BuildingCandidateScanner.Evidence evidence,
            boolean building, BuildingCandidateScanner.Shape shape, String imagery) {
        if (!isEnabled() || reference == null || evidence == null || examples.size() >= MAX_EXAMPLES) {
            return null;
        }
        String eventId = UUID.randomUUID().toString();
        examples.add(new Example(eventId, "", reference.getProjectId(), reference.getTaskId(),
                Instant.now().getEpochSecond(), imageryKey(imagery),
                building ? "building" : "not_building",
                shape == BuildingCandidateScanner.Shape.ROUND ? "round"
                        : shape == BuildingCandidateScanner.Shape.RECTANGULAR
                                ? "rectangular" : "unknown",
                evidence.values(), GeometryEditOutcome.none(), State.QUEUED));
        saveExamples();
        return eventId;
    }

    synchronized boolean removeQueued(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return false;
        }
        Iterator<Example> iterator = examples.iterator();
        while (iterator.hasNext()) {
            Example example = iterator.next();
            if (eventId.equals(example.eventId) && example.state == State.QUEUED) {
                iterator.remove();
                saveExamples();
                return true;
            }
        }
        return false;
    }

    synchronized void updateEdits(String eventId, EnumSet<GeometryEditOutcome> edits) {
        if (eventId == null || edits == null) {
            return;
        }
        for (Example example : examples) {
            if (eventId.equals(example.eventId) && example.state == State.QUEUED) {
                example.edits = EnumSet.copyOf(edits);
                saveExamples();
                return;
            }
        }
    }

    synchronized List<Example> queued() {
        return filtered(State.QUEUED);
    }

    synchronized List<Example> sent() {
        return filtered(State.SENT);
    }

    synchronized void markSent(Map<String, String> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return;
        }
        for (Example example : examples) {
            String serviceId = receipts.get(example.eventId);
            if (serviceId != null && example.state == State.QUEUED) {
                example.serviceId = serviceId;
                example.state = State.SENT;
            }
        }
        saveExamples();
    }

    synchronized void markWithdrawn(Collection<String> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        examples.removeIf(example -> serviceIds.contains(example.serviceId));
        saveExamples();
    }

    synchronized String installationId() {
        return identifier(INSTALLATION, 1);
    }

    synchronized String withdrawalToken() {
        return identifier(WITHDRAWAL, 2);
    }

    synchronized SharedLearningProfile profile() {
        return profile;
    }

    synchronized void setProfile(String json) {
        SharedLearningProfile parsed = SharedLearningProfile.parse(json);
        preferences.put(PROFILE, json == null ? "" : json);
        profile = parsed;
    }

    private String identifier(String key, int parts) {
        String existing = preferences.get(key, "");
        if (!existing.isEmpty()) {
            return existing;
        }
        StringBuilder generated = new StringBuilder();
        for (int index = 0; index < parts; index++) {
            if (generated.length() > 0) {
                generated.append('.');
            }
            generated.append(UUID.randomUUID());
        }
        String value = generated.toString();
        preferences.put(key, value);
        return value;
    }

    private List<Example> filtered(State state) {
        List<Example> result = new ArrayList<>();
        for (Example example : examples) {
            if (example.state == state) {
                result.add(example.copy());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void saveExamples() {
        preferences.put(EXAMPLES, encode(examples));
    }

    private static String imageryKey(String imagery) {
        String normalised = imagery == null ? "" : imagery.trim().toLowerCase();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalised.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder("sha256-");
            for (int index = 0; index < 8; index++) {
                text.append(String.format("%02x", digest[index]));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "unknown";
        }
    }

    private static String encode(List<Example> values) {
        StringBuilder text = new StringBuilder();
        for (Example value : values) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(value.eventId).append('|').append(value.serviceId).append('|')
                    .append(value.projectId).append('|').append(value.taskId).append('|')
                    .append(value.attemptEpoch).append('|').append(value.imageryKey).append('|')
                    .append(value.decision).append('|').append(value.shape);
            for (double feature : value.features) {
                text.append('|').append(feature);
            }
            text.append('|').append(value.edits.contains(GeometryEditOutcome.MOVED) ? 1 : 0)
                    .append('|').append(value.edits.contains(GeometryEditOutcome.ROTATED) ? 1 : 0)
                    .append('|').append(value.edits.contains(GeometryEditOutcome.RESHAPED) ? 1 : 0)
                    .append('|').append(value.edits.contains(GeometryEditOutcome.RESIZED) ? 1 : 0)
                    .append('|').append(value.state.name());
        }
        return text.toString();
    }

    private static List<Example> decode(String encoded) {
        List<Example> result = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return result;
        }
        for (String row : encoded.split(";")) {
            String[] field = row.split("\\|", -1);
            if (field.length != 18) {
                continue;
            }
            try {
                double[] features = new double[5];
                for (int index = 0; index < features.length; index++) {
                    features[index] = Double.parseDouble(field[8 + index]);
                }
                EnumSet<GeometryEditOutcome> edits = GeometryEditOutcome.none();
                if ("1".equals(field[13])) edits.add(GeometryEditOutcome.MOVED);
                if ("1".equals(field[14])) edits.add(GeometryEditOutcome.ROTATED);
                if ("1".equals(field[15])) edits.add(GeometryEditOutcome.RESHAPED);
                if ("1".equals(field[16])) edits.add(GeometryEditOutcome.RESIZED);
                result.add(new Example(field[0], field[1], Long.parseLong(field[2]),
                        Long.parseLong(field[3]), Long.parseLong(field[4]), field[5], field[6],
                        field[7], features, edits, State.valueOf(field[17])));
            } catch (IllegalArgumentException exception) {
                // Ignore one damaged local queue entry without losing the rest.
            }
        }
        return result;
    }

    enum State { QUEUED, SENT }

    static final class Example {
        private final String eventId;
        private String serviceId;
        private final long projectId;
        private final long taskId;
        private final long attemptEpoch;
        private final String imageryKey;
        private final String decision;
        private final String shape;
        private final double[] features;
        private EnumSet<GeometryEditOutcome> edits;
        private State state;

        Example(String eventId, String serviceId, long projectId, long taskId,
                long attemptEpoch, String imageryKey, String decision, String shape,
                double[] features, EnumSet<GeometryEditOutcome> edits, State state) {
            this.eventId = eventId;
            this.serviceId = serviceId;
            this.projectId = projectId;
            this.taskId = taskId;
            this.attemptEpoch = attemptEpoch;
            this.imageryKey = imageryKey;
            this.decision = decision;
            this.shape = shape;
            this.features = features.clone();
            this.edits = EnumSet.copyOf(edits);
            this.state = state;
        }

        Example copy() {
            return new Example(eventId, serviceId, projectId, taskId, attemptEpoch,
                    imageryKey, decision, shape, features, edits, state);
        }

        String getEventId() { return eventId; }
        String getServiceId() { return serviceId; }
        long getProjectId() { return projectId; }
        long getTaskId() { return taskId; }
        long getAttemptEpoch() { return attemptEpoch; }
        String getImageryKey() { return imageryKey; }
        String getDecision() { return decision; }
        String getShape() { return shape; }
        double[] getFeatures() { return features.clone(); }
        EnumSet<GeometryEditOutcome> getEdits() { return EnumSet.copyOf(edits); }
    }
}
