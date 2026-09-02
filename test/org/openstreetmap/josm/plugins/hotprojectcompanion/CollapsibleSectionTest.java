package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

/** Checks independent, persistent sidebar section expansion. */
public final class CollapsibleSectionTest {
    private CollapsibleSectionTest() {
    }

    public static void main(String[] args) {
        MemoryStore preferences = new MemoryStore();
        CollapsibleSection first = new CollapsibleSection(
                "building-check", "Building check", new JPanel(), false, preferences);
        require(!first.isExpanded(), "optional section should honour its collapsed default");

        first.getToggleButton().doClick();
        require(first.isExpanded(), "clicking the header should expand the section");
        require("true".equals(preferences.get(
                "hotprojectcompanion.section.building-check.expanded", "")),
                "expanded state should be saved in plugin preferences");

        CollapsibleSection reopened = new CollapsibleSection(
                "building-check", "Building check", new JPanel(), false, preferences);
        require(reopened.isExpanded(), "a restarted panel should restore the saved state");

        CollapsibleSection independent = new CollapsibleSection(
                "upload-details", "Upload details", new JPanel(), false, preferences);
        require(!independent.isExpanded(), "section preference keys should be independent");
        require(reopened.getToggleButton().getAccessibleContext().getAccessibleName()
                .startsWith("Collapse"), "the toggle should expose its action accessibly");
        System.out.println("CollapsibleSectionTest: all tests passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryStore implements PluginPreferences.Store {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
