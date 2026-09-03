package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Dismissible first-use hint for JOSM's built-in dock/undock control. */
final class DetachTipPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String DISMISSED_KEY = PluginPreferences.PREFIX + "tip.detach.dismissed";

    private final PluginPreferences.Store preferences;
    private final JButton dismiss = SidebarButtons.create(tr("Got it"));

    DetachTipPanel() {
        this(PluginPreferences.josm());
    }

    DetachTipPanel(PluginPreferences.Store preferences) {
        super(new BorderLayout(7, 0));
        this.preferences = preferences;
        setAlignmentX(LEFT_ALIGNMENT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(170, 195, 215)),
                BorderFactory.createEmptyBorder(6, 7, 6, 7)));

        JTextArea message = new JTextArea(tr(
                "Need more map space? Use the pin icon in the panel header to open HOT Project Companion in a separate, resizable window."), 3, 28);
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);
        message.setBorder(null);
        add(message, BorderLayout.CENTER);

        dismiss.setToolTipText(tr("Hide this tip"));
        dismiss.getAccessibleContext().setAccessibleName(tr("Dismiss separate-window tip"));
        dismiss.addActionListener(event -> dismiss());
        add(dismiss, BorderLayout.EAST);

        setVisible(!isDismissed(preferences));
    }

    private void dismiss() {
        preferences.put(DISMISSED_KEY, Boolean.TRUE.toString());
        setVisible(false);
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    JButton getDismissButton() {
        return dismiss;
    }

    static boolean isDismissed(PluginPreferences.Store preferences) {
        return Boolean.parseBoolean(preferences.get(DISMISSED_KEY, Boolean.FALSE.toString()));
    }
}
