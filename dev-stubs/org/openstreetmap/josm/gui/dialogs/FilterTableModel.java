package org.openstreetmap.josm.gui.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.Filter;

/** Compile-only JOSM API stub; never packaged. */
public class FilterTableModel {
    private final List<Filter> filters = new ArrayList<>();

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public void removeFilter(int rowIndex) {
        filters.remove(rowIndex);
    }

    public void executeFilters(boolean force) {
    }

    public int getRowCount() {
        return filters.size();
    }

    public Filter getValue(int rowIndex) {
        return filters.get(rowIndex);
    }

    public List<Filter> getFilters() {
        return Collections.unmodifiableList(filters);
    }
}
