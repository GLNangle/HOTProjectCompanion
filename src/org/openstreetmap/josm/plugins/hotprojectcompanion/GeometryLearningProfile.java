package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;
import java.util.EnumSet;

/** Bounded, imagery-specific calibration of candidate marker geometry. */
final class GeometryLearningProfile {
    private static final int MIN_SAMPLES = 4;
    private static final double MAX_SHIFT = 0.12;
    private static final double MIN_SCALE = 0.85;
    private static final double MAX_SCALE = 1.18;

    private int movedCount;
    private int rotatedCount;
    private int reshapedCount;
    private int resizedCount;
    private double centreXSum;
    private double centreYSum;
    private double rotationSum;
    private double shapeDifferenceSum;
    private double logWidthScaleSum;
    private double logHeightScaleSum;

    void observe(GeometryMeasurement measurement, EnumSet<GeometryEditOutcome> outcomes,
            int direction) {
        if (measurement == null || outcomes == null || direction == 0) {
            return;
        }
        if (outcomes.contains(GeometryEditOutcome.MOVED)) {
            movedCount = Math.max(0, movedCount + direction);
            centreXSum += measurement.getCentreX() * direction;
            centreYSum += measurement.getCentreY() * direction;
        }
        if (outcomes.contains(GeometryEditOutcome.ROTATED)) {
            rotatedCount = Math.max(0, rotatedCount + direction);
            rotationSum += measurement.getRotation() * direction;
        }
        if (outcomes.contains(GeometryEditOutcome.RESHAPED)) {
            reshapedCount = Math.max(0, reshapedCount + direction);
            shapeDifferenceSum += measurement.getShapeDifference() * direction;
        }
        if (outcomes.contains(GeometryEditOutcome.RESIZED)) {
            resizedCount = Math.max(0, resizedCount + direction);
            logWidthScaleSum += Math.log(clamp(measurement.getWidthScale(), 0.25, 4)) * direction;
            logHeightScaleSum += Math.log(clamp(measurement.getHeightScale(), 0.25, 4)) * direction;
        }
    }

    Rectangle adjust(Rectangle original, Rectangle imageBounds) {
        Rectangle adjusted = new Rectangle(original);
        if (movedCount >= MIN_SAMPLES) {
            double dx = clamp(centreXSum / movedCount, -MAX_SHIFT, MAX_SHIFT);
            double dy = clamp(centreYSum / movedCount, -MAX_SHIFT, MAX_SHIFT);
            adjusted.translate((int) Math.round(adjusted.width * dx),
                    (int) Math.round(adjusted.height * dy));
        }
        if (resizedCount >= MIN_SAMPLES) {
            double widthScale = clamp(Math.exp(logWidthScaleSum / resizedCount),
                    MIN_SCALE, MAX_SCALE);
            double heightScale = clamp(Math.exp(logHeightScaleSum / resizedCount),
                    MIN_SCALE, MAX_SCALE);
            int width = Math.max(4, (int) Math.round(adjusted.width * widthScale));
            int height = Math.max(4, (int) Math.round(adjusted.height * heightScale));
            adjusted = new Rectangle((int) Math.round(adjusted.getCenterX() - width / 2.0),
                    (int) Math.round(adjusted.getCenterY() - height / 2.0), width, height);
        }
        Rectangle clipped = adjusted.intersection(imageBounds);
        return clipped.width < 4 || clipped.height < 4 ? new Rectangle(original) : clipped;
    }

    boolean hasActiveAdjustment() {
        return movedCount >= MIN_SAMPLES || resizedCount >= MIN_SAMPLES;
    }

    int getMovedCount() { return movedCount; }
    int getRotatedCount() { return rotatedCount; }
    int getReshapedCount() { return reshapedCount; }
    int getResizedCount() { return resizedCount; }

    String encode() {
        return movedCount + "," + rotatedCount + "," + reshapedCount + "," + resizedCount
                + "," + centreXSum + "," + centreYSum + "," + rotationSum + ","
                + shapeDifferenceSum + "," + logWidthScaleSum + "," + logHeightScaleSum;
    }

    static GeometryLearningProfile decode(String encoded) {
        GeometryLearningProfile profile = new GeometryLearningProfile();
        if (encoded == null || encoded.isEmpty()) {
            return profile;
        }
        String[] fields = encoded.split(",", -1);
        if (fields.length != 10) {
            return profile;
        }
        try {
            profile.movedCount = Math.max(0, Integer.parseInt(fields[0]));
            profile.rotatedCount = Math.max(0, Integer.parseInt(fields[1]));
            profile.reshapedCount = Math.max(0, Integer.parseInt(fields[2]));
            profile.resizedCount = Math.max(0, Integer.parseInt(fields[3]));
            profile.centreXSum = Double.parseDouble(fields[4]);
            profile.centreYSum = Double.parseDouble(fields[5]);
            profile.rotationSum = Double.parseDouble(fields[6]);
            profile.shapeDifferenceSum = Double.parseDouble(fields[7]);
            profile.logWidthScaleSum = Double.parseDouble(fields[8]);
            profile.logHeightScaleSum = Double.parseDouble(fields[9]);
            return profile;
        } catch (NumberFormatException exception) {
            return new GeometryLearningProfile();
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
