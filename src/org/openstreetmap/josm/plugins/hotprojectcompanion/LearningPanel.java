package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Persistent learning summary and task-status synchronisation controls. */
final class LearningPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault());

    private final LearningStore store;
    private final HotTaskingManagerClient client;
    private final JLabel summary = label("");
    private final JLabel state = label("Learning stays on this computer.");
    private final JButton historyButton = SidebarButtons.create(tr("Learning history…"));
    private final JButton syncButton = SidebarButtons.create(tr("Sync validation outcomes"));

    LearningPanel(LearningStore store, HotTaskingManagerClient client) {
        this.store = store;
        this.client = client;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createTitledBorder(tr("Local learning")));
        add(summary);
        add(Box.createVerticalStrut(4));
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyButton.addActionListener(event -> showHistory());
        syncButton.addActionListener(event -> syncStatuses());
        buttons.add(historyButton);
        buttons.add(Box.createHorizontalStrut(5));
        buttons.add(syncButton);
        add(buttons);
        add(Box.createVerticalStrut(4));
        state.setForeground(new Color(90, 90, 90));
        add(state);
        refresh();
        if (!store.recordsForSync().isEmpty()) {
            SwingUtilities.invokeLater(this::syncStatuses);
        }
    }

    void refresh() {
        LearningProfile profile = store.profile();
        summary.setText("<html><div style='width:300px'><b>"
                + profile.getPositiveCount() + " mapped building examples</b> · "
                + profile.getNegativeCount() + " rejected examples<br>"
                + store.awaitingCount() + " previous task(s) awaiting a validation outcome"
                + "</div></html>");
        syncButton.setEnabled(!store.recordsForSync().isEmpty());
    }

    private void syncStatuses() {
        List<LearningStore.TaskRecord> records = store.recordsForSync();
        if (records.isEmpty()) {
            state.setText("No learning tasks have been recorded yet.");
            return;
        }
        syncButton.setEnabled(false);
        state.setForeground(new Color(0, 90, 145));
        state.setText("Checking saved task numbers in the Tasking Manager…");
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                int updated = 0;
                int failed = 0;
                for (LearningStore.TaskRecord record : records) {
                    try {
                        TaskReference reference = TaskReference.forHotTask(record.getProject(),
                                record.getTask());
                        String status = client.loadTaskStatus(reference);
                        if (!status.equalsIgnoreCase(record.getStatus())) {
                            updated++;
                        }
                        store.setTaskStatus(reference, status);
                    } catch (Exception exception) {
                        failed++;
                    }
                }
                return new int[] {updated, failed};
            }

            @Override
            protected void done() {
                syncButton.setEnabled(true);
                try {
                    int[] result = get();
                    int updated = result[0];
                    int failed = result[1];
                    refresh();
                    state.setForeground(failed == 0 ? new Color(0, 105, 45)
                            : new Color(150, 65, 0));
                    state.setText(failed > 0
                            ? updated + " update(s) found; " + failed
                                    + " task(s) could not be checked."
                            : updated == 0
                            ? "Task statuses are up to date."
                            : updated + " validation outcome/status update(s) found.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSyncError("The status check was interrupted.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showSyncError(cause == null || cause.getMessage() == null
                            ? "Validation outcomes could not be checked."
                            : cause.getMessage());
                }
            }
        }.execute();
    }

    private void showSyncError(String message) {
        state.setForeground(new Color(170, 35, 35));
        state.setText("Could not sync validation outcomes: " + escapeHtml(message));
        refresh();
    }

    private void showHistory() {
        StringBuilder text = new StringBuilder();
        text.append("LOCAL LEARNING\n\n")
                .append("Mapped building examples: ").append(store.profile().getPositiveCount())
                .append("\nRejected examples: ").append(store.profile().getNegativeCount())
                .append("\n\nTASK HISTORY\n");
        List<LearningStore.TaskRecord> records = store.records();
        if (records.isEmpty()) {
            text.append("No task decisions recorded yet.\n");
        } else {
            for (LearningStore.TaskRecord record : records) {
                text.append("Project ").append(record.getProject()).append(" · Task ")
                        .append(record.getTask()).append("\n  ")
                        .append(record.getStatus()).append(" · ")
                        .append(record.getMapped()).append(" mapped · ")
                        .append(record.getRejected()).append(" rejected · ")
                        .append(DATE.format(Instant.ofEpochSecond(record.getUpdated())))
                        .append("\n");
            }
        }
        text.append("\nThe current release learns from mapper-confirmed and rejected imagery ")
                .append("examples. Sync checks HOT task states without requiring the task to be ")
                .append("reopened. Object-level validator edit matching is deliberately not applied ")
                .append("until uploaded OSM IDs can be associated reliably.\n\n")
                .append("Validator evidence will be weighted more strongly than mapper evidence, ")
                .append("while ambiguous edits will be ignored.");
        JTextArea area = new JTextArea(text.toString(), 20, 48);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(540, 390));
        JOptionPane.showMessageDialog(this, scroll, tr("HOT Project Companion — learning history"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
