package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MapView;

/** Normalised before/after geometry difference for one mapper-confirmed correction. */
final class GeometryMeasurement {
    private final double centreX;
    private final double centreY;
    private final double widthScale;
    private final double heightScale;
    private final double rotation;
    private final double shapeDifference;

    GeometryMeasurement(double centreX, double centreY, double widthScale, double heightScale,
            double rotation, double shapeDifference) {
        this.centreX = finite(centreX, 0);
        this.centreY = finite(centreY, 0);
        this.widthScale = finite(widthScale, 1);
        this.heightScale = finite(heightScale, 1);
        this.rotation = finite(rotation, 0);
        this.shapeDifference = finite(shapeDifference, 0);
    }

    static GeometryMeasurement candidateToWay(ProjectionBounds candidateArea,
            BuildingCandidateScanner.Shape candidateShape, Way finalWay, MapView mapView) {
        if (candidateArea == null || finalWay == null || mapView == null) {
            return null;
        }
        Point first = mapView.getPoint(candidateArea.getMin());
        Point second = mapView.getPoint(candidateArea.getMax());
        Rectangle candidate = new Rectangle(Math.min(first.x, second.x), Math.min(first.y, second.y),
                Math.max(1, Math.abs(first.x - second.x)), Math.max(1, Math.abs(first.y - second.y)));
        Snapshot mapped = Snapshot.fromPixels(finalWay, mapView);
        if (mapped == null) {
            return null;
        }
        double dx = (mapped.centreX - candidate.getCenterX()) / candidate.width;
        double dy = (mapped.centreY - candidate.getCenterY()) / candidate.height;
        double widthScale = mapped.width / candidate.width;
        double heightScale = mapped.height / candidate.height;
        boolean finalRound = mapped.circularity > 0.72;
        boolean expectedRound = candidateShape == BuildingCandidateScanner.Shape.ROUND;
        return new GeometryMeasurement(dx, dy, widthScale, heightScale,
                normaliseQuarterTurn(mapped.orientation), finalRound == expectedRound ? 0 : 1);
    }

    static GeometryMeasurement betweenWays(WaySnapshot before, Way after) {
        if (before == null || after == null) {
            return null;
        }
        Snapshot current = Snapshot.fromGeographic(after);
        Snapshot original = before.snapshot;
        if (current == null || original == null) {
            return null;
        }
        double dx = (current.centreX - original.centreX) / Math.max(1e-12, original.width);
        double dy = (current.centreY - original.centreY) / Math.max(1e-12, original.height);
        double widthScale = current.width / Math.max(1e-12, original.width);
        double heightScale = current.height / Math.max(1e-12, original.height);
        double rotation = normaliseQuarterTurn(current.orientation - original.orientation);
        double shapeDifference = Math.min(1.0,
                Math.abs(current.circularity - original.circularity)
                        + (current.points == original.points ? 0 : 0.35));
        return new GeometryMeasurement(dx, dy, widthScale, heightScale, rotation,
                shapeDifference);
    }

    double getCentreX() { return centreX; }
    double getCentreY() { return centreY; }
    double getWidthScale() { return widthScale; }
    double getHeightScale() { return heightScale; }
    double getRotation() { return rotation; }
    double getShapeDifference() { return shapeDifference; }

    static final class WaySnapshot {
        private final Snapshot snapshot;

        private WaySnapshot(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        static WaySnapshot capture(Way way) {
            Snapshot snapshot = Snapshot.fromGeographic(way);
            return snapshot == null ? null : new WaySnapshot(snapshot);
        }
    }

    private static final class Snapshot {
        private final double centreX;
        private final double centreY;
        private final double width;
        private final double height;
        private final double area;
        private final double orientation;
        private final double circularity;
        private final int points;

        Snapshot(double[] x, double[] y, int count) {
            points = count;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double twiceArea = 0;
            double perimeter = 0;
            double longest = -1;
            double longestAngle = 0;
            for (int index = 0; index < count; index++) {
                int next = (index + 1) % count;
                minX = Math.min(minX, x[index]);
                minY = Math.min(minY, y[index]);
                maxX = Math.max(maxX, x[index]);
                maxY = Math.max(maxY, y[index]);
                twiceArea += x[index] * y[next] - x[next] * y[index];
                double edgeX = x[next] - x[index];
                double edgeY = y[next] - y[index];
                double length = Math.hypot(edgeX, edgeY);
                perimeter += length;
                if (length > longest) {
                    longest = length;
                    longestAngle = Math.atan2(edgeY, edgeX);
                }
            }
            width = Math.max(1e-12, maxX - minX);
            height = Math.max(1e-12, maxY - minY);
            centreX = (minX + maxX) / 2.0;
            centreY = (minY + maxY) / 2.0;
            area = Math.max(1e-20, Math.abs(twiceArea) / 2.0);
            orientation = longestAngle;
            circularity = Math.max(0, Math.min(1, 4 * Math.PI * area
                    / Math.max(1e-20, perimeter * perimeter)));
        }

        static Snapshot fromPixels(Way way, MapView mapView) {
            List<Node> nodes = usableNodes(way);
            if (nodes.size() < 3) {
                return null;
            }
            double[] x = new double[nodes.size()];
            double[] y = new double[nodes.size()];
            for (int index = 0; index < nodes.size(); index++) {
                Point point = mapView.getPoint(nodes.get(index));
                x[index] = point.x;
                y[index] = point.y;
            }
            return new Snapshot(x, y, nodes.size());
        }

        static Snapshot fromGeographic(Way way) {
            List<Node> nodes = usableNodes(way);
            if (nodes.size() < 3) {
                return null;
            }
            double meanLatitude = 0;
            for (Node node : nodes) {
                meanLatitude += node.lat();
            }
            meanLatitude /= nodes.size();
            double longitudeScale = Math.cos(Math.toRadians(meanLatitude));
            double[] x = new double[nodes.size()];
            double[] y = new double[nodes.size()];
            for (int index = 0; index < nodes.size(); index++) {
                x[index] = nodes.get(index).lon() * longitudeScale;
                y[index] = nodes.get(index).lat();
            }
            return new Snapshot(x, y, nodes.size());
        }

        private static List<Node> usableNodes(Way way) {
            List<Node> nodes = way.getNodes();
            int count = nodes.size();
            if (count > 1 && TaskBoundaryGeometry.sameLocation(nodes.get(0), nodes.get(count - 1))) {
                count--;
            }
            return nodes.subList(0, Math.max(0, count));
        }
    }

    private static double normaliseQuarterTurn(double angle) {
        double period = Math.PI / 2.0;
        double value = angle % period;
        if (value > period / 2) value -= period;
        if (value < -period / 2) value += period;
        return value;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
