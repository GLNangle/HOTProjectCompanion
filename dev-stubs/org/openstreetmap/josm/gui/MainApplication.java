package org.openstreetmap.josm.gui;

import org.openstreetmap.josm.gui.layer.MainLayerManager;

/** Compile-only JOSM API stub; never packaged. */
public final class MainApplication {
    private static final MainLayerManager LAYER_MANAGER = new MainLayerManager();
    private static final MapFrame MAP = new MapFrame();

    private MainApplication() {
    }

    public static MainLayerManager getLayerManager() {
        return LAYER_MANAGER;
    }

    public static MapFrame getMap() {
        return MAP;
    }
}
