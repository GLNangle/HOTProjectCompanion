package org.openstreetmap.josm.plugins.hotprojectcompanion;

import org.openstreetmap.josm.data.osm.Filter;
import org.openstreetmap.josm.gui.dialogs.FilterTableModel;

final class MappedBuildingFilterTest {
    private MappedBuildingFilterTest() {
    }

    static void run() {
        FilterTableModel model = new FilterTableModel();
        Filter mapperFilter = new Filter();
        mapperFilter.text = "highway=*";
        mapperFilter.enable = true;
        model.addFilter(mapperFilter);

        MappedBuildingFilter temporary = new MappedBuildingFilter();
        temporary.hide(model);
        require(temporary.isHidden(), "building filter should be active");
        require(model.getRowCount() == 2, "temporary filter should be added");
        Filter added = model.getValue(1);
        require("building=*".equals(added.text), "building query");
        require(added.enable, "temporary filter should be enabled");
        require(added.hiding, "temporary filter should fully hide matches");

        temporary.show();
        require(!temporary.isHidden(), "building filter should be inactive");
        require(model.getRowCount() == 1, "only temporary filter should be removed");
        require(model.getValue(0) == mapperFilter,
                "mapper's existing filter should be preserved");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
