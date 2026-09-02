package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SharedLearningTest {
    private SharedLearningTest() {
    }

    public static void main(String[] args) {
        sharingIsOffByDefaultAndQueuePersists();
        queueNeverStoresRawImageryLabels();
        geometryFlagsCanBeUpdatedBeforeSubmission();
        submittedExamplesRemainWithdrawable();
        requestContainsOnlyApprovedFields();
        aggregateInfluenceIsInactiveUntilThresholdsAreMetAndAlwaysCapped();
        System.out.println("SharedLearningTest: all tests passed");
    }

    private static void sharingIsOffByDefaultAndQueuePersists() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedLearningStore store = new SharedLearningStore(preferences);
        require(store.queue(TaskReference.forHotTask(12, 34), evidence(), true,
                BuildingCandidateScanner.Shape.RECTANGULAR, "Esri World Imagery") == null,
                "shared learning is off by default");
        store.setEnabled(true);
        String id = store.queue(TaskReference.forHotTask(12, 34), evidence(), true,
                BuildingCandidateScanner.Shape.RECTANGULAR, "Esri World Imagery");
        require(id != null && store.queued().size() == 1,
                "opted-in decision queues locally");
        require(new SharedLearningStore(preferences).queued().size() == 1,
                "queue survives plugin restart");
    }

    private static void queueNeverStoresRawImageryLabels() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedLearningStore store = new SharedLearningStore(preferences);
        store.setEnabled(true);
        store.queue(TaskReference.forHotTask(12, 34), evidence(), false,
                BuildingCandidateScanner.Shape.ROUND,
                "https://private.example.test/tiles?token=do-not-store");
        SharedLearningStore.Example example = store.queued().get(0);
        require(example.getImageryKey().startsWith("sha256-"),
                "imagery source is represented by a one-way identifier");
        require(!preferences.get("hotprojectcompanion.shared.examples-v1", "")
                .contains("private.example.test"), "raw imagery details are not persisted");
    }

    private static void geometryFlagsCanBeUpdatedBeforeSubmission() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedLearningStore store = new SharedLearningStore(preferences);
        store.setEnabled(true);
        String id = store.queue(TaskReference.forHotTask(12, 34), evidence(), true,
                BuildingCandidateScanner.Shape.RECTANGULAR, "imagery");
        store.updateEdits(id, EnumSet.of(GeometryEditOutcome.MOVED,
                GeometryEditOutcome.RESHAPED));
        EnumSet<GeometryEditOutcome> edits = new SharedLearningStore(preferences)
                .queued().get(0).getEdits();
        require(edits.contains(GeometryEditOutcome.MOVED)
                && edits.contains(GeometryEditOutcome.RESHAPED),
                "queued geometry corrections persist");
        require(store.removeQueued(id) && store.queued().isEmpty(),
                "restoring a decision removes its unsent shared example");
    }

    private static void submittedExamplesRemainWithdrawable() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedLearningStore store = new SharedLearningStore(preferences);
        store.setEnabled(true);
        String event = store.queue(TaskReference.forHotTask(12, 34), evidence(), false,
                BuildingCandidateScanner.Shape.RECTANGULAR, "imagery");
        String receipt = "0123456789abcdef0123456789abcdef01234567";
        store.markSent(Map.of(event, receipt));
        require(store.queued().isEmpty() && store.sent().size() == 1,
                "service receipt remains available for withdrawal");
        store.markWithdrawn(List.of(receipt));
        require(store.sent().isEmpty(), "withdrawn receipt is removed locally");
    }

    private static void requestContainsOnlyApprovedFields() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedLearningStore store = new SharedLearningStore(preferences);
        store.setEnabled(true);
        store.queue(TaskReference.forHotTask(12, 34), evidence(), false,
                BuildingCandidateScanner.Shape.ROUND, "Private imagery URL");
        String json = SharedLearningClient.requestBody(store.queued(),
                "installation-identifier-000001", "withdrawal-token-000000000000000001");
        Object parsed = MiniJson.parse(json);
        require(parsed instanceof Map, "submission is valid JSON");
        require(json.contains("\"projectId\":12") && json.contains("\"taskId\":34"),
                "task association is included");
        require(!json.contains("Private imagery URL") && !json.contains("latitude")
                && !json.contains("comment") && !json.contains("username"),
                "request excludes raw imagery, coordinates, comments and identity");
    }

    private static void aggregateInfluenceIsInactiveUntilThresholdsAreMetAndAlwaysCapped() {
        String insufficient = "{\"version\":0,\"profile\":{\"schemaVersion\":1,"
                + "\"status\":\"insufficient_data\",\"contributorCount\":0,"
                + "\"sampleCount\":0}}";
        SharedLearningProfile inactive = SharedLearningProfile.parse(insufficient);
        require(inactive.adjust(0.6, evidence()) == 0.6,
                "insufficient aggregate has no scanner influence");

        String active = "{\"version\":3,\"profile\":{\"schemaVersion\":1,"
                + "\"status\":\"active\",\"contributorCount\":10,\"sampleCount\":100,"
                + "\"learning\":{\"positiveMeans\":{" + means(0.9) + "},"
                + "\"negativeMeans\":{" + means(0.1) + "}}}}";
        SharedLearningProfile profile = SharedLearningProfile.parse(active);
        double adjusted = profile.adjust(0.6, evidence());
        require(profile.isActive() && adjusted >= 0.57 && adjusted <= 0.615,
                "active aggregate adjustment stays inside the strict client cap");
    }

    private static String means(double value) {
        return "\"consistency\":" + value + ",\"contrast\":" + value
                + ",\"boundary\":" + value + ",\"shadow\":" + value
                + ",\"geometry\":" + value;
    }

    private static BuildingCandidateScanner.Evidence evidence() {
        return new BuildingCandidateScanner.Evidence(0.8, 0.65, 0.75, 0.7, 0.8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryPreferences implements PluginPreferences.Store {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
