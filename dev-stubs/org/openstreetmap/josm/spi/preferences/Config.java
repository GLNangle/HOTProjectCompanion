package org.openstreetmap.josm.spi.preferences;

import java.util.HashMap;
import java.util.Map;

public final class Config {
    private static final Map<String, String> VALUES = new HashMap<>();
    private static final IPreferences PREFS = new IPreferences() {
        @Override
        public String get(String key, String defaultValue) {
            return VALUES.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean put(String key, String value) {
            VALUES.put(key, value);
            return true;
        }
    };

    private Config() {
    }

    public static IPreferences getPref() {
        return PREFS;
    }
}
