package org.openstreetmap.josm.gui;

import java.awt.Point;

import javax.swing.JComponent;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;

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

    public void zoomTo(EastNorth location) {
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
