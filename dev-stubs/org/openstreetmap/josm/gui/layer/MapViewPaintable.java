package org.openstreetmap.josm.gui.layer;

import java.awt.Graphics2D;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapView;

/** Compile-only JOSM API stub; never packaged. */
public interface MapViewPaintable {
    void paint(Graphics2D graphics, MapView mapView, Bounds bounds);
}
