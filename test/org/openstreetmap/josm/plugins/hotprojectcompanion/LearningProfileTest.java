package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.prefs.Preferences;

public final class LearningProfileTest {
    private LearningProfileTest() {
    }

    public static void main(String[] args) {
        rejectionOnlyEvidenceSuppressesSimilarCandidates();
        sourceWeightsInfluenceCalibration();
        profileRoundTrips();
        storePersistsAndCapsOneTask();
        existingMappedReviewLearnsWithoutAwaitingValidation();
        System.out.println("LearningProfileTest: all tests passed");
    }

    private static void rejectionOnlyEvidenceSuppressesSimilarCandidates() {
        LearningProfile profile = new LearningProfile();
        BuildingCandidateScanner.Evidence rejected = evidence(0.75, 0.42, 0.48, 0.30, 0.46);
        double baseline = 0.62;
        require(profile.adjust(baseline, rejected) == baseline,
                "an empty profile leaves the baseline unchanged");
        profile.observe(rejected, false, 1.0);
        require(profile.adjust(baseline, rejected) < baseline,
                "one rejection immediately suppresses a similar candidate");
        for (int index = 0; index < 5; index++) {
            profile.observe(rejected, false, 1.0);
        }
        require(profile.adjust(baseline, rejected) < 0.50,
                "repeated rejections strongly suppress the same false-positive pattern");
    }

    private static void sourceWeightsInfluenceCalibration() {
        LearningProfile profile = new LearningProfile();
        BuildingCandidateScanner.Evidence positive = evidence(0.85, 0.75, 0.8, 0.9, 0.8);
        BuildingCandidateScanner.Evidence negative = evidence(0.55, 0.3, 0.35, 0.2, 0.35);
        for (int index = 0; index < 4; index++) {
            profile.observe(positive, true, 1.0);
            profile.observe(negative, false, 1.0);
        }
        double adjusted = profile.adjust(0.60, evidence(0.8, 0.7, 0.75, 0.8, 0.75));
        require(adjusted > 0.60, "positive-like evidence receives a cautious uplift");

        LearningProfile validatorWeighted = new LearningProfile();
        for (int index = 0; index < 4; index++) {
            validatorWeighted.observe(positive, true, 3.0);
            validatorWeighted.observe(negative, false, 1.0);
        }
        require(validatorWeighted.adjust(0.60, positive) >= adjusted,
                "strong validator-style evidence is not diluted to mapper weight");
    }

    private static void profileRoundTrips() {
        LearningProfile profile = new LearningProfile();
        profile.observe(evidence(0.8, 0.7, 0.7, 0.8, 0.7), true, 1.5);
        profile.observe(evidence(0.4, 0.3, 0.4, 0.2, 0.3), false, 1.0);
        LearningProfile restored = LearningProfile.decode(profile.encode());
        require(restored.getPositiveCount() == 1, "positive count persisted");
        require(restored.getNegativeCount() == 1, "negative count persisted");
    }

    private static void storePersistsAndCapsOneTask() {
        String nodeName = "test-" + System.nanoTime();
        Preferences preferences = Preferences.userRoot().node(
                "/org/openstreetmap/josm/plugins/hotprojectcompanion/" + nodeName);
        try {
            LearningStore store = new LearningStore(preferences);
            TaskReference reference = TaskReference.forHotTask(12, 34);
            BuildingCandidateScanner.Evidence evidence = evidence(0.7, 0.7, 0.7, 0.7, 0.7);
            int recorded = 0;
            for (int index = 0; index < 25; index++) {
                if (store.observe(reference, evidence, true, 1.0, 1)) {
                    recorded++;
                }
            }
            require(recorded == 20, "single-task positive examples are capped");
            LearningStore restored = new LearningStore(preferences);
            require(restored.profile().getPositiveCount() == 20, "store profile persisted");
            require(restored.records().size() == 1, "task history persisted");
        } finally {
            try {
                preferences.removeNode();
            } catch (Exception exception) {
                throw new AssertionError("temporary learning preferences removed", exception);
            }
        }
    }

    private static void existingMappedReviewLearnsWithoutAwaitingValidation() {
        String nodeName = "test-mapped-review-" + System.nanoTime();
        Preferences preferences = Preferences.userRoot().node(
                "/org/openstreetmap/josm/plugins/hotprojectcompanion/" + nodeName);
        try {
            LearningStore store = new LearningStore(preferences);
            TaskReference reference = TaskReference.forHotTask(56, 78);
            BuildingCandidateScanner.Evidence evidence = evidence(0.4, 0.3, 0.4, 0.2, 0.3);
            require(store.observe(reference, evidence, true, 1.0, 1, false),
                    "confirmed existing building is recorded");
            require("LOCAL".equals(store.records().get(0).getStatus()),
                    "reviewing existing mapped data does not claim new work awaits validation");
            require(store.observe(reference, evidence, true, 1.0, -1, false),
                    "restoring the review removes its learning observation");
        } finally {
            try {
                preferences.removeNode();
            } catch (Exception exception) {
                throw new AssertionError("temporary mapped-review preferences removed", exception);
            }
        }
    }

    private static BuildingCandidateScanner.Evidence evidence(double consistency, double contrast,
            double boundary, double shadow, double geometry) {
        return new BuildingCandidateScanner.Evidence(consistency, contrast, boundary, shadow,
                geometry);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
