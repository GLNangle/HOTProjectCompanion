package org.openstreetmap.josm.spi.preferences;

public interface IPreferences {
    String get(String key, String defaultValue);
    boolean put(String key, String value);
}
