package org.openstreetmap.josm.plugins.hotprojectcompanion;

import javax.swing.JButton;

/** Creates sidebar buttons that do not steal focus during mouse clicks. */
final class SidebarButtons {
    private SidebarButtons() {
    }

    static JButton create(String text) {
        JButton button = new JButton(text);
        // Prevent mouse focus from asking the enclosing scroll pane to reveal a
        // newly focused button. Keyboard focus traversal remains available.
        button.setRequestFocusEnabled(false);
        return button;
    }
}
