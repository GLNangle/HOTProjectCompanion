package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.Arrays;

/**
 * Small, explainable local calibration profile for reconnaissance evidence.
 * It never replaces the conservative hard gates in the scanner.
 */
final class LearningProfile {
    static final int FEATURE_COUNT = 5;
    private static final int MIN_NEGATIVE_EXAMPLES = 1;
    private static final int MIN_BALANCED_EXAMPLES = 4;
    private static final double MAX_NEGATIVE_ADJUSTMENT = 0.14;
    private static final double MAX_POSITIVE_ADJUSTMENT = 0.04;

    private int positiveCount;
    private int negativeCount;
    private double positiveWeight;
    private double negativeWeight;
    private final double[] positiveSums = new double[FEATURE_COUNT];
    private final double[] negativeSums = new double[FEATURE_COUNT];

    void observe(BuildingCandidateScanner.Evidence evidence, boolean building,
            double signedWeight) {
        if (evidence == null || signedWeight == 0) {
            return;
        }
        double[] values = evidence.values();
        if (building) {
            positiveCount = Math.max(0, positiveCount + (signedWeight > 0 ? 1 : -1));
            positiveWeight = Math.max(0, positiveWeight + signedWeight);
            add(positiveSums, values, signedWeight);
        } else {
            negativeCount = Math.max(0, negativeCount + (signedWeight > 0 ? 1 : -1));
            negativeWeight = Math.max(0, negativeWeight + signedWeight);
            add(negativeSums, values, signedWeight);
        }
    }

    double adjust(double baseline, BuildingCandidateScanner.Evidence evidence) {
        if (evidence == null || negativeCount < MIN_NEGATIVE_EXAMPLES) {
            return baseline;
        }
        double[] values = evidence.values();
        double negativeDistance = distance(values, negativeSums, negativeWeight);
        double negativeSimilarity = clamp(1.0 - negativeDistance / 0.45, 0, 1);
        double negativeConfidence = Math.min(1.0, negativeCount / 6.0);
        double adjustment = -MAX_NEGATIVE_ADJUSTMENT * negativeSimilarity
                * negativeConfidence;

        // Rejections work on their own. Positive examples can only add a small
        // re-ranking uplift once both classes contain enough evidence.
        if (positiveCount >= MIN_BALANCED_EXAMPLES
                && negativeCount >= MIN_BALANCED_EXAMPLES) {
            double positiveDistance = distance(values, positiveSums, positiveWeight);
            double separation = negativeDistance - positiveDistance;
            double balancedConfidence = Math.min(1.0,
                    Math.min(positiveCount, negativeCount) / 12.0);
            adjustment += clamp(separation * 0.08, 0, MAX_POSITIVE_ADJUSTMENT)
                    * balancedConfidence;
        }
        return clamp(baseline + adjustment, 0, 1);
    }

    int getPositiveCount() {
        return positiveCount;
    }

    int getNegativeCount() {
        return negativeCount;
    }

    String encode() {
        StringBuilder text = new StringBuilder();
        text.append(positiveCount).append(',').append(negativeCount).append(',')
                .append(positiveWeight).append(',').append(negativeWeight);
        for (double value : positiveSums) {
            text.append(',').append(value);
        }
        for (double value : negativeSums) {
            text.append(',').append(value);
        }
        return text.toString();
    }

    static LearningProfile decode(String encoded) {
        LearningProfile profile = new LearningProfile();
        if (encoded == null || encoded.trim().isEmpty()) {
            return profile;
        }
        String[] parts = encoded.split(",");
        if (parts.length != 4 + FEATURE_COUNT * 2) {
            return profile;
        }
        try {
            profile.positiveCount = Math.max(0, Integer.parseInt(parts[0]));
            profile.negativeCount = Math.max(0, Integer.parseInt(parts[1]));
            profile.positiveWeight = Math.max(0, Double.parseDouble(parts[2]));
            profile.negativeWeight = Math.max(0, Double.parseDouble(parts[3]));
            for (int index = 0; index < FEATURE_COUNT; index++) {
                profile.positiveSums[index] = Double.parseDouble(parts[4 + index]);
                profile.negativeSums[index] = Double.parseDouble(
                        parts[4 + FEATURE_COUNT + index]);
            }
            return profile;
        } catch (NumberFormatException exception) {
            return new LearningProfile();
        }
    }

    private static void add(double[] sums, double[] values, double signedWeight) {
        for (int index = 0; index < sums.length; index++) {
            sums[index] = Math.max(0, sums[index] + values[index] * signedWeight);
        }
    }

    private static double distance(double[] values, double[] sums, double weight) {
        double total = 0;
        for (int index = 0; index < values.length; index++) {
            double difference = values[index] - sums[index] / Math.max(0.0001, weight);
            total += difference * difference;
        }
        return Math.sqrt(total / values.length);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public String toString() {
        return "LearningProfile{" + positiveCount + "," + negativeCount + ","
                + Arrays.toString(positiveSums) + "}";
    }
}
