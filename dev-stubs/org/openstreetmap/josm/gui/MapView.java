package org.openstreetmap.josm.gui;

import java.awt.Point;
import java.awt.Graphics2D;

import javax.swing.JComponent;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.Layer;

/** Compile-only JOSM API stub; never packaged. */
public class MapView extends JComponent {
    private static final long serialVersionUID = 1L;

    public Point getPoint(Node node) {
        return new Point();
    }

    public EastNorth getEastNorth(int x, int y) {
        return new EastNorth();
    }

    public Point getPoint(EastNorth location) {
        return new Point();
    }

    public ProjectionBounds getProjectionBounds(java.awt.Rectangle rectangle) {
        return new ProjectionBounds();
    }

    public double getDist100Pixel(boolean alwaysPositive) {
        return 100.0;
    }

    public EastNorth getCenter() {
        return new EastNorth();
    }

    public double getScale() {
        return 1.0;
    }

    public void paintLayer(Layer layer, Graphics2D graphics) {
        // Compile-only stub.
    }

    public void zoomTo(EastNorth location) {
        // Compile-only stub.
    }

    public void zoomTo(EastNorth location, double scale) {
        // Compile-only stub.
    }

    public void zoomToFactor(EastNorth location, double factor) {
        // Compile-only stub.
    }

    public void zoomTo(ProjectionBounds bounds) {
        // Compile-only stub.
    }

    public boolean addTemporaryLayer(MapViewPaintable paintable) {
        return true;
    }

    public boolean removeTemporaryLayer(MapViewPaintable paintable) {
        return true;
    }
}
