package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import javax.swing.SwingWorker;

/** Persistent learning summary and task-status synchronisation controls. */
final class LearningPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault());

    private final LearningStore store;
    private final HotTaskingManagerClient client;
    private final JLabel summary = label("");
    private final JLabel state = wrappingLabel("Learning stays on this computer.");
    private final JButton historyButton = SidebarButtons.create(tr("Learning history…"));
    private final JButton syncButton = SidebarButtons.create(tr("Check task statuses"));

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
    }

    void refresh() {
        LearningProfile profile = store.profile();
        int[] geometry = store.geometryTotals();
        summary.setText("<html><div style='width:300px'><b>"
                + profile.getPositiveCount() + " mapped building examples</b> · "
                + profile.getNegativeCount() + " rejected examples<br>"
                + geometry[0] + " moved · " + geometry[1] + " rotated · "
                + geometry[2] + " reshaped · " + geometry[3] + " resized<br>"
                + store.awaitingCount() + " saved task(s) currently marked as awaiting validation"
                + "</div></html>");
        syncButton.setEnabled(!store.recordsForSync().isEmpty());
    }

    private void syncStatuses() {
        List<LearningStore.TaskRecord> records = store.recordsForSync();
        if (records.isEmpty()) {
            setState("No recent learning tasks are available to check.", new Color(90, 90, 90));
            return;
        }
        syncButton.setEnabled(false);
        setState("Checking saved task numbers in the Tasking Manager…", new Color(0, 90, 145));
        new SwingWorker<SyncReport, Void>() {
            @Override
            protected SyncReport doInBackground() {
                List<TaskStatusTransition> transitions = new ArrayList<>();
                int failed = 0;
                for (LearningStore.TaskRecord record : records) {
                    try {
                        TaskReference reference = TaskReference.forHotTask(record.getProject(),
                                record.getTask());
                        String status = client.loadTaskStatus(reference);
                        TaskStatusTransition transition = TaskStatusTransition.between(record, status);
                        if (transition.getKind() != TaskStatusTransition.Kind.UNCHANGED) {
                            transitions.add(transition);
                        }
                        store.setTaskStatus(reference, status);
                    } catch (Exception exception) {
                        failed++;
                    }
                }
                return new SyncReport(records.size(), failed, transitions);
            }

            @Override
            protected void done() {
                syncButton.setEnabled(true);
                try {
                    SyncReport result = get();
                    refresh();
                    showSyncReport(result);
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
        setState("Could not check task statuses: " + message, new Color(170, 35, 35));
        refresh();
    }

    private void showSyncReport(SyncReport report) {
        int validationOutcomes = report.count(TaskStatusTransition.Kind.VALIDATION_OUTCOME);
        int statusChanges = report.count(TaskStatusTransition.Kind.STATUS_CHANGE);
        int initialStatuses = report.count(TaskStatusTransition.Kind.INITIAL_STATUS);
        StringBuilder text = new StringBuilder();
        if (validationOutcomes == 0) {
            text.append("<b>No new validation outcomes were found.</b>");
        } else {
            text.append("<b>").append(validationOutcomes)
                    .append(validationOutcomes == 1
                            ? " validation outcome found:</b>" : " validation outcomes found:</b>");
            appendTransitions(text, report.transitions,
                    TaskStatusTransition.Kind.VALIDATION_OUTCOME, true);
        }
        if (statusChanges > 0) {
            text.append("<br><b>Other status changes:</b>");
            appendTransitions(text, report.transitions,
                    TaskStatusTransition.Kind.STATUS_CHANGE, true);
        }
        if (initialStatuses > 0) {
            text.append("<br><b>Initial public status recorded:</b>");
            appendTransitions(text, report.transitions,
                    TaskStatusTransition.Kind.INITIAL_STATUS, false);
        }
        int checked = report.checked - report.failed;
        text.append("<br>Checked ").append(checked).append(" task")
                .append(checked == 1 ? "" : "s").append(" using public Tasking Manager data.");
        if (report.failed > 0) {
            text.append(" ").append(report.failed).append(" task")
                    .append(report.failed == 1 ? " could" : "s could").append(" not be checked.");
        }
        setStateHtml(text.toString(), report.failed == 0
                ? new Color(0, 105, 45) : new Color(150, 65, 0));
    }

    private static void appendTransitions(StringBuilder text,
            List<TaskStatusTransition> transitions, TaskStatusTransition.Kind kind,
            boolean showPrevious) {
        for (TaskStatusTransition transition : transitions) {
            if (transition.getKind() != kind) {
                continue;
            }
            text.append("<br>Project ").append(transition.getProject())
                    .append(" · Task ").append(transition.getTask()).append(": ");
            if (showPrevious) {
                text.append(escapeHtml(TaskStatusTransition.display(transition.getPrevious())))
                        .append(" → ");
            }
            text.append(escapeHtml(TaskStatusTransition.display(transition.getCurrent())));
        }
    }

    private void setState(String text, Color colour) {
        setStateHtml(escapeHtml(text), colour);
    }

    private void setStateHtml(String html, Color colour) {
        state.setForeground(colour);
        state.setText("<html><div style='width:300px'>" + html + "</div></html>");
    }

    private void showHistory() {
        int[] geometry = store.geometryTotals();
        StringBuilder text = new StringBuilder();
        text.append("LOCAL LEARNING\n\n")
                .append("Mapped building examples: ").append(store.profile().getPositiveCount())
                .append("\nRejected examples: ").append(store.profile().getNegativeCount())
                .append("\nGeometry corrections: ")
                .append(geometry[0]).append(" moved · ")
                .append(geometry[1]).append(" rotated · ")
                .append(geometry[2]).append(" reshaped · ")
                .append(geometry[3]).append(" resized")
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
                        .append(record.getRejected()).append(" rejected")
                        .append("\n  Geometry edits: ")
                        .append(record.getMoved()).append(" moved · ")
                        .append(record.getRotated()).append(" rotated · ")
                        .append(record.getReshaped()).append(" reshaped · ")
                        .append(record.getResized()).append(" resized · ")
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

    private static JLabel wrappingLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:300px'>" + escapeHtml(text)
                + "</div></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static final class SyncReport {
        private final int checked;
        private final int failed;
        private final List<TaskStatusTransition> transitions;

        SyncReport(int checked, int failed, List<TaskStatusTransition> transitions) {
            this.checked = checked;
            this.failed = failed;
            this.transitions = transitions;
        }

        int count(TaskStatusTransition.Kind kind) {
            int count = 0;
            for (TaskStatusTransition transition : transitions) {
                if (transition.getKind() == kind) {
                    count++;
                }
            }
            return count;
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
