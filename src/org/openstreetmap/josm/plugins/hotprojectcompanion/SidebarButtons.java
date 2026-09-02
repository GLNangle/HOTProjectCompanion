package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** Creates sidebar buttons that do not steal focus during mouse clicks. */
final class SidebarButtons {
    private static final String INITIAL_CLICK_HANDLED =
            "hotprojectcompanion.initialClickHandled";

    private SidebarButtons() {
    }

    static JButton create(String text) {
        JButton button = new JButton(text);
        // Prevent mouse focus from asking the enclosing scroll pane to reveal a
        // newly focused button. Keyboard focus traversal remains available.
        button.setRequestFocusEnabled(false);
        installInitialViewportGuard(button);
        return button;
    }

    /** Preserve the containing sidebar viewport during its first mouse/layout cycle. */
    private static void installInitialViewportGuard(JButton button) {
        button.addMouseListener(new MouseAdapter() {
            private JViewport viewport;
            private Point position;

            @Override
            public void mousePressed(MouseEvent event) {
                JViewport candidate = (JViewport) SwingUtilities.getAncestorOfClass(
                        JViewport.class, button);
                if (candidate == null || Boolean.TRUE.equals(
                        candidate.getClientProperty(INITIAL_CLICK_HANDLED))) {
                    return;
                }
                candidate.putClientProperty(INITIAL_CLICK_HANDLED, Boolean.TRUE);
                viewport = candidate;
                position = candidate.getViewPosition();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (viewport != null && position != null) {
                    restoreViewport(viewport, position, 3);
                    viewport = null;
                    position = null;
                }
            }
        });
    }

    private static void restoreViewport(JViewport viewport, Point requested, int passes) {
        SwingUtilities.invokeLater(() -> {
            Dimension view = viewport.getViewSize();
            Dimension extent = viewport.getExtentSize();
            int x = Math.max(0, Math.min(requested.x, Math.max(0, view.width - extent.width)));
            int y = Math.max(0, Math.min(requested.y, Math.max(0, view.height - extent.height)));
            viewport.setViewPosition(new Point(x, y));
            if (passes > 1) {
                restoreViewport(viewport, requested, passes - 1);
            }
        });
    }
}
