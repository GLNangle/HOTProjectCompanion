package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Locale;

/** Classifies already-mapped building footprints for the reconnaissance inventory. */
final class BuildingShapeClassifier {
    private BuildingShapeClassifier() {
    }

    static Shape classify(Polygon polygon, String buildingTag, String shapeTag) {
        String tags = ((buildingTag == null ? "" : buildingTag) + " "
                + (shapeTag == null ? "" : shapeTag)).toLowerCase(Locale.ROOT);
        if (tags.contains("round") || tags.contains("circular") || tags.contains("circle")) {
            return Shape.ROUND;
        }
        if (polygon == null || polygon.npoints < 3) {
            return Shape.OTHER;
        }
        Rectangle bounds = polygon.getBounds();
        if (bounds.width < 1 || bounds.height < 1) {
            return Shape.OTHER;
        }
        double area = polygonArea(polygon);
        double perimeter = perimeter(polygon);
        double aspect = Math.min(bounds.width, bounds.height)
                / (double) Math.max(bounds.width, bounds.height);
        double circularity = perimeter == 0 ? 0 : 4.0 * Math.PI * area / (perimeter * perimeter);
        if (polygon.npoints >= 8 && aspect >= 0.68 && circularity >= 0.72) {
            return Shape.ROUND;
        }
        if (polygon.npoints >= 4 && polygon.npoints <= 18 && orthogonalCornerRatio(polygon) >= 0.58) {
            return Shape.RECTANGULAR;
        }
        return Shape.OTHER;
    }

    private static double orthogonalCornerRatio(Polygon polygon) {
        int orthogonal = 0;
        int measured = 0;
        for (int index = 0; index < polygon.npoints; index++) {
            int previous = (index + polygon.npoints - 1) % polygon.npoints;
            int next = (index + 1) % polygon.npoints;
            double firstX = polygon.xpoints[previous] - polygon.xpoints[index];
            double firstY = polygon.ypoints[previous] - polygon.ypoints[index];
            double secondX = polygon.xpoints[next] - polygon.xpoints[index];
            double secondY = polygon.ypoints[next] - polygon.ypoints[index];
            double lengths = Math.hypot(firstX, firstY) * Math.hypot(secondX, secondY);
            if (lengths < 1) {
                continue;
            }
            double cosine = Math.max(-1, Math.min(1, (firstX * secondX + firstY * secondY) / lengths));
            double angle = Math.toDegrees(Math.acos(cosine));
            if (Math.abs(angle - 90) <= 18 || Math.abs(angle - 180) <= 12) {
                orthogonal++;
            }
            measured++;
        }
        return measured == 0 ? 0 : orthogonal / (double) measured;
    }

    private static double polygonArea(Polygon polygon) {
        double areaTwice = 0;
        for (int index = 0; index < polygon.npoints; index++) {
            int next = (index + 1) % polygon.npoints;
            areaTwice += polygon.xpoints[index] * (double) polygon.ypoints[next]
                    - polygon.xpoints[next] * (double) polygon.ypoints[index];
        }
        return Math.abs(areaTwice) / 2.0;
    }

    private static double perimeter(Polygon polygon) {
        double result = 0;
        for (int index = 0; index < polygon.npoints; index++) {
            int next = (index + 1) % polygon.npoints;
            result += Math.hypot(polygon.xpoints[next] - polygon.xpoints[index],
                    polygon.ypoints[next] - polygon.ypoints[index]);
        }
        return result;
    }

    enum Shape {
        RECTANGULAR,
        ROUND,
        OTHER
    }
}
