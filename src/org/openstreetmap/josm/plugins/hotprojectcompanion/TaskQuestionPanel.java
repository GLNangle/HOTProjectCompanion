package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.gui.widgets.JosmTextField;

/** Local, source-disclosing questions against the currently loaded HOT guidance. */
final class TaskQuestionPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final long FOCUS_RECOVERY_NANOS = 650_000_000L;
    private static final int FOCUS_RECOVERY_INTERVAL_MS = 35;
    private static final int FOCUS_RECOVERY_ATTEMPTS = 6;

    private final JosmTextField question = new JosmTextField();
    private final JButton ask = SidebarButtons.create(tr("Ask"));
    private final JTextArea answer = new JTextArea();
    private TaskContext context;
    private long lastQuestionKeyNanos;
    private Timer focusRecoveryTimer;
    private int recoveryCaretPosition;
    private boolean recoveringFocus;

    TaskQuestionPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        JLabel explanation = wrappingLabel(
                "Ask about the loaded project instructions, imagery or task feedback. Answers are matched locally and never invent missing guidance.");
        add(explanation);
        add(Box.createVerticalStrut(5));
        question.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                question.getPreferredSize().height));
        question.setToolTipText(tr("For example: Should I map buildings under construction?"));
        question.setEnabled(false);
        question.addActionListener(event -> answerQuestion());
        installTypingFocusGuard();
        add(question);
        add(Box.createVerticalStrut(4));
        ask.setAlignmentX(Component.LEFT_ALIGNMENT);
        ask.setEnabled(false);
        ask.addActionListener(event -> answerQuestion());
        add(ask);
        add(Box.createVerticalStrut(5));
        answer.setEditable(false);
        answer.setLineWrap(true);
        answer.setWrapStyleWord(true);
        answer.setOpaque(false);
        answer.setText(tr("Load a HOT task before asking a question."));
        JScrollPane scroll = new JScrollPane(answer);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(320, 150));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        add(scroll);
    }

    void setContext(TaskContext context) {
        this.context = context;
        question.setEnabled(true);
        ask.setEnabled(true);
        answer.setForeground(new Color(45, 45, 45));
        answer.setText(tr("Ask a specific question about this project."));
        answer.setCaretPosition(0);
    }

    void clearContext() {
        context = null;
        question.setEnabled(false);
        ask.setEnabled(false);
        answer.setForeground(new Color(150, 65, 0));
        answer.setText(tr("Load a HOT task before asking a question."));
        answer.setCaretPosition(0);
    }

    private void answerQuestion() {
        TaskQuestionAnswerer.Answer result = TaskQuestionAnswerer.answer(context,
                question.getText());
        StringBuilder text = new StringBuilder(result.getSummary());
        if (!result.getEvidence().isEmpty()) {
            TaskQuestionAnswerer.Passage passage = result.getEvidence().get(0);
            if (result.getOutcome() == TaskQuestionAnswerer.Outcome.NOT_FOUND) {
                text.append("\n\nClosest relevant guidance: ").append(passage.getText());
            }
            text.append("\n\nSource: ").append(passage.getSource());
        }
        if (result.getOutcome() == TaskQuestionAnswerer.Outcome.NOT_FOUND) {
            text.append("\n\nCheck the full instructions or ask the project team if needed.");
            answer.setForeground(new Color(150, 65, 0));
        } else if (result.getOutcome() == TaskQuestionAnswerer.Outcome.RELATED) {
            answer.setForeground(new Color(45, 45, 45));
        } else {
            answer.setForeground(new Color(0, 105, 45));
        }
        answer.setText(text.toString());
        answer.setCaretPosition(0);
    }

    private void installTypingFocusGuard() {
        question.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                lastQuestionKeyNanos = System.nanoTime();
            }
        });
        question.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if (!recoveringFocus) {
                    return;
                }
                if (focusRecoveryTimer != null) {
                    focusRecoveryTimer.stop();
                }
                SwingUtilities.invokeLater(() -> {
                    if (recoveringFocus && question.isFocusOwner()) {
                        int caret = safeCaretPosition(recoveryCaretPosition,
                                question.getDocument().getLength());
                        question.select(caret, caret);
                    }
                    recoveringFocus = false;
                });
            }

            @Override
            public void focusLost(FocusEvent event) {
                if (!shouldRecoverFocus(lastQuestionKeyNanos, System.nanoTime(),
                        event.getCause())) {
                    return;
                }
                recoveryCaretPosition = question.getCaretPosition();
                recoveringFocus = true;
                recoverTypingFocus();
            }
        });
    }

    static boolean shouldRecoverFocus(long lastKeyNanos, long nowNanos,
            FocusEvent.Cause cause) {
        long elapsed = nowNanos - lastKeyNanos;
        if (lastKeyNanos == 0 || elapsed < 0 || elapsed > FOCUS_RECOVERY_NANOS) {
            return false;
        }
        return cause != FocusEvent.Cause.MOUSE_EVENT
                && cause != FocusEvent.Cause.TRAVERSAL_FORWARD
                && cause != FocusEvent.Cause.TRAVERSAL_BACKWARD
                && cause != FocusEvent.Cause.ACTIVATION;
    }

    static int safeCaretPosition(int requested, int documentLength) {
        return Math.max(0, Math.min(requested, Math.max(0, documentLength)));
    }

    private void recoverTypingFocus() {
        if (focusRecoveryTimer != null) {
            focusRecoveryTimer.stop();
        }
        SwingUtilities.invokeLater(() -> {
            if (!canRecoverTypingFocus() || question.isFocusOwner()) {
                if (!question.isFocusOwner()) {
                    recoveringFocus = false;
                }
                return;
            }
            question.requestFocusInWindow();
            final int[] attempts = {0};
            focusRecoveryTimer = new Timer(FOCUS_RECOVERY_INTERVAL_MS, event -> {
                if (!canRecoverTypingFocus() || question.isFocusOwner()
                        || ++attempts[0] >= FOCUS_RECOVERY_ATTEMPTS) {
                    ((Timer) event.getSource()).stop();
                    if (!question.isFocusOwner()) {
                        recoveringFocus = false;
                    }
                    return;
                }
                question.requestFocusInWindow();
            });
            focusRecoveryTimer.start();
        });
    }

    private boolean canRecoverTypingFocus() {
        if (!question.isEnabled() || !question.isShowing()) {
            return false;
        }
        Window window = SwingUtilities.getWindowAncestor(question);
        return window == null || window.isActive();
    }

    private static JLabel wrappingLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:300px'>" + text
                + "</div></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
