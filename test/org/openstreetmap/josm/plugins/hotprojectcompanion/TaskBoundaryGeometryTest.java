package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.Arrays;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

/** Dependency-free tests for HOT's coordinate-closed task boundary representation. */
public final class TaskBoundaryGeometryTest {
    private TaskBoundaryGeometryTest() {
    }

    public static void main(String[] args) {
        Node first = new Node(41.4000000000, 2.2000000000);
        Way hotBoundary = new Way(Arrays.asList(first,
                new Node(41.4000000000, 2.2100000000),
                new Node(41.4100000000, 2.2100000000),
                new Node(41.4100000000, 2.2000000000),
                new Node(41.4000000000, 2.2000000000)), false);
        require(TaskBoundaryGeometry.isClosed(hotBoundary),
                "coordinate-closed HOT boundary with different node IDs");

        Way normalClosed = new Way(Arrays.asList(first,
                new Node(41.4000000000, 2.2100000000),
                new Node(41.4100000000, 2.2100000000), first), true);
        require(TaskBoundaryGeometry.isClosed(normalClosed), "normally closed JOSM way");

        Way open = new Way(Arrays.asList(first,
                new Node(41.4000000000, 2.2100000000),
                new Node(41.4100000000, 2.2100000000),
                new Node(41.4090000000, 2.2000000000)), false);
        require(!TaskBoundaryGeometry.isClosed(open), "genuinely open way remains rejected");

        System.out.println("TaskBoundaryGeometryTest: all tests passed");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}
