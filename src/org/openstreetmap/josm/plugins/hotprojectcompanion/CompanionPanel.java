package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.Shortcut;

/** Read-only first-version sidebar for task context. */
public final class CompanionPanel extends ToggleDialog {
    private static final long serialVersionUID = 1L;

    private final JTextField projectId = new JTextField(7);
    private final JTextField taskId = new JTextField(5);
    private final JLabel status = new JLabel(tr("No task loaded"), SwingConstants.LEFT);
    private final JTextArea mapSummary = sectionText(tr("Open a HOT task in JOSM, then detect the task boundary."));
    private final JTextArea imagerySummary = sectionText(tr("Required imagery and explicit offset guidance will appear here."));
    private final JTextArea feedbackSummary = sectionText(tr("Previous mapper and validator comments will appear here."));
    private final JTextArea uploadSummary = sectionText(tr("Changeset comment, source and hashtags will appear here."));
    private final JPanel instructionImages = new JPanel();
    private final TaskQuestionPanel taskQuestions = new TaskQuestionPanel();
    private final BuildingCheckPanel buildingCheck = new BuildingCheckPanel();
    private final HotTaskingManagerClient taskingManagerClient = new HotTaskingManagerClient();
    private final LearningStore learningStore = new LearningStore();
    private final LearningPanel learning = new LearningPanel(learningStore, taskingManagerClient);
    private final TaskReconnaissancePanel reconnaissance = new TaskReconnaissancePanel(
            learningStore, learning::refresh);
    private final SplitFeedbackCache splitFeedbackCache = new SplitFeedbackCache();
    private int requestGeneration;

    public CompanionPanel() {
        super(
                tr("HOT Project Companion"),
                "hotprojectcompanion",
                tr("Show project instructions and previous task feedback"),
                Shortcut.registerShortcut(
                        "subwindow:hotprojectcompanion",
                        tr("Toggle: {0}", tr("HOT Project Companion")),
                        KeyEvent.VK_H,
                        Shortcut.ALT_SHIFT),
                420,
                true);

        createLayout(buildContent(), true, Collections.emptyList());
        SwingUtilities.invokeLater(this::detectCurrentTask);
    }

    @Override
    public void hideNotify() {
        reconnaissance.restoreMappedBuildingOutlines();
        super.hideNotify();
    }

    @Override
    public void destroy() {
        reconnaissance.restoreMappedBuildingOutlines();
        super.destroy();
    }

    private Component buildContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel("<html>The companion detects the HOT task from JOSM's task boundary layer.</html>");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(intro);
        content.add(Box.createVerticalStrut(6));

        JButton detect = SidebarButtons.create(tr("Detect current task"));
        detect.setAlignmentX(Component.LEFT_ALIGNMENT);
        detect.addActionListener(event -> detectCurrentTask());
        content.add(detect);
        content.add(Box.createVerticalStrut(5));

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(status);
        content.add(Box.createVerticalStrut(5));

