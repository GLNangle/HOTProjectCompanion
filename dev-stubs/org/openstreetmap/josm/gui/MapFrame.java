package org.openstreetmap.josm.gui;

import org.openstreetmap.josm.gui.dialogs.FilterDialog;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;

public class MapFrame {
    public final MapView mapView = new MapView();
    public final FilterDialog filterDialog = new FilterDialog();

    public IconToggleButton addToggleDialog(ToggleDialog dialog) {
        return null;
    }
}
