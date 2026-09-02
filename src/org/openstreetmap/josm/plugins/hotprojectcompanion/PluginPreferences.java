package org.openstreetmap.josm.plugins.hotprojectcompanion;

import org.openstreetmap.josm.spi.preferences.Config;

/** Small adapter that keeps all persistent plugin state in JOSM preferences. */
final class PluginPreferences {
    static final String PREFIX = "hotprojectcompanion.";

    private PluginPreferences() {
    }

    static Store josm() {
        return new Store() {
            @Override
            public String get(String key, String defaultValue) {
                return Config.getPref().get(key, defaultValue);
            }

            @Override
            public void put(String key, String value) {
                Config.getPref().put(key, value);
            }
        };
    }

    interface Store {
        String get(String key, String defaultValue);

        void put(String key, String value);
    }
}
