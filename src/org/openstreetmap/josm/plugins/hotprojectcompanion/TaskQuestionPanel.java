package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Local, source-disclosing questions against the currently loaded HOT guidance. */
final class TaskQuestionPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTextField question = new JTextField();
    private final JButton ask = SidebarButtons.create(tr("Ask"));
    private final JTextArea answer = new JTextArea();
    private TaskContext context;

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
            text.append("\n\nEvidence from the loaded task:");
            for (TaskQuestionAnswerer.Passage passage : result.getEvidence()) {
                text.append("\n\n").append(passage.getSource()).append(":\n")
                        .append(passage.getText());
            }
        }
        if (result.getOutcome() == TaskQuestionAnswerer.Outcome.NOT_FOUND
                || result.getOutcome() == TaskQuestionAnswerer.Outcome.RELATED) {
            text.append("\n\nCheck the full project instructions or ask the project team before making an uncertain mapping decision.");
            answer.setForeground(new Color(150, 65, 0));
        } else {
            answer.setForeground(new Color(0, 105, 45));
        }
        answer.setText(text.toString());
        answer.setCaretPosition(0);
    }

    private static JLabel wrappingLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:300px'>" + text
                + "</div></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
