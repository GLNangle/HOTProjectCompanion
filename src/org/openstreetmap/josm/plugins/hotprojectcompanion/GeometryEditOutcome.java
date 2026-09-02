package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.EnumSet;

/** Mapper-confirmed geometry changes, kept separate from building-presence evidence. */
enum GeometryEditOutcome {
    MOVED,
    ROTATED,
    RESHAPED,
    RESIZED;

    static EnumSet<GeometryEditOutcome> none() {
        return EnumSet.noneOf(GeometryEditOutcome.class);
    }
}
