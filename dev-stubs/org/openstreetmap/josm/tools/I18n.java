package org.openstreetmap.josm.tools;

import java.text.MessageFormat;

public final class I18n {
    private I18n() {
    }

    public static String tr(String text, Object... arguments) {
        return MessageFormat.format(text, arguments);
    }
}
