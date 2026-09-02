package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** A sidebar section whose expanded state is retained in JOSM preferences. */
final class CollapsibleSection extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String KEY_PREFIX = PluginPreferences.PREFIX + "section.";

    private final String title;
    private final String preferenceKey;
    private final PluginPreferences.Store preferences;
    private final JButton toggleButton;
    private final JComponent body;
    private boolean expanded;

    CollapsibleSection(String id, String title, JComponent body, boolean expandedByDefault) {
        this(id, title, body, expandedByDefault, PluginPreferences.josm());
    }

    CollapsibleSection(String id, String title, JComponent body, boolean expandedByDefault,
            PluginPreferences.Store preferences) {
        this.title = title;
        this.body = body;
        this.preferences = preferences;
        this.preferenceKey = KEY_PREFIX + id + ".expanded";
        this.expanded = Boolean.parseBoolean(preferences.get(preferenceKey,
                Boolean.toString(expandedByDefault)));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                getForeground().brighter()));

        toggleButton = SidebarButtons.create("");
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setOpaque(false);
        toggleButton.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
        toggleButton.addActionListener(event -> setExpanded(!expanded, true));
        add(toggleButton);

        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(body);
        add(Box.createVerticalStrut(2));
        applyState();
    }

    private void setExpanded(boolean value, boolean save) {
        expanded = value;
        if (save) {
            preferences.put(preferenceKey, Boolean.toString(value));
        }
        applyState();
    }

    private void applyState() {
        body.setVisible(expanded);
        toggleButton.setText((expanded ? "▾ " : "▸ ") + title);
        String action = expanded ? "Collapse " : "Expand ";
        toggleButton.setToolTipText(action + title);
        toggleButton.getAccessibleContext().setAccessibleName(action + title);
        revalidate();
        repaint();
    }

    boolean isExpanded() {
        return expanded;
    }

    JButton getToggleButton() {
        return toggleButton;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
