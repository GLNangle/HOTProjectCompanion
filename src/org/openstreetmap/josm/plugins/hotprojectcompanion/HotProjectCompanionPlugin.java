package org.openstreetmap.josm.plugins.hotprojectcompanion;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/** Entry point for the HOT Project Companion JOSM plugin. */
public final class HotProjectCompanionPlugin extends Plugin {
    private CompanionPanel companionPanel;

    public HotProjectCompanionPlugin(PluginInformation info) {
        super(info);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            companionPanel = new CompanionPanel();
            newFrame.addToggleDialog(companionPanel);
        } else if (oldFrame != null && newFrame == null) {
            companionPanel = null;
        }
    }
}
