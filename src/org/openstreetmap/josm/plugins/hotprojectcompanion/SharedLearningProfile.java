package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.Map;

/**
 * Privacy-preserving aggregate downloaded from the shared-learning service.
 * Individual examples are never included in this object.
 */
final class SharedLearningProfile {
    private static final int FEATURE_COUNT = 5;
    private static final double MAX_SUPPRESSION = 0.03;
    private static final double MAX_UPLIFT = 0.015;

    private final int version;
    private final String status;
    private final int contributorCount;
    private final int sampleCount;
    private final double[] positiveMeans;
    private final double[] negativeMeans;
    private final String qualityStatus;
    private final int holdoutSampleCount;
    private final double baselineBrierScore;
    private final double proposedBrierScore;

    private SharedLearningProfile(int version, String status, int contributorCount,
            int sampleCount, double[] positiveMeans, double[] negativeMeans,
            String qualityStatus, int holdoutSampleCount, double baselineBrierScore,
            double proposedBrierScore) {
        this.version = Math.max(0, version);
        this.status = status == null ? "unavailable" : status;
        this.contributorCount = Math.max(0, contributorCount);
        this.sampleCount = Math.max(0, sampleCount);
        this.positiveMeans = positiveMeans;
        this.negativeMeans = negativeMeans;
        this.qualityStatus = qualityStatus == null ? "" : qualityStatus;
        this.holdoutSampleCount = Math.max(0, holdoutSampleCount);
        this.baselineBrierScore = baselineBrierScore;
        this.proposedBrierScore = proposedBrierScore;
    }

    static SharedLearningProfile unavailable() {
        return new SharedLearningProfile(0, "unavailable", 0, 0, null, null,
                "", 0, Double.NaN, Double.NaN);
    }

    static SharedLearningProfile parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return unavailable();
        }
        try {
            Map<?, ?> root = map(MiniJson.parse(json));
            Map<?, ?> profile = map(root.get("profile"));
            int version = integer(root.get("version"));
            int schemaVersion = integer(profile.get("schemaVersion"));
            String status = text(profile.get("status"));
            int contributors = integer(profile.get("contributorCount"));
            int samples = integer(profile.get("sampleCount"));
            Map<?, ?> quality = optionalMap(profile.get("qualityGate"));
            String qualityStatus = text(quality.get("status"));
            int holdout = integer(quality.get("holdoutSampleCount"));
            Map<?, ?> baselineMetrics = optionalMap(quality.get("baseline"));
            Map<?, ?> proposedMetrics = optionalMap(quality.get("proposed"));
            double baselineBrier = number(baselineMetrics.get("brierScore"));
            double proposedBrier = number(proposedMetrics.get("brierScore"));
            if (schemaVersion != 1 || !"active".equals(status)) {
                return new SharedLearningProfile(version,
                        status.isEmpty() ? "unavailable" : status,
                        contributors, samples, null, null, qualityStatus, holdout,
                        baselineBrier, proposedBrier);
            }
            Map<?, ?> learning = map(profile.get("learning"));
            double[] positive = means(map(learning.get("positiveMeans")));
            double[] negative = means(map(learning.get("negativeMeans")));
            if (positive == null || negative == null) {
                return new SharedLearningProfile(version, "unavailable",
                        contributors, samples, null, null, qualityStatus, holdout,
                        baselineBrier, proposedBrier);
            }
            return new SharedLearningProfile(version, status, contributors, samples,
                    positive, negative, qualityStatus, holdout, baselineBrier,
                    proposedBrier);
        } catch (IllegalArgumentException | ClassCastException exception) {
            return unavailable();
        }
    }

    double adjust(double baseline, BuildingCandidateScanner.Evidence evidence) {
        if (!isActive() || evidence == null) {
            return baseline;
        }
        double[] values = evidence.values();
        double positiveDistance = distance(values, positiveMeans);
        double negativeDistance = distance(values, negativeMeans);
        double separation = negativeDistance - positiveDistance;
        double confidence = Math.min(1.0, sampleCount / 100.0)
                * Math.min(1.0, contributorCount / 10.0);
        double adjustment = clamp(separation * 0.06,
                -MAX_SUPPRESSION, MAX_UPLIFT) * confidence;
        return clamp(baseline + adjustment, 0, 1);
    }

    boolean isActive() {
        return "active".equals(status) && positiveMeans != null && negativeMeans != null;
    }

    int getVersion() { return version; }
    String getStatus() { return status; }
    int getContributorCount() { return contributorCount; }
    int getSampleCount() { return sampleCount; }
    String getQualityStatus() { return qualityStatus; }
    int getHoldoutSampleCount() { return holdoutSampleCount; }
    double getBaselineBrierScore() { return baselineBrierScore; }
    double getProposedBrierScore() { return proposedBrierScore; }

    private static double[] means(Map<?, ?> values) {
        String[] names = {"consistency", "contrast", "boundary", "shadow", "geometry"};
        double[] result = new double[FEATURE_COUNT];
        for (int index = 0; index < names.length; index++) {
            Object value = values.get(names[index]);
            if (!(value instanceof Number)) {
                return null;
            }
            result[index] = ((Number) value).doubleValue();
            if (!Double.isFinite(result[index]) || result[index] < 0 || result[index] > 1) {
                return null;
            }
        }
        return result;
    }

    private static double distance(double[] left, double[] right) {
        double total = 0;
        for (int index = 0; index < left.length; index++) {
            double difference = left[index] - right[index];
            total += difference * difference;
        }
        return Math.sqrt(total / left.length);
    }

    private static Map<?, ?> map(Object value) {
        if (value instanceof Map) {
            return (Map<?, ?>) value;
        }
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private static Map<?, ?> optionalMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : java.util.Collections.emptyMap();
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
    }

    private static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
