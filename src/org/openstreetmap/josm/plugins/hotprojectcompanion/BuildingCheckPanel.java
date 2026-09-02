package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;

/** Automatic, local-only visual analysis of a selected outline on authorised imagery. */
final class BuildingCheckPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int PREVIEW_WIDTH = 340;
    private static final int PREVIEW_HEIGHT = 230;

    private final JLabel state = wrappingLabel("Load a HOT task, then select one closed outline.");
    private final JLabel preview = new JLabel();
    private final JLabel result = wrappingLabel("No building analysis calculated.");
    private final JButton analyseButton = SidebarButtons.create(tr("Analyse selected outline"));
    private TaskContext context;
    private int analysisGeneration;
    private Timer pendingCapture;

    BuildingCheckPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createTitledBorder(tr("Building check")));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 610));

        add(wrappingLabel("JOSM will compare the selected outline with the visible authorised imagery and usable example images from this task."));
        add(Box.createVerticalStrut(6));

        analyseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        analyseButton.setEnabled(false);
        analyseButton.addActionListener(event -> analyseSelection());
        add(analyseButton);
        add(Box.createVerticalStrut(5));
        add(state);
        add(Box.createVerticalStrut(7));

        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        preview.setVisible(false);
        add(preview);
        add(Box.createVerticalStrut(7));
        add(result);
        add(Box.createVerticalStrut(5));

        JLabel warning = wrappingLabel("Guidance only: this is an explainable visual match score, not a statistical probability. Check uncertain cases against the project instructions. The companion never changes the selected object.");
        warning.setForeground(new Color(150, 65, 0));
        add(warning);
    }

    void setContext(TaskContext context) {
        analysisGeneration++;
        cancelPendingCapture();
        this.context = context;
        resetDisplay();
        analyseButton.setEnabled(true);
        state.setForeground(new Color(0, 105, 45));
        state.setText("Project loaded. Select exactly one closed outline, keep it visible and click Analyse selected outline.");
    }

    void clearContext() {
        analysisGeneration++;
        cancelPendingCapture();
        context = null;
        resetDisplay();
        analyseButton.setEnabled(false);
        state.setForeground(new Color(150, 65, 0));
        state.setText("Load a HOT task before running a building check.");
    }

    private void analyseSelection() {
        int generation = ++analysisGeneration;
        try {
            if (context == null) {
                throw new IllegalArgumentException("Load the HOT task context first.");
            }
            verifyAuthorisedImagery(context.getAuthorisedImagery());
            Way way = selectedClosedWay();
            analyseButton.setEnabled(false);
            TaskContext activeContext = context;
            MapFrame map = MainApplication.getMap();
            if (map == null || map.mapView == null) {
                throw new IllegalArgumentException("The JOSM map view is not available.");
            }
            map.mapView.repaint();
            state.setForeground(new Color(0, 90, 145));
            state.setText("Preparing a fresh render of the visible authorised imagery…");
            pendingCapture = new Timer(250, event -> {
                pendingCapture = null;
                if (generation != analysisGeneration || context != activeContext) {
                    return;
                }
                captureAndAnalyse(generation, way, activeContext);
            });
            pendingCapture.setRepeats(false);
            pendingCapture.start();
        } catch (IllegalArgumentException exception) {
            analyseButton.setEnabled(context != null);
            state.setForeground(new Color(170, 35, 35));
            state.setText(exception.getMessage());
        } catch (LinkageError error) {
            analyseButton.setEnabled(context != null);
            state.setForeground(new Color(170, 35, 35));
            state.setText("This Building check is not compatible with this JOSM API. Update the companion and try again.");
        }
        revalidate();
        repaint();
    }

    private void captureAndAnalyse(int generation, Way way, TaskContext activeContext) {
        try {
            CapturedOutline captured = captureMapCrop(way);
            preview.setIcon(new ImageIcon(scaleToFit(captured.image, PREVIEW_WIDTH, PREVIEW_HEIGHT)));
            preview.setVisible(true);
            result.setForeground(Color.BLACK);
            result.setText("Analysing the outline and task examples…");
            state.setForeground(new Color(0, 105, 45));
            state.setText("Fresh imagery captured. Analysis runs locally; captured imagery is not saved or sent anywhere.");

            new SwingWorker<AnalysisOutcome, Void>() {
                @Override
                protected AnalysisOutcome doInBackground() {
                    List<BuildingImageAnalyser.ReferenceImage> references = new ArrayList<>();
                    int failures = 0;
                    for (InstructionImage reference : activeContext.getInstructionImages()) {
                        try {
                            references.add(new BuildingImageAnalyser.ReferenceImage(
                                    InstructionImageLoader.loadForAnalysis(reference),
                                    reference.getDescription()));
                        } catch (Exception ignored) {
                            failures++;
                        }
                    }
                    return new AnalysisOutcome(
                            BuildingImageAnalyser.analyse(captured.image, captured.outline, references),
                            activeContext.getInstructionImages().size(), failures);
                }

                @Override
                protected void done() {
                    if (generation != analysisGeneration) {
                        return;
                    }
                    analyseButton.setEnabled(context != null);
                    try {
                        showResult(get());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        showAnalysisError("Analysis was interrupted. Try again.");
                    } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        showAnalysisError(cause == null || cause.getMessage() == null
                                ? "The outline could not be analysed." : cause.getMessage());
                    }
                }
            }.execute();
        } catch (IllegalArgumentException exception) {
            analyseButton.setEnabled(context != null);
            state.setForeground(new Color(170, 35, 35));
            state.setText(exception.getMessage());
        } catch (LinkageError error) {
            analyseButton.setEnabled(context != null);
            state.setForeground(new Color(170, 35, 35));
            state.setText("This Building check is not compatible with this JOSM API. Update the companion and try again.");
        }
        revalidate();
        repaint();
    }

    private void cancelPendingCapture() {
        if (pendingCapture != null) {
            pendingCapture.stop();
            pendingCapture = null;
        }
    }

    private void showResult(AnalysisOutcome outcome) {
        BuildingImageAnalyser.Result scored = outcome.result;
        String colour = scored.getScore() >= 70 ? "#006E2D" : scored.getScore() >= 45 ? "#9A6400" : "#AA2323";
        StringBuilder details = new StringBuilder("<html><div style='width:300px'><b style='color:")
                .append(colour).append("'>Visual match score: ")
                .append(scored.getScore()).append("/100 — ").append(escapeHtml(scored.getBand())).append("</b>")
                .append("<br><b>Automatic measurements:</b> outline geometry ").append(scored.getShapeScore())
                .append(" (diagnostic only), roof consistency ").append(scored.getConsistencyScore())
                .append(", surroundings contrast ").append(scored.getContrastScore())
                .append(", imagery boundary ").append(scored.getBoundaryScore())
                .append(", shadow cue ").append(scored.getShadowScore());
        if (scored.getReferenceScore() >= 0) {
            details.append(", task examples ").append(scored.getReferenceScore());
        } else {
            details.append(", task examples not scored");
        }
        details.append(".");
        if (!scored.getSupporting().isEmpty()) {
            details.append("<br><b>Supporting evidence:</b> ")
                    .append(escapeHtml(String.join(", ", scored.getSupporting()))).append('.');
        }
        if (!scored.getCautions().isEmpty()) {
            details.append("<br><b>Check carefully:</b> ")
                    .append(escapeHtml(String.join(", ", scored.getCautions()))).append('.');
        }
        if (scored.getComparedReferences() > 0) {
            details.append("<br>Compared with ").append(scored.getComparedReferences()).append(" task image(s)");
            if (scored.getLabelledReferences() == 0) {
                details.append("; none had a clear building/non-building label");
            }
            if (!scored.getClosestDescription().isEmpty()) {
                details.append(". Closest task image: <i>")
                        .append(escapeHtml(scored.getClosestDescription())).append("</i>");
            }
            details.append('.');
        } else if (outcome.requestedReferences > 0 && outcome.failedReferences > 0) {
            details.append("<br>The task contains ").append(outcome.requestedReferences)
                    .append(" instruction image(s), but ").append(outcome.failedReferences)
                    .append(" could not be loaded for analysis.");
        }
        details.append("<br><i>This score is automated visual evidence, not a probability or a mapping decision.</i></div></html>");
        result.setForeground(Color.BLACK);
        result.setText(details.toString());
        revalidate();
        repaint();
    }

    private void showAnalysisError(String message) {
        result.setForeground(new Color(170, 35, 35));
        result.setText(message);
    }

    private static Way selectedClosedWay() {
        DataSet data = MainApplication.getLayerManager().getEditDataSet();
        if (data == null) {
            throw new IllegalArgumentException("No editable OSM data layer is active.");
        }
        Collection<Way> selected = data.getSelectedWays();
        if (selected.size() != 1) {
            throw new IllegalArgumentException("Select exactly one building outline before analysing.");
        }
        Way way = selected.iterator().next();
        if (!way.isClosed() || way.getNodes().size() < 4 || way.hasIncompleteNodes()) {
            throw new IllegalArgumentException("The selected object must be one complete, closed outline.");
        }
        return way;
    }

    static void verifyAuthorisedImagery(String authorisedImagery) {
        if (authorisedImagery == null || authorisedImagery.trim().isEmpty()) {
            throw new IllegalArgumentException("The project did not return an authorised imagery value, so the check cannot run safely.");
        }
        List<String> visibleImagery = new ArrayList<>();
        for (Layer layer : MainApplication.getLayerManager().getVisibleLayersInZOrder()) {
            if (isImageryLayer(layer)) {
                visibleImagery.add(layer.getName());
            }
        }
        if (visibleImagery.isEmpty()) {
            throw new IllegalArgumentException("No visible imagery layer was found.");
        }
        for (String layerName : visibleImagery) {
            if (!imageryMatches(authorisedImagery, layerName)) {
                throw new IllegalArgumentException("Visible imagery ‘" + layerName
                        + "’ could not be matched to the project-authorised imagery. Hide it or switch to the required layer.");
            }
        }
    }

    static boolean imageryMatches(String authorisedImagery, String layerName) {
        String authorised = normalise(authorisedImagery);
        String layer = normalise(layerName);
        if (authorised.isEmpty() || layer.isEmpty()) {
            return false;
        }
        String authorisedCompact = authorised.replace(" ", "");
        String layerCompact = layer.replace(" ", "");
        return authorised.contains(layer) || layer.contains(authorised)
                || (authorisedCompact.length() >= 4 && layerCompact.length() >= 4
                        && (authorisedCompact.contains(layerCompact) || layerCompact.contains(authorisedCompact)))
                || sharedMeaningfulWords(authorised, layer) >= 2;
    }

    private static int sharedMeaningfulWords(String first, String second) {
        int shared = 0;
        for (String word : first.split(" ")) {
            if (word.length() >= 4 && (" " + second + " ").contains(" " + word + " ")) {
                shared++;
            }
        }
        return shared;
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    static boolean isImageryLayer(Layer layer) {
        String className = layer.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains(".imagery.") || className.endsWith("tmslayer")
                || className.endsWith("wmtslayer") || className.endsWith("wmslayer");
    }

    private static CapturedOutline captureMapCrop(Way way) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || map.mapView.getWidth() < 1 || map.mapView.getHeight() < 1) {
            throw new IllegalArgumentException("The JOSM map view is not available.");
        }
        MapView mapView = map.mapView;
        Polygon mapOutline = outlinePolygon(mapView, way);
        Rectangle outlineBounds = mapOutline.getBounds();
        Rectangle view = new Rectangle(0, 0, mapView.getWidth(), mapView.getHeight());
        int padding = Math.max(35, Math.min(100, Math.max(outlineBounds.width, outlineBounds.height) / 2));
        Rectangle crop = new Rectangle(outlineBounds.x - padding, outlineBounds.y - padding,
                outlineBounds.width + padding * 2, outlineBounds.height + padding * 2).intersection(view);
        if (crop.width < 30 || crop.height < 30 || !view.contains(outlineBounds)) {
            throw new IllegalArgumentException("Keep the entire selected outline visible and zoom in enough to see it clearly, then try again.");
        }
        BufferedImage mapImage = new BufferedImage(mapView.getWidth(), mapView.getHeight(), BufferedImage.TYPE_INT_RGB);
        List<Layer> hiddenForCapture = new ArrayList<>();
        try {
            for (Layer layer : MainApplication.getLayerManager().getLayers()) {
                if (layer.isVisible() && !isImageryLayer(layer)) {
                    hiddenForCapture.add(layer);
                    layer.setVisible(false);
                }
            }
            Graphics2D graphics = mapImage.createGraphics();
            try {
                // printAll disables Swing double buffering for this render. paintAll
                // can otherwise return the previous map frame on the first capture.
                renderFreshMapView(mapView, graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            for (Layer layer : hiddenForCapture) {
                layer.setVisible(true);
            }
        }
        BufferedImage result = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D copy = result.createGraphics();
        try {
            copy.drawImage(mapImage, 0, 0, crop.width, crop.height,
                    crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
        } finally {
            copy.dispose();
        }
        int[] x = new int[mapOutline.npoints];
        int[] y = new int[mapOutline.npoints];
        for (int index = 0; index < mapOutline.npoints; index++) {
            x[index] = mapOutline.xpoints[index] - crop.x;
            y[index] = mapOutline.ypoints[index] - crop.y;
        }
        return new CapturedOutline(result, new Polygon(x, y, mapOutline.npoints));
    }

    /**
     * Renders the current map into an off-screen graphics context.
     *
     * <p>A {@link BufferedImage} graphics context has no clip by default. JOSM's
     * layer painter requires a non-null clip rectangle, so calling
     * {@code MapView#printAll} without setting one can make JOSM's map renderer
     * throw a {@link NullPointerException}.</p>
     *
     * @param mapView active JOSM map view
     * @param graphics target graphics context
     * @throws IllegalArgumentException if JOSM cannot render the current frame
     */
    static void renderFreshMapView(MapView mapView, Graphics2D graphics) {
        graphics.setClip(0, 0, mapView.getWidth(), mapView.getHeight());
        try {
            mapView.printAll(graphics);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "JOSM was still preparing the map view. Wait a moment and try again.", exception);
        }
    }

    private static Polygon outlinePolygon(MapView mapView, Way way) {
        List<Node> nodes = way.getNodes();
        int pointCount = nodes.size();
        if (pointCount > 1 && nodes.get(0) == nodes.get(pointCount - 1)) {
            pointCount--;
        }
        int[] x = new int[pointCount];
        int[] y = new int[pointCount];
        for (int index = 0; index < pointCount; index++) {
            Point point = mapView.getPoint(nodes.get(index));
            x[index] = point.x;
            y[index] = point.y;
        }
        return new Polygon(x, y, pointCount);
    }

    private static Image scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min(maxWidth / (double) source.getWidth(),
                maxHeight / (double) source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private void resetDisplay() {
        preview.setIcon(null);
        preview.setVisible(false);
        result.setForeground(Color.BLACK);
        result.setText("No building analysis calculated.");
    }

    private static JLabel wrappingLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:300px'>" + escapeHtml(text) + "</div></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class CapturedOutline {
        private final BufferedImage image;
        private final Polygon outline;

        CapturedOutline(BufferedImage image, Polygon outline) {
            this.image = image;
            this.outline = outline;
        }
    }

    private static final class AnalysisOutcome {
        private final BuildingImageAnalyser.Result result;
        private final int requestedReferences;
        private final int failedReferences;

        AnalysisOutcome(BuildingImageAnalyser.Result result, int requestedReferences, int failedReferences) {
            this.result = result;
            this.requestedReferences = requestedReferences;
            this.failedReferences = failedReferences;
        }
    }
}
