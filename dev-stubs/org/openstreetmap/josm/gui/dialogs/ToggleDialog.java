package org.openstreetmap.josm.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collection;

import javax.swing.JPanel;

import org.openstreetmap.josm.tools.Shortcut;

public class ToggleDialog extends JPanel {
    public ToggleDialog(String name, String iconName, String tooltip, Shortcut shortcut,
            int preferredHeight, boolean defaultShow) {
    }

    protected Component createLayout(Component data, boolean scroll, Collection<?> buttons) {
        setLayout(new BorderLayout());
        add(data, BorderLayout.CENTER);
        return data;
    }

    public void hideNotify() {
    }

    public void destroy() {
    }
}
