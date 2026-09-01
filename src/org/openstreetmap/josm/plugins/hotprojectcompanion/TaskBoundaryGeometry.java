package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.List;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

/** Handles HOT boundaries whose closing coordinate uses a different temporary node ID. */
final class TaskBoundaryGeometry {
    private static final double COORDINATE_TOLERANCE = 1e-10;

    private TaskBoundaryGeometry() {
    }

    static boolean isClosed(Way way) {
        if (way == null || way.getNodes().size() < 4) {
            return false;
        }
        if (way.isClosed()) {
            return true;
        }
        List<Node> nodes = way.getNodes();
        return sameLocation(nodes.get(0), nodes.get(nodes.size() - 1));
    }

    static boolean sameLocation(Node first, Node second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null
                || !Double.isFinite(first.lat()) || !Double.isFinite(first.lon())
                || !Double.isFinite(second.lat()) || !Double.isFinite(second.lon())) {
            return false;
        }
        return Math.abs(first.lat() - second.lat()) <= COORDINATE_TOLERANCE
                && Math.abs(first.lon() - second.lon()) <= COORDINATE_TOLERANCE;
    }
}
