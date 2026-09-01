package org.openstreetmap.josm.plugins.hotprojectcompanion;

import javax.swing.JButton;

final class SidebarButtonsTest {
    private SidebarButtonsTest() {
    }

    static void run() {
        JButton button = SidebarButtons.create("Review");
        require(!button.isRequestFocusEnabled(),
                "mouse clicks should not transfer focus into sidebar buttons");
        require(button.isFocusable(),
                "buttons should remain available to keyboard focus traversal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
