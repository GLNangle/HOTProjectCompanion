package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** A remote image referenced by the project's mapping instructions. */
final class InstructionImage {
    private final String url;
    private final String description;

    InstructionImage(String url, String description) {
        this.url = url;
        this.description = description == null ? "" : description.trim();
    }

    String getUrl() {
        return url;
    }

    String getDescription() {
        return description;
    }
}