        JPanel fallback = new JPanel(new BorderLayout(6, 0));
        fallback.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel identifiers = new JPanel();
        identifiers.add(new JLabel(tr("Project:")));
        identifiers.add(projectId);
        identifiers.add(new JLabel(tr("Task:")));
        identifiers.add(taskId);
        JButton useManual = SidebarButtons.create(tr("Use"));
        useManual.addActionListener(event -> loadManualReference());
        projectId.addActionListener(event -> loadManualReference());
        taskId.addActionListener(event -> loadManualReference());
        fallback.add(identifiers, BorderLayout.CENTER);
        fallback.add(useManual, BorderLayout.EAST);
        fallback.setMaximumSize(new Dimension(Integer.MAX_VALUE, fallback.getPreferredSize().height));
        fallback.setBorder(BorderFactory.createTitledBorder(tr("Manual fallback")));
        content.add(fallback);
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("learning", tr("Local learning"), learning, false));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("what-to-map", tr("What to map"),
                instructionSection(mapSummary, instructionImages), true));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("task-questions", tr("Ask about this task"),
                taskQuestions, false));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("required-imagery", tr("Required imagery"),
                section(imagerySummary), true));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("previous-feedback", tr("Previous feedback"),
                section(feedbackSummary), true));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("building-check", tr("Building check"), buildingCheck, false));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("reconnaissance", tr("Task building reconnaissance"),
                reconnaissance, false));
        content.add(Box.createVerticalStrut(8));
        content.add(collapsible("upload-details", tr("Upload details"),
                section(uploadSummary), false));
        content.add(Box.createVerticalGlue());
        return content;
    }

    private void detectCurrentTask() {
        for (Layer layer : MainApplication.getLayerManager().getLayers()) {
            TaskReference reference = TaskLayerNameParser.parse(layer.getName());
            if (reference != null) {
                projectId.setText(Long.toString(reference.getProjectId()));
                taskId.setText(Long.toString(reference.getTaskId()));
                showReference(reference, tr("Detected from the task boundary layer"));
                return;
            }
        }

        status.setForeground(new Color(170, 90, 0));
        status.setText("<html><b>Task boundary not found.</b> Try Detect again, or use the manual fields.</html>");
    }

    private void loadManualReference() {
        try {
            long project = parsePositiveNumber(projectId.getText(), tr("project number"));
            long task = parsePositiveNumber(taskId.getText(), tr("task number"));
            showReference(TaskReference.forHotTask(project, task), tr("Entered manually"));
        } catch (IllegalArgumentException exception) {
            status.setForeground(new Color(170, 35, 35));
            status.setText("<html><b>" + escapeHtml(exception.getMessage()) + "</b></html>");
        }
    }

    private void showReference(TaskReference reference, String source) {
        status.setForeground(new Color(0, 110, 45));
        status.setText("<html><b>Project " + reference.getProjectId() + " · Task "
                + reference.getTaskId() + "</b><br>" + escapeHtml(source) + " · Loading live details…</html>");
        setAllSections(tr("Loading live Tasking Manager information…"));
        loadLiveContext(reference, source);
    }

    private void loadLiveContext(TaskReference reference, String source) {
        int generation = ++requestGeneration;
        new SwingWorker<TaskContext, Void>() {
            @Override
            protected TaskContext doInBackground() throws Exception {
                return taskingManagerClient.load(reference);
            }

            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    TaskContext context = get();
                    setSection(mapSummary, context.getWhatToMap());
                    showInstructionImages(context.getInstructionImages(), generation);
                    setSection(imagerySummary, context.getImageryGuidance());
                    String feedback = context.getPreviousFeedback();
                    if (context.isSplitTask() && !context.hasDetailedFeedback()) {
                        feedback = SplitFeedbackCache.appendInherited(feedback,
                                splitFeedbackCache.recentForSplitChild(reference));
                    }
                    setSection(feedbackSummary, feedback);
                    setSection(uploadSummary, context.getUploadDetails());
                    buildingCheck.setContext(context);
                    taskQuestions.setContext(context);
                    reconnaissance.setContext(context, reference);
                    splitFeedbackCache.remember(reference, context);
                    status.setForeground(new Color(0, 110, 45));
                    status.setText("<html><b>Project " + reference.getProjectId() + " · Task "
                            + reference.getTaskId() + "</b><br>" + escapeHtml(source)
                            + " · Live details loaded</html>");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showLoadError(reference, "Loading was interrupted");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showLoadError(reference, cause == null ? exception.getMessage() : cause.getMessage());
                }
            }
        }.execute();
    }

    private void showLoadError(TaskReference reference, String message) {
        status.setForeground(new Color(170, 35, 35));
        status.setText("<html><b>Project " + reference.getProjectId() + " · Task "
                + reference.getTaskId() + "</b><br>Could not load live details: "
                + escapeHtml(message == null ? "unknown error" : message) + "</html>");
        setAllSections(tr("Live information could not be loaded. Check your connection, then click Detect current task to retry."));
    }

    private void setAllSections(String text) {
        setSection(mapSummary, text);
        instructionImages.removeAll();
        instructionImages.setVisible(false);
        setSection(imagerySummary, text);
        setSection(feedbackSummary, text);
        setSection(uploadSummary, text);
        buildingCheck.clearContext();
        taskQuestions.clearContext();
        reconnaissance.clearContext();
    }

    private static void setSection(JTextArea area, String text) {
        area.setText(text);
        area.setCaretPosition(0);
    }

    private static long parsePositiveNumber(String value, String label) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(tr("Enter a valid {0}.", label), exception);
        }
    }

    private static CollapsibleSection collapsible(String id, String title, JPanel body,
            boolean expandedByDefault) {
        return new CollapsibleSection(id, title, body, expandedByDefault);
    }

    private static JPanel section(JTextArea text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 3, 3, 3));
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        return panel;
    }

    private static JPanel instructionSection(JTextArea text, JPanel images) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 3, 3, 3));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(text);
        images.setLayout(new BoxLayout(images, BoxLayout.Y_AXIS));
        images.setAlignmentX(Component.LEFT_ALIGNMENT);
        images.setVisible(false);
        body.add(images);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        return panel;
    }

    private void showInstructionImages(List<InstructionImage> references, int generation) {
        instructionImages.removeAll();
        if (references.isEmpty()) {
            instructionImages.setVisible(false);
            instructionImages.revalidate();
            instructionImages.repaint();
            return;
        }
        JLabel loading = new JLabel(tr("Loading {0} instruction image(s)…", references.size()));
        loading.setBorder(BorderFactory.createEmptyBorder(8, 3, 8, 3));
        instructionImages.add(loading);
        instructionImages.setVisible(true);
        instructionImages.revalidate();
        instructionImages.repaint();

        new SwingWorker<List<ImageResult>, Void>() {
            @Override
            protected List<ImageResult> doInBackground() {
                List<ImageResult> results = new ArrayList<>();
                for (InstructionImage reference : references) {
                    try {
                        results.add(ImageResult.loaded(reference,
                                InstructionImageLoader.loadThumbnail(reference)));
                    } catch (Exception exception) {
                        results.add(ImageResult.failed(reference,
                                exception.getMessage() == null ? "Image could not be loaded" : exception.getMessage()));
                    }
                }
                return results;
            }

            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    displayInstructionImages(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    displayInstructionImages(Collections.emptyList());
                }
            }
        }.execute();
    }

    private void displayInstructionImages(List<ImageResult> results) {
        instructionImages.removeAll();
        int imageNumber = 1;
        for (ImageResult result : results) {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(205, 205, 205)),
                    BorderFactory.createEmptyBorder(8, 3, 8, 3)));

            String description = result.reference.getDescription().isEmpty()
                    ? tr("Instruction image {0}", imageNumber) : result.reference.getDescription();
            JLabel caption = new JLabel("<html><b>" + escapeHtml(description) + "</b></html>");
            caption.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(caption);
            card.add(Box.createVerticalStrut(5));
            if (result.icon != null) {
                JLabel image = new JLabel(result.icon);
                image.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(image);
            } else {
                JLabel error = new JLabel("<html>Could not display this image: "
                        + escapeHtml(result.error) + "</html>");
                error.setForeground(new Color(150, 65, 0));
                error.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(error);
            }
            card.add(Box.createVerticalStrut(5));
            JButton open = SidebarButtons.create(tr("Open full-size image"));
            open.setAlignmentX(Component.LEFT_ALIGNMENT);
            open.setToolTipText(result.reference.getUrl());
            open.addActionListener(event -> openImageInBrowser(result.reference.getUrl()));
            card.add(open);
            instructionImages.add(card);
            imageNumber++;
        }
        if (results.isEmpty()) {
            instructionImages.add(new JLabel(tr("Instruction images could not be loaded.")));
        }
        instructionImages.setVisible(true);
        instructionImages.revalidate();
        instructionImages.repaint();
    }

    private static void openImageInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // The URL remains available in the button tooltip if the desktop cannot open it.
        }
    }

    private static JTextArea sectionText(String text) {
        JTextArea area = new JTextArea(text, 4, 24);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        return area;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class ImageResult {
        private final InstructionImage reference;
        private final ImageIcon icon;
        private final String error;

        private ImageResult(InstructionImage reference, ImageIcon icon, String error) {
            this.reference = reference;
            this.icon = icon;
            this.error = error;
        }

        private static ImageResult loaded(InstructionImage reference, ImageIcon icon) {
            return new ImageResult(reference, icon, "");
        }

        private static ImageResult failed(InstructionImage reference, String error) {
            return new ImageResult(reference, null, error);
        }
    }
}
