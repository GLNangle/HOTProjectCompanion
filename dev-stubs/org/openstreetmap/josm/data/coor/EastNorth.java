package org.openstreetmap.josm.data.coor;

/** Compile-only JOSM API stub; never packaged. */
public class EastNorth {
    private final double east;
    private final double north;

    public EastNorth() {
        this(0, 0);
    }

    public EastNorth(double east, double north) {
        this.east = east;
        this.north = north;
    }

    public double east() {
        return east;
    }

    public double north() {
        return north;
    }
}
