package org.openstreetmap.josm.data;

import org.openstreetmap.josm.data.coor.EastNorth;

/** Compile-only JOSM API stub; never packaged. */
public class ProjectionBounds {
    private final EastNorth min;
    private final EastNorth max;

    public ProjectionBounds() {
        this(0, 0, 0, 0);
    }

    public ProjectionBounds(double minEast, double minNorth,
            double maxEast, double maxNorth) {
        min = new EastNorth(minEast, minNorth);
        max = new EastNorth(maxEast, maxNorth);
    }

    public EastNorth getMin() {
        return min;
    }

    public EastNorth getMax() {
        return max;
    }
}
