package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.HashMap;
import java.util.Map;

/** Checks that the dock/undock hint is shown once and remains dismissed. */
public final class DetachTipPanelTest {
    private DetachTipPanelTest() {
    }

    public static void main(String[] args) {
        MemoryStore preferences = new MemoryStore();
        DetachTipPanel first = new DetachTipPanel(preferences);
        require(first.isVisible(), "the separate-window tip should appear before dismissal");

        first.getDismissButton().doClick();
        require(!first.isVisible(), "the tip should hide immediately after dismissal");
        require(DetachTipPanel.isDismissed(preferences),
                "dismissal should be saved in plugin preferences");

        DetachTipPanel reopened = new DetachTipPanel(preferences);
        require(!reopened.isVisible(), "the dismissed tip should stay hidden after restart");
        System.out.println("DetachTipPanelTest passed");
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
