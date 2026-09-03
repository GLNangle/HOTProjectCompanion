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
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Persistent learning summary and task-status synchronisation controls. */
final class LearningPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault());

    private final LearningStore store;
    private final HotTaskingManagerClient client;
    private final SharedLearningStore sharedStore;
    private final SharedLearningClient sharedClient;
    private final JLabel summary = label("");
    private final JLabel state = wrappingLabel("Learning stays on this computer.");
    private final JLabel sharedSummary = wrappingLabel("");
    private final JLabel sharedState = wrappingLabel("Shared learning is off.");
    private final JButton historyButton = SidebarButtons.create(tr("Learning history…"));
    private final JButton syncButton = SidebarButtons.create(tr("Check task statuses"));
    private final JCheckBox shareOptIn = new JCheckBox(tr("Contribute anonymous learning (test)"));
    private final JButton sendButton = SidebarButtons.create(tr("Send queued examples"));
    private final JButton profileButton = SidebarButtons.create(tr("Refresh shared profile"));
    private final JButton withdrawButton = SidebarButtons.create(tr("Withdraw sent examples"));
    private final JButton privacyButton = SidebarButtons.create(tr("What is shared?"));
    private boolean updatingConsent;

    LearningPanel(LearningStore store, HotTaskingManagerClient client,
            SharedLearningStore sharedStore, SharedLearningClient sharedClient) {
        this.store = store;
        this.client = client;
        this.sharedStore = sharedStore;
        this.sharedClient = sharedClient;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
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
        add(Box.createVerticalStrut(10));
        JLabel sharedHeading = label("<html><b>Shared learning — controlled test</b></html>");
        add(sharedHeading);
        add(Box.createVerticalStrut(3));
        shareOptIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        shareOptIn.setSelected(sharedStore.isEnabled());
        shareOptIn.addActionListener(event -> changeConsent());
        add(shareOptIn);
        add(Box.createVerticalStrut(3));
        add(sharedSummary);
        add(Box.createVerticalStrut(3));
        JPanel sharedButtons = new JPanel();
        sharedButtons.setLayout(new BoxLayout(sharedButtons, BoxLayout.X_AXIS));
        sharedButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendButton.addActionListener(event -> sendQueued());
        profileButton.addActionListener(event -> refreshSharedProfile());
        sharedButtons.add(sendButton);
        sharedButtons.add(Box.createHorizontalStrut(4));
        sharedButtons.add(profileButton);
        add(sharedButtons);
        JPanel privacyButtons = new JPanel();
        privacyButtons.setLayout(new BoxLayout(privacyButtons, BoxLayout.X_AXIS));
        privacyButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        privacyButton.addActionListener(event -> showPrivacyDetails());
        withdrawButton.addActionListener(event -> withdrawSent());
        privacyButtons.add(privacyButton);
        privacyButtons.add(Box.createHorizontalStrut(4));
        privacyButtons.add(withdrawButton);
        add(privacyButtons);
        add(Box.createVerticalStrut(3));
        sharedState.setForeground(new Color(90, 90, 90));
        add(sharedState);
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
        List<SharedLearningStore.Example> queued = sharedStore.queued();
        List<SharedLearningStore.Example> sent = sharedStore.sent();
        SharedLearningProfile shared = sharedStore.profile();
        String profileText = shared.isActive()
                ? "active profile v" + shared.getVersion() + " · "
                        + shared.getContributorCount() + " contributors · "
                        + shared.getSampleCount() + " validated training samples"
                : "profile " + shared.getStatus().replace('_', ' ');
        String qualityText = qualityText(shared);
        sharedSummary.setText("<html><div style='width:300px'>"
                + queued.size() + " queued locally · " + sent.size()
                + " sent and withdrawable<br>Shared " + profileText
                + (qualityText.isEmpty() ? "" : "<br>" + qualityText)
                + "</div></html>");
        shareOptIn.setSelected(sharedStore.isEnabled());
        sendButton.setEnabled(sharedStore.isEnabled() && !queued.isEmpty());
        withdrawButton.setEnabled(!sent.isEmpty());
    }

    private static String qualityText(SharedLearningProfile profile) {
        String status = profile.getQualityStatus();
        if ("passed".equals(status) && Double.isFinite(profile.getBaselineBrierScore())
                && Double.isFinite(profile.getProposedBrierScore())) {
            return "Quality passed on " + profile.getHoldoutSampleCount()
                    + " unseen examples · error "
                    + String.format(Locale.ROOT, "%.3f", profile.getBaselineBrierScore())
                    + " → "
                    + String.format(Locale.ROOT, "%.3f", profile.getProposedBrierScore());
        }
        if (status.startsWith("waiting")) {
            return "Quality gate waiting for more unseen validated examples ("
                    + profile.getHoldoutSampleCount() + " so far)";
        }
        return "";
    }

    private void changeConsent() {
        if (updatingConsent) {
            return;
        }
        boolean enable = shareOptIn.isSelected();
        if (enable) {
            JTextArea consent = new JTextArea(
                    "This controlled test shares only project/task numbers, decision time, "
                    + "a hashed imagery identifier, building/not-building decision, shape, "
                    + "scan mode, original and locally adjusted scores, five numeric visual "
                    + "measurements and selected geometry-correction flags.\n\n"
                    + "It never sends imagery pixels, candidate coordinates, comments, mapper "
                    + "names or OSM login details. Examples remain quarantined until dated "
                    + "Tasking Manager validation evidence is available.\n\nContinue?",
                    8, 52);
            consent.setEditable(false);
            consent.setLineWrap(true);
            consent.setWrapStyleWord(true);
            consent.setOpaque(false);
            consent.setFont(shareOptIn.getFont());
            consent.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            int choice = JOptionPane.showConfirmDialog(this, consent,
                    tr("Enable anonymous shared learning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                updatingConsent = true;
                shareOptIn.setSelected(false);
                updatingConsent = false;
                return;
            }
        }
        sharedStore.setEnabled(enable);
        setSharedState(enable
                ? "Sharing enabled. New examples queue locally until you press Send queued examples."
                : "Sharing disabled. No new examples will be queued; previously sent examples remain withdrawable.",
                enable ? new Color(0, 105, 45) : new Color(90, 90, 90));
        refresh();
    }

    private void sendQueued() {
        List<SharedLearningStore.Example> all = sharedStore.queued();
        if (all.isEmpty()) {
            setSharedState("No anonymous examples are queued.", new Color(90, 90, 90));
            return;
        }
        List<SharedLearningStore.Example> batch = new ArrayList<>(
                all.subList(0, Math.min(50, all.size())));
        setSharedButtonsEnabled(false);
        setSharedState("Sending " + batch.size()
                + " anonymous example(s) into quarantine…", new Color(0, 90, 145));
        new SwingWorker<java.util.Map<String, String>, Void>() {
            @Override
            protected java.util.Map<String, String> doInBackground() throws Exception {
                return sharedClient.submit(batch, sharedStore.installationId(),
                        sharedStore.withdrawalToken());
            }

            @Override
            protected void done() {
                setSharedButtonsEnabled(true);
                try {
                    java.util.Map<String, String> receipts = get();
                    sharedStore.markSent(receipts);
                    setSharedState(receipts.size()
                            + " example(s) sent. They are quarantined and remain withdrawable.",
                            new Color(0, 105, 45));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setSharedState("Shared submission was interrupted.", new Color(170, 35, 35));
                } catch (ExecutionException exception) {
                    setSharedState(message("Could not send anonymous examples", exception),
                            new Color(170, 35, 35));
                }
                refresh();
            }
        }.execute();
    }

    private void refreshSharedProfile() {
        setSharedButtonsEnabled(false);
        setSharedState("Downloading the anonymous aggregate profile…", new Color(0, 90, 145));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return sharedClient.fetchProfile();
            }

            @Override
            protected void done() {
                setSharedButtonsEnabled(true);
                try {
                    sharedStore.setProfile(get());
                    SharedLearningProfile profile = sharedStore.profile();
                    setSharedState(profile.isActive()
                            ? "Shared aggregate refreshed. Its scanner influence is strictly capped."
                            : "Shared aggregate refreshed; there is not yet enough validated data to influence scanning.",
                            new Color(0, 105, 45));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setSharedState("Profile refresh was interrupted.", new Color(170, 35, 35));
                } catch (ExecutionException exception) {
                    setSharedState(message("Could not refresh the shared profile", exception),
                            new Color(170, 35, 35));
                }
                refresh();
            }
        }.execute();
    }

    private void withdrawSent() {
        List<SharedLearningStore.Example> sent = sharedStore.sent();
        if (sent.isEmpty()) {
            return;
        }
        List<SharedLearningStore.Example> selected = chooseWithdrawalExamples(sent);
        if (selected.isEmpty()) {
            setSharedState("No sent examples were selected for withdrawal.",
                    new Color(90, 90, 90));
            return;
        }
        setSharedButtonsEnabled(false);
        setSharedState("Withdrawing " + selected.size() + " selected example(s)…",
                new Color(0, 90, 145));
        new SwingWorker<WithdrawalResult, Void>() {
            @Override
            protected WithdrawalResult doInBackground() {
                List<String> withdrawn = new ArrayList<>();
                int failed = 0;
                String failure = "";
                for (SharedLearningStore.Example example : selected) {
                    try {
                        sharedClient.withdraw(example.getServiceId(),
                                sharedStore.withdrawalToken());
                        withdrawn.add(example.getServiceId());
                    } catch (Exception exception) {
                        failed++;
                        if (failure.isEmpty() && exception.getMessage() != null) {
                            failure = exception.getMessage();
                        }
                    }
                }
                return new WithdrawalResult(withdrawn, failed, failure);
            }

            @Override
            protected void done() {
                setSharedButtonsEnabled(true);
                try {
                    WithdrawalResult result = get();
                    sharedStore.markWithdrawn(result.withdrawn);
                    if (result.failed == 0) {
                        setSharedState(result.withdrawn.size()
                                + " selected shared example(s) withdrawn.",
                                new Color(0, 105, 45));
                    } else {
                        String detail = result.failure.isEmpty() ? "" : ": " + result.failure;
                        setSharedState(result.withdrawn.size() + " withdrawn; "
                                + result.failed + " could not be withdrawn" + detail,
                                new Color(170, 80, 0));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setSharedState("Withdrawal was interrupted.", new Color(170, 35, 35));
                } catch (ExecutionException exception) {
                    setSharedState(message("Could not withdraw every example", exception),
                            new Color(170, 35, 35));
                }
                refresh();
            }
        }.execute();
    }

    private List<SharedLearningStore.Example> chooseWithdrawalExamples(
            List<SharedLearningStore.Example> sent) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[] {"Withdraw", "Project", "Task", "Decision", "Decision date"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        for (SharedLearningStore.Example example : sent) {
            model.addRow(new Object[] {Boolean.FALSE,
                    Long.toString(example.getProjectId()),
                    Long.toString(example.getTaskId()),
                    example.getDecision().replace('_', ' '),
                    DATE.format(Instant.ofEpochSecond(example.getAttemptEpoch()))});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(68);
        table.getColumnModel().getColumn(1).setPreferredWidth(65);
        table.getColumnModel().getColumn(2).setPreferredWidth(55);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(105);

        JButton selectAll = SidebarButtons.create(tr("Select all"));
        selectAll.addActionListener(event -> {
            for (int row = 0; row < model.getRowCount(); row++) {
                model.setValueAt(Boolean.TRUE, row, 0);
            }
        });
        JButton clear = SidebarButtons.create(tr("Clear"));
        clear.addActionListener(event -> {
            for (int row = 0; row < model.getRowCount(); row++) {
                model.setValueAt(Boolean.FALSE, row, 0);
            }
        });
        JPanel selectionButtons = new JPanel();
        selectionButtons.add(selectAll);
        selectionButtons.add(clear);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(tr("Tick only the sent examples you want to withdraw.")));
        panel.add(Box.createVerticalStrut(5));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, Math.min(300,
                48 + sent.size() * table.getRowHeight())));
        panel.add(scroll);
        panel.add(selectionButtons);
        int choice = JOptionPane.showConfirmDialog(this, panel,
                tr("Choose shared examples to withdraw"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return java.util.Collections.emptyList();
        }
        List<SharedLearningStore.Example> selected = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                selected.add(sent.get(row));
            }
        }
        return selected;
    }

    private static final class WithdrawalResult {
        private final List<String> withdrawn;
        private final int failed;
        private final String failure;

        WithdrawalResult(List<String> withdrawn, int failed, String failure) {
            this.withdrawn = withdrawn;
            this.failed = failed;
            this.failure = failure;
        }
    }

    private void showPrivacyDetails() {
        JTextArea details = new JTextArea(
                "SHARED\n"
                + "• HOT project and task numbers\n"
                + "• decision time\n"
                + "• one-way hashed imagery identifier\n"
                + "• building/not-building and rectangular/round/unknown decision\n"
                + "• scan mode and original/locally adjusted numeric scores\n"
                + "• five numeric visual measurements\n"
                + "• moved/rotated/reshaped/resized flags\n\n"
                + "NEVER SHARED\n"
                + "• imagery or screenshots\n"
                + "• candidate coordinates or building geometry\n"
                + "• comments or task instructions\n"
                + "• mapper name, OSM username, email or login tokens\n\n"
                + "Sharing is off by default. During this controlled test, examples queue "
                + "locally and are sent only when you press Send queued examples. The service "
                + "quarantines submissions and publishes only a thresholded multi-mapper aggregate. "
                + "Sent examples can be withdrawn from this panel.", 18, 48);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(details),
                tr("HOT Project Companion — shared learning privacy"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void setSharedButtonsEnabled(boolean enabled) {
        sendButton.setEnabled(enabled && sharedStore.isEnabled()
                && !sharedStore.queued().isEmpty());
        profileButton.setEnabled(enabled);
        withdrawButton.setEnabled(enabled && !sharedStore.sent().isEmpty());
        shareOptIn.setEnabled(enabled);
    }

    private void setSharedState(String text, Color colour) {
        sharedState.setForeground(colour);
        sharedState.setText("<html><div style='width:300px'>" + escapeHtml(text)
                + "</div></html>");
    }

    private static String message(String fallback, ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null
                ? fallback : fallback + ": " + cause.getMessage();
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
