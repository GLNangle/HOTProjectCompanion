package org.openstreetmap.josm.plugins.hotprojectcompanion;

import org.openstreetmap.josm.data.osm.Filter;
import org.openstreetmap.josm.gui.dialogs.FilterTableModel;

/** Manages the companion's temporary, display-only building filter. */
final class MappedBuildingFilter {
    private Filter filter;
    private FilterTableModel model;

    boolean isHidden() {
        return filter != null;
    }

    void hide(FilterTableModel filterModel) {
        if (filter != null) {
            return;
        }
        Filter temporary = new Filter();
        temporary.text = "building=*";
        temporary.enable = true;
        temporary.hiding = true;
        filterModel.addFilter(temporary);
        filterModel.executeFilters(true);
        filter = temporary;
        model = filterModel;
    }

    void show() {
        if (filter == null || model == null) {
            filter = null;
            model = null;
            return;
        }
        for (int row = model.getRowCount() - 1; row >= 0; row--) {
            if (model.getValue(row) == filter) {
                model.removeFilter(row);
                break;
            }
        }
        // Re-run every remaining filter so a mapper's own filters retain control.
        model.executeFilters(true);
        filter = null;
        model = null;
    }
}
