package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

final class SidebarButtonsTest {
    private SidebarButtonsTest() {
    }

    static void run() {
        JButton button = SidebarButtons.create("Review");
        require(!button.isRequestFocusEnabled(),
                "mouse clicks should not transfer focus into sidebar buttons");
        require(button.isFocusable(),
                "buttons should remain available to keyboard focus traversal");
        verifyInitialViewportGuard(button);
    }

    private static void verifyInitialViewportGuard(JButton button) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                JPanel view = new JPanel();
                view.setPreferredSize(new Dimension(400, 1200));
                view.setSize(400, 1200);
                view.add(button);
                JViewport viewport = new JViewport();
                viewport.setSize(300, 200);
                viewport.setExtentSize(new Dimension(300, 200));
                viewport.setView(view);
                viewport.setViewSize(new Dimension(400, 1200));
                viewport.setViewPosition(new Point(0, 400));

                MouseEvent pressed = new MouseEvent(button, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), 0, 2, 2, 1, false);
                for (MouseListener listener : button.getMouseListeners()) {
                    listener.mousePressed(pressed);
                }
                viewport.setViewPosition(new Point(0, 0));
                MouseEvent released = new MouseEvent(button, MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), 0, 2, 2, 1, false);
                for (MouseListener listener : button.getMouseListeners()) {
                    listener.mouseReleased(released);
                }
                button.putClientProperty("test.viewport", viewport);
            });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
            JViewport viewport = (JViewport) button.getClientProperty("test.viewport");
            require(viewport.getViewPosition().y == 400,
                    "the first sidebar mouse interaction should preserve scroll position");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
