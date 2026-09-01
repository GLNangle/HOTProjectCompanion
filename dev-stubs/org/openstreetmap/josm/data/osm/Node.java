package org.openstreetmap.josm.data.osm;

/** Compile-only JOSM API stub; never packaged. */
public class Node {
    private final double lat;
    private final double lon;

    public Node() {
        this(Double.NaN, Double.NaN);
    }

    public Node(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double lat() {
        return lat;
    }

    public double lon() {
        return lon;
    }
}
