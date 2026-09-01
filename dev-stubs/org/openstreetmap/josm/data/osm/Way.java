package org.openstreetmap.josm.data.osm;

import java.util.Collections;
import java.util.List;

/** Compile-only JOSM API stub; never packaged. */
public class Way {
    private final List<Node> nodes;
    private final boolean closed;

    public Way() {
        this(Collections.emptyList(), false);
    }

    public Way(List<Node> nodes, boolean closed) {
        this.nodes = nodes;
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public boolean hasIncompleteNodes() {
        return false;
    }

    public boolean isDeleted() {
        return false;
    }

    public boolean isIncomplete() {
        return false;
    }

    public boolean hasKey(String key) {
        return false;
    }

    public String get(String key) {
        return null;
    }
}
