package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;
import java.util.EnumSet;

public final class GeometryLearningProfileTest {
    private GeometryLearningProfileTest() {
    }

    public static void main(String[] args) {
        requiresRepeatedCorrectionsAndCapsAdjustment();
        profileRoundTrips();
        System.out.println("GeometryLearningProfileTest: all tests passed");
    }

    private static void requiresRepeatedCorrectionsAndCapsAdjustment() {
        GeometryLearningProfile profile = new GeometryLearningProfile();
        GeometryMeasurement correction = new GeometryMeasurement(0.30, -0.25,
                1.50, 0.60, 0.3, 0.8);
        EnumSet<GeometryEditOutcome> outcomes = EnumSet.of(
                GeometryEditOutcome.MOVED, GeometryEditOutcome.RESIZED);
        Rectangle original = new Rectangle(100, 100, 50, 40);
        Rectangle image = new Rectangle(0, 0, 500, 500);
        for (int index = 0; index < 3; index++) {
            profile.observe(correction, outcomes, 1);
        }
        require(profile.adjust(original, image).equals(original),
                "fewer than four samples do not alter candidates");
        profile.observe(correction, outcomes, 1);
        Rectangle adjusted = profile.adjust(original, image);
        require(adjusted.x == 102 && adjusted.y == 98,
                "learned centre adjustment is capped at twelve percent");
        require(adjusted.width == 59 && adjusted.height == 34,
                "learned size adjustment is capped to the safe range");
    }

    private static void profileRoundTrips() {
        GeometryLearningProfile profile = new GeometryLearningProfile();
        profile.observe(new GeometryMeasurement(0.1, 0.05, 1.1, 0.95, 0.2, 0.4),
                EnumSet.allOf(GeometryEditOutcome.class), 1);
        GeometryLearningProfile restored = GeometryLearningProfile.decode(profile.encode());
        require(restored.getMovedCount() == 1 && restored.getRotatedCount() == 1
                        && restored.getReshapedCount() == 1 && restored.getResizedCount() == 1,
                "all geometry outcome aggregates persist");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
