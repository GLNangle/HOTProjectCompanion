package org.openstreetmap.josm.plugins.hotprojectcompanion;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.FilterTableModel;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/** Read-only building inventory and imagery reconnaissance for the current HOT task. */
final class TaskReconnaissancePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int THUMBNAIL_SIZE = 82;

    private final JButton scanButton = SidebarButtons.create(tr("Scan entire task"));
    private final JButton scanVisibleButton = SidebarButtons.create(tr("Scan visible area"));
    private final JButton rescanButton = SidebarButtons.create(tr("Rescan after review"));
    private final JComboBox<BuildingCandidateScanner.ScanMode> scanMode = new JComboBox<>(
            BuildingCandidateScanner.ScanMode.values());
    private final JButton highlightToggleButton = SidebarButtons.create(tr("Hide candidate outline"));
    private final JButton mappedBuildingsToggleButton = SidebarButtons.create(tr("Hide mapped building outlines"));
    private final JButton learnMissedButton = SidebarButtons.create(
            tr("Learn from buildings drawn since scan"));
    private final JLabel state = wrappingLabel("Load a HOT task before scanning.");
    private final JLabel summary = wrappingLabel("No task reconnaissance calculated.");
    private final JPanel checklist = new JPanel();
    private TaskContext context;
    private TaskReference reference;
    private int scanGeneration;
    private MapViewPaintable reviewHighlight;
    private boolean reviewHighlightVisible;
    private CandidateReviewDecisions reviewDecisions;
    private final List<CandidateReviewItem> candidateItems = new ArrayList<>();
    private final List<MappedReviewItem> mappedReviewItems = new ArrayList<>();
    private boolean rejectedExpanded;
    private boolean confirmedMappedExpanded;
    private boolean notBuildingMappedExpanded;
    private int activeCandidateNumber = -1;
    private int activeMappedReviewNumber = -1;
    private TaskCapture displayedCapture;
    private BuildingCandidateScanner.Result displayedResult;
    private BuildingCandidateScanner.ScanMode displayedMode =
            BuildingCandidateScanner.ScanMode.CONSERVATIVE;
    private final MappedBuildingFilter mappedBuildingFilter = new MappedBuildingFilter();
    private final LearningStore learningStore;
    private final SharedLearningStore sharedLearningStore;
    private final Runnable learningChanged;
    private final Set<Way> learnedBuildingWays = new HashSet<>();
    private final List<ProjectionBounds> reviewedCandidateAreas = new ArrayList<>();
    private final PluginPreferences.Store preferences = PluginPreferences.josm();

    TaskReconnaissancePanel(LearningStore learningStore,
            SharedLearningStore sharedLearningStore, Runnable learningChanged) {
        this.learningStore = learningStore;
        this.sharedLearningStore = sharedLearningStore;
        this.learningChanged = learningChanged;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        add(wrappingLabel("Count downloaded mapped buildings and estimate possible unmapped rectangular and round candidates from the visible authorised imagery."));
        add(Box.createVerticalStrut(6));
        JLabel modeLabel = new JLabel(tr("Scan sensitivity:"));
        modeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(modeLabel);
        scanMode.setMaximumSize(scanMode.getPreferredSize());
        scanMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        scanMode.setSelectedItem(BuildingCandidateScanner.ScanMode.fromPreference(
                preferences.get(PluginPreferences.PREFIX + "scan.mode", "CONSERVATIVE")));
        scanMode.setToolTipText(tr(
                "Conservative requires strong evidence; Balanced shows moderate candidates; Exploratory deliberately includes weaker borderline possibilities."));
        scanMode.addActionListener(event -> preferences.put(
                PluginPreferences.PREFIX + "scan.mode", selectedScanMode().name()));
        add(scanMode);
        add(Box.createVerticalStrut(5));
        scanButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        scanButton.setEnabled(false);
        scanButton.setToolTipText(tr(
                "Scan the complete task after fitting its entire boundary in the map view."));
        scanButton.addActionListener(event -> scanTask(ScanScope.ENTIRE_TASK));
        scanVisibleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        scanVisibleButton.setEnabled(false);
        scanVisibleButton.setToolTipText(tr(
                "Scan only the part of the task currently visible in the JOSM map view."));
        scanVisibleButton.addActionListener(event -> scanTask(ScanScope.VISIBLE_AREA));
        rescanButton.setEnabled(false);
        rescanButton.setToolTipText(tr(
                "Scan the same area again and omit candidate locations already accepted, rejected or mapped."));
        rescanButton.addActionListener(event -> rescanAfterReview());
        highlightToggleButton.setEnabled(false);
        highlightToggleButton.addActionListener(event -> toggleReviewHighlight());
        mappedBuildingsToggleButton.setEnabled(false);
        mappedBuildingsToggleButton.setToolTipText(tr(
                "Temporarily hide building-tagged OSM objects without changing map data."));
        mappedBuildingsToggleButton.addActionListener(event -> toggleMappedBuildingOutlines());
        learnMissedButton.setToolTipText(tr(
                "Use newly drawn building outlines as positive examples, including buildings the scan missed."));
        learnMissedButton.addActionListener(event -> learnFromNewBuildings());
        add(scanButton);
        add(Box.createVerticalStrut(3));
        add(scanVisibleButton);
        add(Box.createVerticalStrut(5));
        add(state);
        add(Box.createVerticalStrut(6));
        add(summary);
        add(Box.createVerticalStrut(6));
        checklist.setLayout(new BoxLayout(checklist, BoxLayout.Y_AXIS));
        checklist.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklist.setVisible(false);
        add(checklist);
        add(Box.createVerticalStrut(5));
        JLabel warning = wrappingLabel("Estimated candidates are a review checklist, not a building count or mapping instruction. The scan never creates or changes OSM data.");
        warning.setForeground(new Color(150, 65, 0));
        add(warning);
    }

    void setContext(TaskContext context, TaskReference reference) {
        scanGeneration++;
        this.context = context;
        this.reference = reference;
        reset();
        scanButton.setEnabled(true);
        scanVisibleButton.setEnabled(true);
        state.setForeground(new Color(0, 105, 45));
        state.setText("Project loaded. Scan the complete task, or zoom in and scan only the visible area.");
    }

    void clearContext() {
        scanGeneration++;
        context = null;
        reference = null;
        reset();
        scanButton.setEnabled(false);
        scanVisibleButton.setEnabled(false);
        state.setForeground(new Color(150, 65, 0));
        state.setText("Load a HOT task before scanning.");
    }

    private void scanTask(ScanScope scope) {
        scanTask(scope, false);
    }

    private void scanTask(ScanScope scope, boolean excludeReviewed) {
        int generation = ++scanGeneration;
        try {
            clearReviewHighlight();
            if (context == null || reference == null) {
                throw new IllegalArgumentException("Load the HOT task context first.");
            }
            BuildingCheckPanel.verifyAuthorisedImagery(context.getAuthorisedImagery());
            TaskCapture capture = captureTask(reference, scope);
            BuildingCandidateScanner.ScanMode mode = selectedScanMode();
            List<Rectangle> reviewedRegions = excludeReviewed
                    ? reviewedRegionsFor(capture) : new ArrayList<>();
            if (!excludeReviewed) {
                reviewedCandidateAreas.clear();
            }
            setScanButtonsEnabled(false);
            checklist.removeAll();
            checklist.setVisible(false);
            summary.setText(scope == ScanScope.VISIBLE_AREA
                    ? "Scanning the visible part of the task imagery…"
                    : "Scanning the complete task imagery…");
            state.setForeground(new Color(0, 105, 45));
            state.setText("The scan runs locally. Imagery is not saved or transmitted.");

            new SwingWorker<ScanResult, Void>() {
                @Override
                protected ScanResult doInBackground() {
                    BuildingCandidateScanner.Result candidates = BuildingCandidateScanner.scan(
                            capture.image, capture.boundary, capture.mappedPolygons,
                            learningStore.profile(),
                            learningStore.geometryProfile(context.getAuthorisedImagery()),
                            capture.metresPerPixel, mode, reviewedRegions,
                            sharedLearningStore.profile());
                    return new ScanResult(candidates, mappedBuildingsToReview(capture));
                }

                @Override
                protected void done() {
                    if (generation != scanGeneration) {
                        return;
                    }
                    setScanButtonsEnabled(context != null);
                    try {
                        ScanResult result = get();
                        showResult(capture, result.candidates, result.mappedToReview,
                                mode, reviewedRegions.size());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        showError("The scan was interrupted. Try again.");
                    } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        showError(cause == null || cause.getMessage() == null
                                ? "The task could not be scanned." : cause.getMessage());
                    }
                }
            }.execute();
        } catch (IllegalArgumentException exception) {
            setScanButtonsEnabled(context != null);
            showError(exception.getMessage());
        } catch (LinkageError error) {
            setScanButtonsEnabled(context != null);
            showError("Task reconnaissance is not compatible with this JOSM API. Update the companion and try again.");
        }
        revalidate();
        repaint();
    }

    private void setScanButtonsEnabled(boolean enabled) {
        scanButton.setEnabled(enabled);
        scanVisibleButton.setEnabled(enabled);
        scanMode.setEnabled(enabled);
        rescanButton.setEnabled(enabled && displayedCapture != null
                && !reviewedCandidateAreas.isEmpty());
    }

    private BuildingCandidateScanner.ScanMode selectedScanMode() {
        Object selected = scanMode.getSelectedItem();
        return selected instanceof BuildingCandidateScanner.ScanMode
                ? (BuildingCandidateScanner.ScanMode) selected
                : BuildingCandidateScanner.ScanMode.CONSERVATIVE;
    }

    private void rescanAfterReview() {
        if (displayedCapture == null || reviewedCandidateAreas.isEmpty()) {
            state.setForeground(new Color(150, 65, 0));
            state.setText("Review at least one candidate before rescanning.");
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            showError("The JOSM map view is not available.");
            return;
        }
        ScanScope scope = displayedCapture.scope;
        ProjectionBounds originalArea = displayedCapture.scanBounds;
        map.mapView.zoomTo(originalArea);
        map.mapView.repaint();
        state.setForeground(new Color(0, 105, 45));
        state.setText("Returning to the previous scan area before rescanning…");
        SwingUtilities.invokeLater(() -> scanTask(scope, true));
    }

    private void rememberReviewedArea(ProjectionBounds area) {
        if (area != null && !reviewedCandidateAreas.contains(area)) {
            reviewedCandidateAreas.add(area);
        }
    }

    private List<Rectangle> reviewedRegionsFor(TaskCapture capture) {
        List<Rectangle> result = new ArrayList<>();
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            return result;
        }
        Rectangle imageBounds = new Rectangle(0, 0,
                capture.image.getWidth(), capture.image.getHeight());
        for (ProjectionBounds reviewed : reviewedCandidateAreas) {
            Point first = map.mapView.getPoint(reviewed.getMin());
            Point second = map.mapView.getPoint(reviewed.getMax());
            int x = Math.min(first.x, second.x) - capture.crop.x;
            int y = Math.min(first.y, second.y) - capture.crop.y;
            int width = Math.max(1, Math.abs(first.x - second.x));
            int height = Math.max(1, Math.abs(first.y - second.y));
            int paddingX = Math.max(3, width / 4);
            int paddingY = Math.max(3, height / 4);
            Rectangle expanded = new Rectangle(x - paddingX, y - paddingY,
                    width + paddingX * 2, height + paddingY * 2).intersection(imageBounds);
            if (!expanded.isEmpty()) {
                result.add(expanded);
            }
        }
        return result;
    }

    private void showResult(TaskCapture capture, BuildingCandidateScanner.Result result,
            List<MappedBuildingConcern> mappedToReview,
            BuildingCandidateScanner.ScanMode mode, int excludedReviewedCount) {
        displayedCapture = capture;
        displayedResult = result;
        displayedMode = mode;
        reviewDecisions = new CandidateReviewDecisions(result.getCandidates().size());
        candidateItems.clear();
        mappedReviewItems.clear();
        learnedBuildingWays.clear();
        rejectedExpanded = false;
        confirmedMappedExpanded = false;
        notBuildingMappedExpanded = false;
        updateSummary();

        MapView mapView = MainApplication.getMap().mapView;
        int mappedIndex = 1;
        for (MappedBuildingConcern concern : mappedToReview) {
            Rectangle bounds = concern.bounds;
            Rectangle mappedPixels = new Rectangle(capture.crop.x + bounds.x,
                    capture.crop.y + bounds.y, bounds.width, bounds.height);
            Rectangle reviewPixels = CandidateReviewNavigation.reviewRectangle(mappedPixels,
                    new Rectangle(0, 0, mapView.getWidth(), mapView.getHeight()));
            mappedReviewItems.add(createMappedReviewItem(capture.image, concern, mappedIndex,
                    mapView.getProjectionBounds(mappedPixels),
                    mapView.getProjectionBounds(reviewPixels)));
            mappedIndex++;
        }
        int index = 1;
        for (BuildingCandidateScanner.Candidate candidate : result.getCandidates()) {
            Rectangle bounds = candidate.getBounds();
            Rectangle candidatePixels = new Rectangle(capture.crop.x + bounds.x,
                    capture.crop.y + bounds.y, bounds.width, bounds.height);
            Rectangle reviewPixels = CandidateReviewNavigation.reviewRectangle(candidatePixels,
                    new Rectangle(0, 0, mapView.getWidth(), mapView.getHeight()));
            ProjectionBounds candidateArea = mapView.getProjectionBounds(candidatePixels);
            ProjectionBounds reviewArea = mapView.getProjectionBounds(reviewPixels);
            candidateItems.add(createCandidateItem(capture.image, candidate, index,
                    candidateArea, reviewArea));
            index++;
        }
        rebuildChecklist();
        state.setForeground(new Color(0, 105, 45));
        state.setText(capture.scope == ScanScope.VISIBLE_AREA
                ? "Visible-area scan complete. Results cover only the displayed part of the task."
                : "Complete-task scan finished. Review possible unmapped candidates and mapped buildings with unusually weak imagery evidence.");
        if (excludedReviewedCount > 0) {
            state.setText(state.getText() + " " + excludedReviewedCount
                    + " previously reviewed location(s) were omitted.");
        }
        revalidate();
        repaint();
    }

    private CandidateReviewItem createCandidateItem(BufferedImage image,
            BuildingCandidateScanner.Candidate candidate, int candidateNumber,
            ProjectionBounds candidateArea, ProjectionBounds reviewArea) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel(new ImageIcon(candidateThumbnail(image, candidate))));
        row.add(Box.createHorizontalStrut(6));

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        String shape = candidate.getShape() == BuildingCandidateScanner.Shape.ROUND
                ? "round" : "rectangular";
        String confidence = candidate.isHighConfidence() ? "high" : "uncertain";
        JButton review = SidebarButtons.create("Review " + candidateNumber + " · " + shape + " · "
                + candidate.getConfidence() + "/100 (" + confidence + ")");
        review.setAlignmentX(Component.LEFT_ALIGNMENT);
        review.addActionListener(event -> reviewCandidate(candidateNumber,
                candidate.getShape(), candidateArea, reviewArea));
        details.add(review);

        JLabel evidenceLabel = wrappingLabel("Why shown: " + candidate.explanation() + ".");
        evidenceLabel.setForeground(new Color(80, 80, 80));
        details.add(evidenceLabel);

        JLabel decisionLabel = new JLabel(tr("Decision: not reviewed"));
        decisionLabel.setForeground(new Color(95, 95, 95));
        decisionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(decisionLabel);

        JPanel decisions = new JPanel();
        decisions.setLayout(new BoxLayout(decisions, BoxLayout.X_AXIS));
        decisions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton accept = SidebarButtons.create(tr("Accept"));
        accept.setEnabled(false);
        accept.addActionListener(event -> recordDecision(candidateNumber,
                CandidateReviewDecisions.Decision.ACCEPTED));
        JButton reject = SidebarButtons.create(tr("Reject"));
        reject.setEnabled(false);
        reject.addActionListener(event -> recordDecision(candidateNumber,
                CandidateReviewDecisions.Decision.REJECTED));
        decisions.add(accept);
        decisions.add(Box.createHorizontalStrut(3));
        decisions.add(reject);
        details.add(decisions);

        JButton map = SidebarButtons.create(tr("Map this building"));
        map.setAlignmentX(Component.LEFT_ALIGNMENT);
        map.setVisible(false);
        map.addActionListener(event -> mapCandidate(candidateNumber));
        details.add(map);
        JButton checkMapped = SidebarButtons.create(tr("Check if mapped"));
        checkMapped.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkMapped.setVisible(false);
        checkMapped.addActionListener(event -> checkCandidateMapped(candidateNumber));
        details.add(checkMapped);
        GeometryEditControls geometryEdits = createGeometryEditControls();
        geometryEdits.panel.setVisible(false);
        details.add(geometryEdits.panel);
        JButton restore = SidebarButtons.create(tr("Restore"));
        restore.setAlignmentX(Component.LEFT_ALIGNMENT);
        restore.setVisible(false);
        restore.addActionListener(event -> restoreCandidate(candidateNumber));
        details.add(restore);
        row.add(details);

        CandidateReviewItem item = new CandidateReviewItem(candidateNumber, candidate.getShape(), candidateArea,
                reviewArea, candidate.getEvidence(), row, decisionLabel, decisions, accept,
                reject, map, checkMapped, restore, geometryEdits);
        geometryEdits.setSaveAction(() -> saveGeometryEdits(item.geometryEdits));
        return item;
    }

    private static List<MappedBuildingConcern> mappedBuildingsToReview(TaskCapture capture) {
        List<MappedBuildingConcern> concerns = new ArrayList<>();
        for (int index = 0; index < capture.mappedPolygons.size(); index++) {
            Polygon footprint = capture.mappedPolygons.get(index);
            Way way = capture.mappedWays.get(index);
            Rectangle bounds = footprint.getBounds();
            if (bounds.width < 6 || bounds.height < 6) {
                continue;
            }
            BuildingShapeClassifier.Shape mappedShape = BuildingShapeClassifier.classify(
                    footprint, null, null);
            BuildingCandidateScanner.Shape shape = mappedShape == BuildingShapeClassifier.Shape.ROUND
                    ? BuildingCandidateScanner.Shape.ROUND
                    : BuildingCandidateScanner.Shape.RECTANGULAR;
            BuildingCandidateScanner.Assessment assessment = BuildingCandidateScanner.assess(
                    capture.image, shape, bounds);
            if (assessment != null && assessment.getScore() < 45) {
                concerns.add(new MappedBuildingConcern(shape, bounds, assessment.getScore(),
                        assessment.getEvidence(), way,
                        GeometryMeasurement.WaySnapshot.capture(way)));
            }
        }
        concerns.sort(Comparator.comparingInt(concern -> concern.score));
        return new ArrayList<>(concerns.subList(0, Math.min(10, concerns.size())));
    }

    private MappedReviewItem createMappedReviewItem(BufferedImage image,
            MappedBuildingConcern concern, int number, ProjectionBounds mappedArea,
            ProjectionBounds reviewArea) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        BuildingCandidateScanner.Candidate previewCandidate = new BuildingCandidateScanner.Candidate(
                concern.shape, concern.score, concern.score, concern.bounds, concern.evidence);
        row.add(new JLabel(new ImageIcon(candidateThumbnail(image, previewCandidate))));
        row.add(Box.createHorizontalStrut(6));
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton review = SidebarButtons.create("Review mapped " + number + " · "
                + concern.score + "/100 visual evidence");
        review.setAlignmentX(Component.LEFT_ALIGNMENT);
        review.addActionListener(event -> reviewMappedBuilding(number, concern.shape,
                mappedArea, reviewArea));
        details.add(review);
        JButton toggleHighlight = SidebarButtons.create(tr("Show / hide review highlight"));
        toggleHighlight.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleHighlight.setToolTipText(tr(
                "Toggle the labelled review marker so the imagery underneath can be inspected."));
        toggleHighlight.addActionListener(event -> toggleMappedReviewHighlight(number,
                concern.shape, mappedArea, reviewArea));
        details.add(toggleHighlight);
        JLabel decisionLabel = new JLabel(tr("Decision: not reviewed"));
        decisionLabel.setForeground(new Color(95, 95, 95));
        decisionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(decisionLabel);
        JPanel decisions = new JPanel();
        decisions.setLayout(new BoxLayout(decisions, BoxLayout.X_AXIS));
        decisions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton confirm = SidebarButtons.create(tr("Confirm building"));
        confirm.setEnabled(false);
        confirm.addActionListener(event -> recordMappedReviewDecision(number,
                MappedReviewDecision.CONFIRMED_BUILDING));
        JButton notBuilding = SidebarButtons.create(tr("Not a building"));
        notBuilding.setEnabled(false);
        notBuilding.addActionListener(event -> recordMappedReviewDecision(number,
                MappedReviewDecision.NOT_A_BUILDING));
        decisions.add(confirm);
        decisions.add(Box.createHorizontalStrut(3));
        decisions.add(notBuilding);
        details.add(decisions);
        GeometryEditControls geometryEdits = createGeometryEditControls();
        geometryEdits.panel.setVisible(false);
        details.add(geometryEdits.panel);
        JButton restore = SidebarButtons.create(tr("Restore review"));
        restore.setAlignmentX(Component.LEFT_ALIGNMENT);
        restore.setVisible(false);
        restore.addActionListener(event -> restoreMappedReviewDecision(number));
        details.add(restore);
        row.add(details);
        MappedReviewItem item = new MappedReviewItem(number, concern.shape, concern.evidence,
                row, decisionLabel, decisions, confirm, notBuilding, restore, geometryEdits);
        geometryEdits.measurementProvider = () -> GeometryMeasurement.betweenWays(
                concern.originalGeometry, concern.way);
        geometryEdits.setSaveAction(() -> saveGeometryEdits(item.geometryEdits));
        return item;
    }

    private void reviewMappedBuilding(int number, BuildingCandidateScanner.Shape shape,
            ProjectionBounds mappedArea, ProjectionBounds reviewArea) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            showError("The JOSM map view is not available.");
            return;
        }
        clearReviewHighlight();
        map.mapView.zoomTo(reviewArea);
        reviewHighlight = new CandidateHighlight("Mapped review " + number, shape, mappedArea);
        map.mapView.addTemporaryLayer(reviewHighlight);
        reviewHighlightVisible = true;
        activeMappedReviewNumber = number;
        highlightToggleButton.setEnabled(true);
        highlightToggleButton.setText(tr("Hide mapped review highlight"));
        MappedReviewItem item = mappedReviewItem(number);
        item.reviewed = true;
        updateMappedReviewControls(item);
        map.mapView.repaint();
        state.setForeground(new Color(150, 65, 0));
        state.setText("Mapped building " + number
                + " has weak visual evidence in this imagery. Check imagery age, obstruction and alignment before considering any OSM edit.");
    }

    private void recordMappedReviewDecision(int number, MappedReviewDecision decision) {
        MappedReviewItem item = mappedReviewItem(number);
        if (!item.reviewed || decision == MappedReviewDecision.UNREVIEWED) {
            return;
        }
        ViewportAnchor anchor = captureAdjacentMappedReviewAnchor(number);
        item.decision = decision;
        boolean building = decision == MappedReviewDecision.CONFIRMED_BUILDING;
        if (!building) {
            clearGeometryEdits(item.geometryEdits);
        }
        item.learningRecorded = learningStore.observe(reference, item.evidence,
                building, 1.0, 1, false);
        if (item.learningRecorded) {
            item.sharedEventId = queueShared(item.evidence, building, item.shape);
            item.geometryEdits.sharedEventId = item.sharedEventId;
        }
        learningChanged.run();
        updateMappedReviewControls(item);
        clearReviewHighlight();
        state.setForeground(building ? new Color(0, 105, 45) : new Color(150, 65, 0));
        String learningMessage = item.learningRecorded
                ? " The decision was saved as a local learning example."
                : " The per-task learning limit has already been reached.";
        state.setText((building
                ? "Mapped building " + number + " confirmed and removed from the active caution list."
                : "Mapped building " + number + " marked as not a building and removed from the active list. The companion has not changed OSM; inspect and correct the mapped object manually if appropriate.")
                + learningMessage);
        updateSummary();
        rebuildChecklistPreservingAnchor(anchor);
    }

    private void restoreMappedReviewDecision(int number) {
        MappedReviewItem item = mappedReviewItem(number);
        if (item.decision == MappedReviewDecision.UNREVIEWED) {
            return;
        }
        if (item.learningRecorded) {
            boolean building = item.decision == MappedReviewDecision.CONFIRMED_BUILDING;
            learningStore.observe(reference, item.evidence, building, 1.0, -1, false);
            sharedLearningStore.removeQueued(item.sharedEventId);
            learningChanged.run();
        }
        item.decision = MappedReviewDecision.UNREVIEWED;
        item.learningRecorded = false;
        item.sharedEventId = null;
        item.geometryEdits.sharedEventId = null;
        item.reviewed = false;
        clearGeometryEdits(item.geometryEdits);
        updateMappedReviewControls(item);
        state.setForeground(new Color(0, 105, 45));
        state.setText("Mapped building " + number + " restored to the active review list.");
        updateSummary();
        rebuildChecklistPreservingViewport();
    }

    private void updateMappedReviewControls(MappedReviewItem item) {
        boolean undecided = item.decision == MappedReviewDecision.UNREVIEWED;
        item.decisionButtons.setVisible(undecided);
        item.confirm.setEnabled(undecided && item.reviewed);
        item.notBuilding.setEnabled(undecided && item.reviewed);
        item.restore.setVisible(!undecided);
        item.geometryEdits.panel.setVisible(item.reviewed
                && item.decision != MappedReviewDecision.NOT_A_BUILDING);
        if (item.decision == MappedReviewDecision.CONFIRMED_BUILDING) {
            item.decisionLabel.setText(tr("Confirmed: building"));
            item.decisionLabel.setForeground(new Color(0, 105, 45));
        } else if (item.decision == MappedReviewDecision.NOT_A_BUILDING) {
            item.decisionLabel.setText(tr("Marked: not a building — manual OSM correction required"));
            item.decisionLabel.setForeground(new Color(175, 40, 35));
        } else {
            item.decisionLabel.setText(item.reviewed ? tr("Decision needed")
                    : tr("Decision: not reviewed"));
            item.decisionLabel.setForeground(new Color(95, 95, 95));
        }
    }

    private void toggleMappedReviewHighlight(int number,
            BuildingCandidateScanner.Shape shape, ProjectionBounds mappedArea,
            ProjectionBounds reviewArea) {
        if (reviewHighlight == null || activeMappedReviewNumber != number) {
            reviewMappedBuilding(number, shape, mappedArea, reviewArea);
            return;
        }
        toggleReviewHighlight();
    }

    private void rebuildChecklist() {
        checklist.removeAll();
        JButton overview = SidebarButtons.create(tr("Return to task overview"));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);
        overview.addActionListener(event -> returnToOverview(displayedCapture.taskBounds));
        checklist.add(overview);
        checklist.add(Box.createVerticalStrut(5));
        mappedBuildingsToggleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        mappedBuildingsToggleButton.setEnabled(true);
        checklist.add(mappedBuildingsToggleButton);
        checklist.add(Box.createVerticalStrut(5));
        learnMissedButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        learnMissedButton.setEnabled(displayedCapture != null);
        checklist.add(learnMissedButton);
        checklist.add(Box.createVerticalStrut(5));
        rescanButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        rescanButton.setEnabled(displayedCapture != null && !reviewedCandidateAreas.isEmpty());
        rescanButton.setText(reviewedCandidateAreas.isEmpty()
                ? tr("Rescan after review")
                : tr("Rescan after review ({0} location(s) excluded)",
                        reviewedCandidateAreas.size()));
        checklist.add(rescanButton);
        checklist.add(Box.createVerticalStrut(7));
        int mappedUnreviewed = mappedReviewCount(MappedReviewDecision.UNREVIEWED);
        if (mappedUnreviewed > 0) {
            JLabel mappedHeading = new JLabel("<html><b>Mapped buildings to review ("
                    + mappedUnreviewed + ")</b></html>");
            mappedHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
            checklist.add(mappedHeading);
            checklist.add(wrappingLabel("These outlines have unusually weak visual evidence in the current authorised imagery. They may still be genuine buildings."));
            checklist.add(Box.createVerticalStrut(4));
            for (MappedReviewItem item : mappedReviewItems) {
                if (item.decision == MappedReviewDecision.UNREVIEWED) {
                    checklist.add(item.row);
                    checklist.add(Box.createVerticalStrut(5));
                }
            }
        }
        addMappedReviewSection("confirmed mapped buildings", MappedReviewDecision.CONFIRMED_BUILDING,
                confirmedMappedExpanded);
        addMappedReviewSection("mapped objects marked not a building",
                MappedReviewDecision.NOT_A_BUILDING, notBuildingMappedExpanded);
        if (candidateItems.isEmpty() && mappedReviewItems.isEmpty()) {
            checklist.add(wrappingLabel("No unmapped candidates met the conservative review threshold in this rendered view."));
            checklist.setVisible(true);
            return;
        }
        highlightToggleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklist.add(highlightToggleButton);
        checklist.add(Box.createVerticalStrut(7));

        addCandidateSection("Candidates to review", CandidateReviewDecisions.Decision.UNREVIEWED);
        addCandidateSection("Accepted — awaiting manual mapping", CandidateReviewDecisions.Decision.ACCEPTED);
        addCandidateSection("Mapped during this review", CandidateReviewDecisions.Decision.MAPPED);

        int rejected = reviewDecisions.count(CandidateReviewDecisions.Decision.REJECTED);
        if (rejected > 0) {
            JButton rejectedToggle = SidebarButtons.create((rejectedExpanded ? "Hide" : "Show")
                    + " rejected candidates (" + rejected + ")");
            rejectedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            rejectedToggle.addActionListener(event -> {
                rejectedExpanded = !rejectedExpanded;
                rebuildChecklistPreservingViewport();
            });
            checklist.add(rejectedToggle);
            checklist.add(Box.createVerticalStrut(5));
            if (rejectedExpanded) {
                for (CandidateReviewItem item : candidateItems) {
                    if (reviewDecisions.get(item.candidateNumber)
                            == CandidateReviewDecisions.Decision.REJECTED) {
                        checklist.add(item.row);
                        checklist.add(Box.createVerticalStrut(5));
                    }
                }
            }
        }
        checklist.setVisible(true);
        checklist.revalidate();
        checklist.repaint();
    }

    private void addMappedReviewSection(String title, MappedReviewDecision decision,
            boolean expanded) {
        int count = mappedReviewCount(decision);
        if (count < 1) {
            return;
        }
        JButton toggle = SidebarButtons.create((expanded ? "Hide " : "Show ")
                + title + " (" + count + ")");
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addActionListener(event -> {
            if (decision == MappedReviewDecision.CONFIRMED_BUILDING) {
                confirmedMappedExpanded = !confirmedMappedExpanded;
            } else {
                notBuildingMappedExpanded = !notBuildingMappedExpanded;
            }
            rebuildChecklistPreservingViewport();
        });
        checklist.add(toggle);
        checklist.add(Box.createVerticalStrut(5));
        if (!expanded) {
            return;
        }
        for (MappedReviewItem item : mappedReviewItems) {
            if (item.decision == decision) {
                checklist.add(item.row);
                checklist.add(Box.createVerticalStrut(5));
            }
        }
    }

    private int mappedReviewCount(MappedReviewDecision decision) {
        int count = 0;
        for (MappedReviewItem item : mappedReviewItems) {
            if (item.decision == decision) {
                count++;
            }
        }
        return count;
    }

    /**
     * Rebuild a changing candidate list without allowing Swing focus transfer or
     * layout validation to scroll the containing companion sidebar elsewhere.
     */
    private void rebuildChecklistPreservingViewport() {
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                JViewport.class, checklist);
        Point position = viewport == null ? null : viewport.getViewPosition();
        releaseChecklistFocus();
        rebuildChecklist();
        revalidate();
        repaint();
        if (viewport == null || position == null) {
            return;
        }
        restoreViewportPosition(viewport, position, false);
    }

    private static void restoreViewportPosition(JViewport viewport, Point requested,
            boolean deferred) {
        SwingUtilities.invokeLater(() -> {
            Dimension view = viewport.getViewSize();
            Dimension extent = viewport.getExtentSize();
            int x = Math.max(0, Math.min(requested.x, Math.max(0, view.width - extent.width)));
            int y = Math.max(0, Math.min(requested.y, Math.max(0, view.height - extent.height)));
            viewport.setViewPosition(new Point(x, y));
            if (!deferred) {
                // Focus changes caused by removing the pressed button may be queued
                // after validation, so restore once more on the following EDT turn.
                restoreViewportPosition(viewport, requested, true);
            }
        });
    }

    private void addCandidateSection(String title, CandidateReviewDecisions.Decision decision) {
        int count = reviewDecisions.count(decision);
        if (count < 1) {
            return;
        }
        JLabel heading = new JLabel("<html><b>" + escapeHtml(title) + " (" + count + ")</b></html>");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklist.add(heading);
        checklist.add(Box.createVerticalStrut(4));
        for (CandidateReviewItem item : candidateItems) {
            if (reviewDecisions.get(item.candidateNumber) == decision) {
                checklist.add(item.row);
                checklist.add(Box.createVerticalStrut(5));
            }
        }
    }

    private void updateSummary() {
        if (displayedCapture == null || displayedResult == null || reviewDecisions == null) {
            return;
        }
        TaskCapture capture = displayedCapture;
        BuildingCandidateScanner.Result result = displayedResult;
        int highRectangular = result.count(BuildingCandidateScanner.Shape.RECTANGULAR, true);
        int highRound = result.count(BuildingCandidateScanner.Shape.ROUND, true);
        int uncertain = result.getCandidates().size() - highRectangular - highRound;
        String scopeLabel = capture.scope == ScanScope.VISIBLE_AREA
                ? "Visible-area scan — counts below cover only the displayed part of the task."
                : "Complete-task scan.";
        summary.setText("<html><div style='width:300px'><b>" + scopeLabel + "</b><br>"
                + "<b>Scan sensitivity:</b> " + escapeHtml(displayedMode.toString()) + ".<br>"
                + "<b>Already mapped in downloaded OSM data:</b> "
                + capture.inventory.rectangular + " rectangular/orthogonal, "
                + capture.inventory.round + " round, " + capture.inventory.other + " other.<br>"
                + "<b>Mapped-building review:</b> "
                + mappedReviewCount(MappedReviewDecision.UNREVIEWED) + " awaiting review, "
                + mappedReviewCount(MappedReviewDecision.CONFIRMED_BUILDING) + " confirmed, "
                + mappedReviewCount(MappedReviewDecision.NOT_A_BUILDING) + " marked not a building.<br>"
                + "<b>Possible unmapped candidates:</b> " + highRectangular
                + " rectangular, " + highRound + " round, " + uncertain
                + " uncertain.<br><b>Review decisions:</b> "
                + reviewDecisions.count(CandidateReviewDecisions.Decision.ACCEPTED) + " awaiting mapping, "
                + reviewDecisions.count(CandidateReviewDecisions.Decision.MAPPED) + " mapped, "
                + reviewDecisions.count(CandidateReviewDecisions.Decision.REJECTED) + " rejected, "
                + reviewDecisions.count(CandidateReviewDecisions.Decision.UNREVIEWED) + " not reviewed."
                + "<br><i>The candidate line is an estimate from the currently rendered imagery; review decisions do not edit OSM.</i></div></html>");
    }

    private static TaskCapture captureTask(TaskReference reference, ScanScope scope) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null || map.mapView.getWidth() < 1 || map.mapView.getHeight() < 1) {
            throw new IllegalArgumentException("The JOSM map view is not available.");
        }
        MapView mapView = map.mapView;
        Way boundaryWay = findBoundaryWay(reference, mapView);
        Polygon mapBoundary = polygon(mapView, boundaryWay);
        Rectangle bounds = mapBoundary.getBounds();
        Rectangle view = new Rectangle(0, 0, mapView.getWidth(), mapView.getHeight());
        Rectangle crop = bounds.intersection(view);
        if (scope == ScanScope.ENTIRE_TASK
                && (!view.contains(bounds) || bounds.width < 160 || bounds.height < 160)) {
            throw new IllegalArgumentException("Keep the complete task boundary visible and large enough to inspect, then scan again.");
        }
        if (scope == ScanScope.VISIBLE_AREA
                && (crop.width < 160 || crop.height < 160 || !taskIntersects(mapBoundary, crop))) {
            throw new IllegalArgumentException("Zoom to a useful part of the task boundary, keep at least a moderate area visible, then scan the visible area again.");
        }
        Inventory inventory = new Inventory();
        List<Way> includedWays = new ArrayList<>();
        List<Polygon> mappedOnMap = mappedBuildings(mapView, mapBoundary, crop, inventory,
                includedWays);

        BufferedImage mapImage = new BufferedImage(mapView.getWidth(), mapView.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = mapImage.createGraphics();
        try {
            BuildingCheckPanel.renderVisibleImagery(mapView, graphics,
                    BuildingCheckPanel.visibleImageryLayers());
        } finally {
            graphics.dispose();
        }

        BufferedImage image = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D copy = image.createGraphics();
        try {
            copy.drawImage(mapImage, 0, 0, crop.width, crop.height,
                    crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
        } finally {
            copy.dispose();
        }
        Polygon localBoundary = translate(mapBoundary, -crop.x, -crop.y);
        List<Polygon> localMapped = new ArrayList<>();
        for (Polygon mapped : mappedOnMap) {
            localMapped.add(translate(mapped, -crop.x, -crop.y));
        }
        return new TaskCapture(image, localBoundary, localMapped, crop, inventory,
                mapView.getProjectionBounds(bounds), mapView.getProjectionBounds(crop),
                mapBoundary, includedWays, scope,
                Math.abs(mapView.getDist100Pixel(true)) / 100.0);
    }

    private void reviewCandidate(int candidateNumber, BuildingCandidateScanner.Shape shape,
            ProjectionBounds candidateArea, ProjectionBounds reviewArea) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            showError("The JOSM map view is not available.");
            return;
        }
        clearReviewHighlight();
        MapView mapView = map.mapView;
        mapView.zoomTo(reviewArea);
        reviewHighlight = new CandidateHighlight(candidateNumber, shape, candidateArea);
        mapView.addTemporaryLayer(reviewHighlight);
        reviewHighlightVisible = true;
        activeCandidateNumber = candidateNumber;
        highlightToggleButton.setEnabled(true);
        highlightToggleButton.setText(tr("Hide candidate outline"));
        CandidateReviewItem item = candidateItem(candidateNumber);
        item.reviewed = true;
        updateCandidateControls(item);
        mapView.repaint();
        state.setForeground(new Color(0, 105, 45));
        state.setText("Candidate " + candidateNumber
                + " is highlighted. Inspect the imagery before deciding whether to map it.");
    }

    private void recordDecision(int candidateNumber, CandidateReviewDecisions.Decision decision) {
        if (reviewDecisions == null || candidateNumber < 1) {
            return;
        }
        ViewportAnchor anchor = captureAdjacentCandidateAnchor(candidateNumber);
        CandidateReviewItem item = candidateItem(candidateNumber);
        CandidateReviewDecisions.Decision previous = reviewDecisions.get(candidateNumber);
        reviewDecisions.set(candidateNumber, decision);
        rememberReviewedArea(item.candidateArea);
        if (decision == CandidateReviewDecisions.Decision.REJECTED
                && previous != CandidateReviewDecisions.Decision.REJECTED) {
            item.negativeLearned = learningStore.observe(reference, item.evidence,
                    false, 1.0, 1);
            if (item.negativeLearned) {
                item.sharedEventId = queueShared(item.evidence, false, item.shape);
            }
            learningChanged.run();
        }
        if (decision == CandidateReviewDecisions.Decision.ACCEPTED) {
            state.setText("Candidate " + candidateNumber
                    + " accepted and moved to Awaiting manual mapping.");
        } else {
            state.setText("Candidate " + candidateNumber
                    + " rejected and removed from the active review list."
                    + (item.negativeLearned
                            ? " It was saved as a negative learning example."
                            : " The per-task learning limit has already been reached."));
        }
        state.setForeground(new Color(0, 105, 45));
        updateCandidateControls(item);
        leaveCandidateReview();
        updateSummary();
        rebuildChecklistPreservingAnchor(anchor);
    }

    private void updateCandidateControls(CandidateReviewItem item) {
        CandidateReviewDecisions.Decision decision = reviewDecisions.get(item.candidateNumber);
        boolean decisionAvailable = item.reviewed
                && decision != CandidateReviewDecisions.Decision.REJECTED
                && decision != CandidateReviewDecisions.Decision.MAPPED;
        item.decisionButtons.setVisible(decisionAvailable);
        item.accept.setEnabled(decisionAvailable
                && decision != CandidateReviewDecisions.Decision.ACCEPTED);
        item.reject.setEnabled(decisionAvailable);
        item.map.setVisible(decision == CandidateReviewDecisions.Decision.ACCEPTED);
        item.checkMapped.setVisible(decision == CandidateReviewDecisions.Decision.ACCEPTED);
        item.geometryEdits.panel.setVisible(decision == CandidateReviewDecisions.Decision.MAPPED);
        item.restore.setVisible(decision == CandidateReviewDecisions.Decision.REJECTED
                || decision == CandidateReviewDecisions.Decision.MAPPED);
        if (decision == CandidateReviewDecisions.Decision.ACCEPTED) {
            item.decisionLabel.setText(tr("Accepted: awaiting manual mapping"));
            item.decisionLabel.setForeground(new Color(0, 115, 55));
        } else if (decision == CandidateReviewDecisions.Decision.REJECTED) {
            item.decisionLabel.setText(tr("Rejected: not a building"));
            item.decisionLabel.setForeground(new Color(175, 40, 35));
        } else if (decision == CandidateReviewDecisions.Decision.MAPPED) {
            item.decisionLabel.setText(tr("Mapped: building outline found"));
            item.decisionLabel.setForeground(new Color(0, 90, 145));
        } else {
            item.decisionLabel.setText(item.reviewed
                    ? tr("Decision needed") : tr("Decision: not reviewed"));
            item.decisionLabel.setForeground(new Color(95, 95, 95));
        }
    }

    private void mapCandidate(int candidateNumber) {
        restoreMappedBuildingOutlines();
        CandidateReviewItem item = candidateItem(candidateNumber);
        reviewCandidate(candidateNumber, item.shape, item.candidateArea, item.reviewArea);
        toggleReviewHighlight();
        state.setForeground(new Color(0, 105, 45));
        state.setText("Candidate " + candidateNumber
                + " is ready for manual tracing. Use JOSM's Draw tool (A), trace the roof, "
                + "tag it as a building, then click Check if mapped.");
    }

    private void checkCandidateMapped(int candidateNumber) {
        CandidateReviewItem item = candidateItem(candidateNumber);
        MapFrame map = MainApplication.getMap();
        DataSet data = MainApplication.getLayerManager().getEditDataSet();
        if (map == null || map.mapView == null || data == null) {
            showError("The editable OSM data layer is not available.");
            return;
        }
        Point first = map.mapView.getPoint(item.candidateArea.getMin());
        Point second = map.mapView.getPoint(item.candidateArea.getMax());
        int centreX = (first.x + second.x) / 2;
        int centreY = (first.y + second.y) / 2;
        Way foundWay = null;
        for (Way way : data.getWays()) {
            if (way.isClosed() && !way.isDeleted() && !way.isIncomplete()
                    && !way.hasIncompleteNodes() && way.hasKey("building")
                    && way.getNodes().size() >= 4
                    && polygon(map.mapView, way).contains(centreX, centreY)) {
                foundWay = way;
                break;
            }
        }
        if (foundWay == null) {
            state.setForeground(new Color(150, 65, 0));
            state.setText("No complete closed building outline was found over candidate "
                    + candidateNumber + ". Finish drawing and tagging it, then check again.");
            return;
        }
        reviewDecisions.set(candidateNumber, CandidateReviewDecisions.Decision.MAPPED);
        item.geometryEdits.measurement = GeometryMeasurement.candidateToWay(item.candidateArea,
                item.shape, foundWay, map.mapView);
        item.positiveLearned = learningStore.observe(reference, item.evidence,
                true, 1.0, 1);
        if (item.positiveLearned) {
            item.sharedEventId = queueShared(item.evidence, true, item.shape);
            item.geometryEdits.sharedEventId = item.sharedEventId;
        }
        learnedBuildingWays.add(foundWay);
        learningChanged.run();
        updateCandidateControls(item);
        clearReviewHighlight();
        updateSummary();
        rebuildChecklistPreservingViewport();
        state.setForeground(new Color(0, 105, 45));
        state.setText("Candidate " + candidateNumber
                + " moved to Mapped. The plugin did not create or modify the outline.");
    }

    private void restoreCandidate(int candidateNumber) {
        CandidateReviewItem item = candidateItem(candidateNumber);
        CandidateReviewDecisions.Decision previous = reviewDecisions.get(candidateNumber);
        CandidateReviewDecisions.Decision restored = previous == CandidateReviewDecisions.Decision.MAPPED
                ? CandidateReviewDecisions.Decision.ACCEPTED
                : CandidateReviewDecisions.Decision.UNREVIEWED;
        reviewDecisions.set(candidateNumber, restored);
        if (previous == CandidateReviewDecisions.Decision.REJECTED) {
            reviewedCandidateAreas.remove(item.candidateArea);
        }
        if (previous == CandidateReviewDecisions.Decision.REJECTED && item.negativeLearned) {
            learningStore.observe(reference, item.evidence, false, 1.0, -1);
            sharedLearningStore.removeQueued(item.sharedEventId);
            item.negativeLearned = false;
            learningChanged.run();
        } else if (previous == CandidateReviewDecisions.Decision.MAPPED && item.positiveLearned) {
            learningStore.observe(reference, item.evidence, true, 1.0, -1);
            sharedLearningStore.removeQueued(item.sharedEventId);
            item.positiveLearned = false;
            learningChanged.run();
        }
        item.sharedEventId = null;
        item.geometryEdits.sharedEventId = null;
        if (previous == CandidateReviewDecisions.Decision.MAPPED) {
            clearGeometryEdits(item.geometryEdits);
        }
        updateCandidateControls(item);
        updateSummary();
        rebuildChecklistPreservingViewport();
        state.setForeground(new Color(0, 105, 45));
        state.setText(previous == CandidateReviewDecisions.Decision.MAPPED
                ? "Candidate " + candidateNumber + " restored to Awaiting manual mapping."
                : "Candidate " + candidateNumber + " restored to the active review list.");
    }

    private ViewportAnchor captureAdjacentCandidateAnchor(int candidateNumber) {
        CandidateReviewItem anchorItem = null;
        for (CandidateReviewItem item : candidateItems) {
            if (item.candidateNumber > candidateNumber
                    && reviewDecisions.get(item.candidateNumber)
                            != CandidateReviewDecisions.Decision.REJECTED) {
                anchorItem = item;
                break;
            }
        }
        if (anchorItem == null) {
            for (int index = candidateItems.size() - 1; index >= 0; index--) {
                CandidateReviewItem item = candidateItems.get(index);
                if (item.candidateNumber < candidateNumber
                        && reviewDecisions.get(item.candidateNumber)
                                != CandidateReviewDecisions.Decision.REJECTED) {
                    anchorItem = item;
                    break;
                }
            }
        }
        if (anchorItem == null || !anchorItem.row.isShowing()) {
            return null;
        }
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                JViewport.class, anchorItem.row);
        if (viewport == null) {
            return null;
        }
        int screenY = SwingUtilities.convertPoint(anchorItem.row, 0, 0, viewport).y;
        return new ViewportAnchor(viewport, anchorItem.row, screenY);
    }

    private ViewportAnchor captureAdjacentMappedReviewAnchor(int number) {
        MappedReviewItem anchorItem = null;
        for (MappedReviewItem item : mappedReviewItems) {
            if (item.number > number && item.decision == MappedReviewDecision.UNREVIEWED) {
                anchorItem = item;
                break;
            }
        }
        if (anchorItem == null) {
            for (int index = mappedReviewItems.size() - 1; index >= 0; index--) {
                MappedReviewItem item = mappedReviewItems.get(index);
                if (item.number < number && item.decision == MappedReviewDecision.UNREVIEWED) {
                    anchorItem = item;
                    break;
                }
            }
        }
        if (anchorItem == null || !anchorItem.row.isShowing()) {
            return null;
        }
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                JViewport.class, anchorItem.row);
        if (viewport == null) {
            return null;
        }
        int screenY = SwingUtilities.convertPoint(anchorItem.row, 0, 0, viewport).y;
        return new ViewportAnchor(viewport, anchorItem.row, screenY);
    }

    private void rebuildChecklistPreservingAnchor(ViewportAnchor anchor) {
        if (anchor == null) {
            rebuildChecklistPreservingViewport();
            return;
        }
        releaseChecklistFocus();
        rebuildChecklist();
        revalidate();
        repaint();
        restoreViewportAnchor(anchor, false);
    }

    private void releaseChecklistFocus() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusOwner();
        if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, checklist)) {
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.requestFocusInWindow();
        } else {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        }
    }

    private static void restoreViewportAnchor(ViewportAnchor anchor, boolean deferred) {
        SwingUtilities.invokeLater(() -> {
            if (!anchor.component.isShowing()) {
                return;
            }
            int currentScreenY = SwingUtilities.convertPoint(
                    anchor.component, 0, 0, anchor.viewport).y;
            Point position = anchor.viewport.getViewPosition();
            Dimension view = anchor.viewport.getViewSize();
            Dimension extent = anchor.viewport.getExtentSize();
            int y = CandidateReviewNavigation.anchoredViewY(position.y,
                    currentScreenY, anchor.screenY, view.height, extent.height);
            anchor.viewport.setViewPosition(new Point(position.x, y));
            if (!deferred) {
                restoreViewportAnchor(anchor, true);
            }
        });
    }

    private CandidateReviewItem candidateItem(int candidateNumber) {
        if (candidateNumber < 1 || candidateNumber > candidateItems.size()) {
            throw new IllegalArgumentException("Candidate number is outside the review list.");
        }
        return candidateItems.get(candidateNumber - 1);
    }

    private MappedReviewItem mappedReviewItem(int number) {
        if (number < 1 || number > mappedReviewItems.size()) {
            throw new IllegalArgumentException("Mapped review number is outside the review list.");
        }
        return mappedReviewItems.get(number - 1);
    }

    private void leaveCandidateReview() {
        clearReviewHighlight();
        MapFrame map = MainApplication.getMap();
        if (displayedCapture != null && map != null && map.mapView != null) {
            map.mapView.zoomTo(displayedCapture.taskBounds);
        }
    }

    private void returnToOverview(ProjectionBounds taskBounds) {
        clearReviewHighlight();
        MapFrame map = MainApplication.getMap();
        if (map != null && map.mapView != null) {
            map.mapView.zoomTo(taskBounds);
            state.setForeground(new Color(0, 105, 45));
            state.setText("Returned to the complete task overview.");
        }
    }

    private void toggleReviewHighlight() {
        if (reviewHighlight == null) {
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (map == null || map.mapView == null) {
            showError("The JOSM map view is not available.");
            return;
        }
        if (reviewHighlightVisible) {
            map.mapView.removeTemporaryLayer(reviewHighlight);
            reviewHighlightVisible = false;
            if (activeMappedReviewNumber > 0) {
                highlightToggleButton.setText(tr("Show mapped review highlight"));
                state.setText("Mapped review highlight hidden. Inspect the imagery underneath, then show it again if needed.");
            } else {
                highlightToggleButton.setText(tr("Show candidate outline"));
                state.setText("Candidate outline hidden. Review the imagery, then show it again if needed.");
            }
        } else {
            map.mapView.addTemporaryLayer(reviewHighlight);
            reviewHighlightVisible = true;
            if (activeMappedReviewNumber > 0) {
                highlightToggleButton.setText(tr("Hide mapped review highlight"));
                state.setText("Mapped review highlight shown.");
            } else {
                highlightToggleButton.setText(tr("Hide candidate outline"));
                state.setText("Candidate outline shown.");
            }
        }
        state.setForeground(new Color(0, 105, 45));
        map.mapView.repaint();
    }

    private void toggleMappedBuildingOutlines() {
        if (mappedBuildingFilter.isHidden()) {
            restoreMappedBuildingOutlines();
            state.setForeground(new Color(0, 105, 45));
            state.setText("Mapped building outlines shown again.");
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (map == null || map.filterDialog == null) {
            showError("JOSM's filter controls are not available.");
            return;
        }
        FilterTableModel model = map.filterDialog.getFilterModel();
        if (model == null) {
            showError("JOSM's filter controls are not available.");
            return;
        }
        mappedBuildingFilter.hide(model);
        mappedBuildingsToggleButton.setText(tr("Show mapped building outlines"));
        state.setForeground(new Color(0, 105, 45));
        state.setText("Mapped building outlines hidden temporarily. Imagery and the candidate marker remain visible.");
    }

    private void learnFromNewBuildings() {
        if (displayedCapture == null || reference == null) {
            showError("Run the task scan before collecting newly drawn buildings.");
            return;
        }
        restoreMappedBuildingOutlines();
        returnToOverview(displayedCapture.scope == ScanScope.VISIBLE_AREA
                ? displayedCapture.scanBounds : displayedCapture.taskBounds);
        state.setForeground(new Color(0, 90, 145));
        state.setText("Checking building outlines drawn since this scan…");
        SwingUtilities.invokeLater(this::collectNewBuildingExamples);
    }

    private void collectNewBuildingExamples() {
        try {
            TaskCapture fresh = captureTask(reference, displayedCapture.scope);
            MapFrame map = MainApplication.getMap();
            DataSet data = MainApplication.getLayerManager().getEditDataSet();
            if (map == null || map.mapView == null || data == null) {
                throw new IllegalArgumentException("The editable OSM data layer is not available.");
            }
            int learned = 0;
            for (Way way : data.getWays()) {
                if (displayedCapture.initialBuildingWays.contains(way)
                        || learnedBuildingWays.contains(way)
                        || !way.isClosed() || way.isDeleted() || way.isIncomplete()
                        || way.hasIncompleteNodes() || !way.hasKey("building")
                        || way.getNodes().size() < 4) {
                    continue;
                }
                Polygon onMap = polygon(map.mapView, way);
                Rectangle mapBounds = onMap.getBounds();
                if (!fresh.mapBoundary.contains(mapBounds.getCenterX(), mapBounds.getCenterY())) {
                    continue;
                }
                Polygon local = translate(onMap, -fresh.crop.x, -fresh.crop.y);
                BuildingShapeClassifier.Shape classified = BuildingShapeClassifier.classify(local,
                        way.get("building"), way.get("building:shape"));
                BuildingCandidateScanner.Shape shape = classified == BuildingShapeClassifier.Shape.ROUND
                        ? BuildingCandidateScanner.Shape.ROUND
                        : BuildingCandidateScanner.Shape.RECTANGULAR;
                BuildingCandidateScanner.Evidence evidence;
                try {
                    evidence = BuildingCandidateScanner.evidenceFor(
                            fresh.image, shape, local.getBounds());
                } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                    // One partly visible or otherwise unusable outline must not
                    // abort learning from every other newly drawn building.
                    continue;
                }
                if (evidence != null) {
                    if (learningStore.observe(reference, evidence, true, 1.5, 1)) {
                        queueShared(evidence, true, shape);
                        learnedBuildingWays.add(way);
                        learned++;
                    }
                }
            }
            learningChanged.run();
            state.setForeground(new Color(0, 105, 45));
            state.setText(learned == 0
                    ? "No new unrecorded building outlines were found inside this task."
                    : "Learned from " + learned + " newly drawn building(s), including scan misses.");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    void restoreMappedBuildingOutlines() {
        mappedBuildingFilter.show();
        mappedBuildingsToggleButton.setText(tr("Hide mapped building outlines"));
    }

    private void clearReviewHighlight() {
        if (reviewHighlight == null) {
            return;
        }
        MapFrame map = MainApplication.getMap();
        if (reviewHighlightVisible && map != null && map.mapView != null) {
            map.mapView.removeTemporaryLayer(reviewHighlight);
            map.mapView.repaint();
        }
        reviewHighlight = null;
        reviewHighlightVisible = false;
        activeCandidateNumber = -1;
        activeMappedReviewNumber = -1;
        highlightToggleButton.setEnabled(false);
        highlightToggleButton.setText(tr("Hide candidate outline"));
    }

    private static Way findBoundaryWay(TaskReference reference, MapView mapView) {
        Way largest = null;
        double largestArea = -1;
        for (Layer layer : MainApplication.getLayerManager().getLayers()) {
            TaskReference layerReference = TaskLayerNameParser.parse(layer.getName());
            if (layerReference == null || layerReference.getProjectId() != reference.getProjectId()
                    || layerReference.getTaskId() != reference.getTaskId()
                    || !(layer instanceof OsmDataLayer)) {
                continue;
            }
            for (Way way : ((OsmDataLayer) layer).getDataSet().getWays()) {
                if (!TaskBoundaryGeometry.isClosed(way) || way.hasIncompleteNodes()) {
                    continue;
                }
                Polygon candidate = polygon(mapView, way);
                double area = Math.abs(area(candidate));
                if (area > largestArea) {
                    largest = way;
                    largestArea = area;
                }
            }
        }
        if (largest == null) {
            throw new IllegalArgumentException("The HOT task boundary geometry could not be read. Reopen the task from the Tasking Manager and try again.");
        }
        return largest;
    }

    private static List<Polygon> mappedBuildings(MapView mapView, Polygon boundary,
            Rectangle scanArea, Inventory inventory, List<Way> includedWays) {
        DataSet data = MainApplication.getLayerManager().getEditDataSet();
        if (data == null) {
            throw new IllegalArgumentException("No editable OSM data layer is active.");
        }
        List<Polygon> result = new ArrayList<>();
        for (Way way : data.getWays()) {
            if (!way.isClosed() || way.isDeleted() || way.isIncomplete() || way.hasIncompleteNodes()
                    || !way.hasKey("building") || way.getNodes().size() < 4) {
                continue;
            }
            Polygon footprint = polygon(mapView, way);
            Rectangle footprintBounds = footprint.getBounds();
            double centreX = footprintBounds.getCenterX();
            double centreY = footprintBounds.getCenterY();
            if (!boundary.contains(centreX, centreY) || !scanArea.contains(centreX, centreY)) {
                continue;
            }
            result.add(footprint);
            includedWays.add(way);
            BuildingShapeClassifier.Shape shape = BuildingShapeClassifier.classify(footprint,
                    way.get("building"), way.get("building:shape"));
            if (shape == BuildingShapeClassifier.Shape.ROUND) {
                inventory.round++;
            } else if (shape == BuildingShapeClassifier.Shape.RECTANGULAR) {
                inventory.rectangular++;
            } else {
                inventory.other++;
            }
        }
        return result;
    }

    private static boolean taskIntersects(Polygon boundary, Rectangle area) {
        if (boundary.intersects(area)) {
            return true;
        }
        return boundary.contains(area.getCenterX(), area.getCenterY())
                || area.contains(boundary.getBounds().getCenterX(),
                        boundary.getBounds().getCenterY());
    }

    private static Polygon polygon(MapView mapView, Way way) {
        List<Node> nodes = way.getNodes();
        int count = nodes.size();
        if (count > 1 && TaskBoundaryGeometry.sameLocation(nodes.get(0), nodes.get(count - 1))) {
            count--;
        }
        int[] x = new int[count];
        int[] y = new int[count];
        for (int index = 0; index < count; index++) {
            Point point = mapView.getPoint(nodes.get(index));
            x[index] = point.x;
            y[index] = point.y;
        }
        return new Polygon(x, y, count);
    }

    private static Polygon translate(Polygon source, int dx, int dy) {
        int[] x = new int[source.npoints];
        int[] y = new int[source.npoints];
        for (int index = 0; index < source.npoints; index++) {
            x[index] = source.xpoints[index] + dx;
            y[index] = source.ypoints[index] + dy;
        }
        return new Polygon(x, y, source.npoints);
    }

    private static double area(Polygon polygon) {
        double twice = 0;
        for (int index = 0; index < polygon.npoints; index++) {
            int next = (index + 1) % polygon.npoints;
            twice += polygon.xpoints[index] * (double) polygon.ypoints[next]
                    - polygon.xpoints[next] * (double) polygon.ypoints[index];
        }
        return twice / 2.0;
    }

    private static Image candidateThumbnail(BufferedImage image,
            BuildingCandidateScanner.Candidate candidate) {
        Rectangle box = candidate.getBounds();
        int padding = Math.max(8, Math.max(box.width, box.height) / 2);
        Rectangle crop = new Rectangle(box.x - padding, box.y - padding,
                box.width + padding * 2, box.height + padding * 2)
                .intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        BufferedImage preview = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, crop.width, crop.height,
                    crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
            graphics.setColor(new Color(255, 55, 35));
            graphics.setStroke(new BasicStroke(2.2f));
            int x = box.x - crop.x;
            int y = box.y - crop.y;
            if (candidate.getShape() == BuildingCandidateScanner.Shape.ROUND) {
                graphics.drawOval(x, y, box.width, box.height);
            } else {
                graphics.drawRect(x, y, box.width, box.height);
            }
        } finally {
            graphics.dispose();
        }
        BufferedImage scaled = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D scaledGraphics = scaled.createGraphics();
        try {
            scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            scaledGraphics.drawImage(preview, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, null);
        } finally {
            scaledGraphics.dispose();
        }
        return scaled;
    }

    private void reset() {
        restoreMappedBuildingOutlines();
        clearReviewHighlight();
        checklist.removeAll();
        checklist.setVisible(false);
        reviewDecisions = null;
        candidateItems.clear();
        mappedReviewItems.clear();
        rejectedExpanded = false;
        confirmedMappedExpanded = false;
        notBuildingMappedExpanded = false;
        displayedCapture = null;
        displayedResult = null;
        displayedMode = selectedScanMode();
        reviewedCandidateAreas.clear();
        rescanButton.setEnabled(false);
        mappedBuildingsToggleButton.setEnabled(false);
        summary.setText("No task reconnaissance calculated.");
    }

    private void showError(String message) {
        state.setForeground(new Color(170, 35, 35));
        state.setText(message);
    }

    private static JLabel wrappingLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:300px'>" + escapeHtml(text)
                + "</div></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private GeometryEditControls createGeometryEditControls() {
        GeometryEditControls controls = new GeometryEditControls();
        controls.panel.setLayout(new BoxLayout(controls.panel, BoxLayout.Y_AXIS));
        controls.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel prompt = new JLabel(tr("Geometry changed after review:"));
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.panel.add(prompt);
        JPanel choices = new JPanel();
        choices.setLayout(new BoxLayout(choices, BoxLayout.X_AXIS));
        choices.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.moved = correctionBox(tr("Moved"));
        controls.rotated = correctionBox(tr("Rotated"));
        controls.reshaped = correctionBox(tr("Shape"));
        controls.resized = correctionBox(tr("Size"));
        choices.add(controls.moved);
        choices.add(controls.rotated);
        choices.add(controls.reshaped);
        choices.add(controls.resized);
        controls.panel.add(choices);
        controls.savedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.panel.add(controls.savedLabel);
        return controls;
    }

    private static JCheckBox correctionBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setFocusable(false);
        return box;
    }

    private void saveGeometryEdits(GeometryEditControls controls) {
        EnumSet<GeometryEditOutcome> current = controls.selection();
        GeometryMeasurement measurement = controls.measurement();
        if (!current.isEmpty() && measurement == null) {
            controls.savedLabel.setForeground(new Color(175, 40, 35));
            controls.savedLabel.setText(tr("Finish the geometry edit before recording it"));
            return;
        }
        String imagery = context == null ? "" : context.getAuthorisedImagery();
        learningStore.replaceGeometryEdits(reference, imagery,
                controls.recorded, controls.recordedMeasurement, current, measurement);
        sharedLearningStore.updateEdits(controls.sharedEventId, current);
        controls.recorded = EnumSet.copyOf(current);
        controls.recordedMeasurement = measurement;
        controls.savedLabel.setForeground(new Color(0, 105, 45));
        controls.savedLabel.setText(current.isEmpty()
                ? tr("No geometry correction recorded")
                : tr("Geometry correction saved locally"));
        learningChanged.run();
    }

    private void clearGeometryEdits(GeometryEditControls controls) {
        String imagery = context == null ? "" : context.getAuthorisedImagery();
        learningStore.replaceGeometryEdits(reference, imagery,
                controls.recorded, controls.recordedMeasurement,
                GeometryEditOutcome.none(), null);
        sharedLearningStore.updateEdits(controls.sharedEventId, GeometryEditOutcome.none());
        controls.recorded = GeometryEditOutcome.none();
        controls.recordedMeasurement = null;
        controls.updating = true;
        controls.moved.setSelected(false);
        controls.rotated.setSelected(false);
        controls.reshaped.setSelected(false);
        controls.resized.setSelected(false);
        controls.updating = false;
        controls.savedLabel.setText(" ");
        learningChanged.run();
    }

    private String queueShared(BuildingCandidateScanner.Evidence evidence, boolean building,
            BuildingCandidateScanner.Shape shape) {
        String imagery = context == null ? "" : context.getAuthorisedImagery();
        return sharedLearningStore.queue(reference, evidence, building, shape, imagery);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class Inventory {
        private int rectangular;
        private int round;
        private int other;
    }

    private enum ScanScope {
        ENTIRE_TASK,
        VISIBLE_AREA
    }

    private static final class TaskCapture {
        private final BufferedImage image;
        private final Polygon boundary;
        private final List<Polygon> mappedPolygons;
        private final Rectangle crop;
        private final Inventory inventory;
        private final ProjectionBounds taskBounds;
        private final ProjectionBounds scanBounds;
        private final Polygon mapBoundary;
        private final Set<Way> initialBuildingWays;
        private final List<Way> mappedWays;
        private final ScanScope scope;
        private final double metresPerPixel;

        TaskCapture(BufferedImage image, Polygon boundary, List<Polygon> mappedPolygons,
                Rectangle crop, Inventory inventory, ProjectionBounds taskBounds,
                ProjectionBounds scanBounds, Polygon mapBoundary,
                List<Way> initialBuildingWays, ScanScope scope,
                double metresPerPixel) {
            this.image = image;
            this.boundary = boundary;
            this.mappedPolygons = mappedPolygons;
            this.crop = crop;
            this.inventory = inventory;
            this.taskBounds = taskBounds;
            this.scanBounds = scanBounds;
            this.mapBoundary = mapBoundary;
            this.initialBuildingWays = new HashSet<>(initialBuildingWays);
            this.mappedWays = new ArrayList<>(initialBuildingWays);
            this.scope = scope;
            this.metresPerPixel = metresPerPixel;
        }
    }

    private static final class CandidateReviewItem {
        private final int candidateNumber;
        private final BuildingCandidateScanner.Shape shape;
        private final ProjectionBounds candidateArea;
        private final ProjectionBounds reviewArea;
        private final BuildingCandidateScanner.Evidence evidence;
        private final JPanel row;
        private final JLabel decisionLabel;
        private final JPanel decisionButtons;
        private final JButton accept;
        private final JButton reject;
        private final JButton map;
        private final JButton checkMapped;
        private final JButton restore;
        private final GeometryEditControls geometryEdits;
        private boolean reviewed;
        private boolean positiveLearned;
        private boolean negativeLearned;
        private String sharedEventId;

        CandidateReviewItem(int candidateNumber, BuildingCandidateScanner.Shape shape,
                ProjectionBounds candidateArea, ProjectionBounds reviewArea,
                BuildingCandidateScanner.Evidence evidence, JPanel row,
                JLabel decisionLabel, JPanel decisionButtons, JButton accept, JButton reject,
                JButton map, JButton checkMapped, JButton restore,
                GeometryEditControls geometryEdits) {
            this.candidateNumber = candidateNumber;
            this.shape = shape;
            this.candidateArea = candidateArea;
            this.reviewArea = reviewArea;
            this.evidence = evidence;
            this.row = row;
            this.decisionLabel = decisionLabel;
            this.decisionButtons = decisionButtons;
            this.accept = accept;
            this.reject = reject;
            this.map = map;
            this.checkMapped = checkMapped;
            this.restore = restore;
            this.geometryEdits = geometryEdits;
        }
    }

    private enum MappedReviewDecision {
        UNREVIEWED,
        CONFIRMED_BUILDING,
        NOT_A_BUILDING
    }

    private static final class MappedReviewItem {
        private final int number;
        private final BuildingCandidateScanner.Shape shape;
        private final BuildingCandidateScanner.Evidence evidence;
        private final JPanel row;
        private final JLabel decisionLabel;
        private final JPanel decisionButtons;
        private final JButton confirm;
        private final JButton notBuilding;
        private final JButton restore;
        private final GeometryEditControls geometryEdits;
        private MappedReviewDecision decision = MappedReviewDecision.UNREVIEWED;
        private boolean reviewed;
        private boolean learningRecorded;
        private String sharedEventId;

        MappedReviewItem(int number, BuildingCandidateScanner.Shape shape,
                BuildingCandidateScanner.Evidence evidence, JPanel row, JLabel decisionLabel,
                JPanel decisionButtons, JButton confirm,
                JButton notBuilding, JButton restore, GeometryEditControls geometryEdits) {
            this.number = number;
            this.shape = shape;
            this.evidence = evidence;
            this.row = row;
            this.decisionLabel = decisionLabel;
            this.decisionButtons = decisionButtons;
            this.confirm = confirm;
            this.notBuilding = notBuilding;
            this.restore = restore;
            this.geometryEdits = geometryEdits;
        }
    }

    private static final class GeometryEditControls {
        private final JPanel panel = new JPanel();
        private final JLabel savedLabel = new JLabel(" ");
        private JCheckBox moved;
        private JCheckBox rotated;
        private JCheckBox reshaped;
        private JCheckBox resized;
        private EnumSet<GeometryEditOutcome> recorded = GeometryEditOutcome.none();
        private GeometryMeasurement measurement;
        private Supplier<GeometryMeasurement> measurementProvider;
        private GeometryMeasurement recordedMeasurement;
        private String sharedEventId;
        private boolean updating;

        void setSaveAction(Runnable action) {
            moved.addActionListener(event -> run(action));
            rotated.addActionListener(event -> run(action));
            reshaped.addActionListener(event -> run(action));
            resized.addActionListener(event -> run(action));
        }

        private void run(Runnable action) {
            if (!updating) {
                action.run();
            }
        }

        EnumSet<GeometryEditOutcome> selection() {
            EnumSet<GeometryEditOutcome> result = GeometryEditOutcome.none();
            if (moved.isSelected()) result.add(GeometryEditOutcome.MOVED);
            if (rotated.isSelected()) result.add(GeometryEditOutcome.ROTATED);
            if (reshaped.isSelected()) result.add(GeometryEditOutcome.RESHAPED);
            if (resized.isSelected()) result.add(GeometryEditOutcome.RESIZED);
            return result;
        }

        GeometryMeasurement measurement() {
            return measurementProvider == null ? measurement : measurementProvider.get();
        }
    }

    private static final class MappedBuildingConcern {
        private final BuildingCandidateScanner.Shape shape;
        private final Rectangle bounds;
        private final int score;
        private final BuildingCandidateScanner.Evidence evidence;
        private final Way way;
        private final GeometryMeasurement.WaySnapshot originalGeometry;

        MappedBuildingConcern(BuildingCandidateScanner.Shape shape, Rectangle bounds,
                int score, BuildingCandidateScanner.Evidence evidence, Way way,
                GeometryMeasurement.WaySnapshot originalGeometry) {
            this.shape = shape;
            this.bounds = new Rectangle(bounds);
            this.score = score;
            this.evidence = evidence;
            this.way = way;
            this.originalGeometry = originalGeometry;
        }
    }

    private static final class ScanResult {
        private final BuildingCandidateScanner.Result candidates;
        private final List<MappedBuildingConcern> mappedToReview;

        ScanResult(BuildingCandidateScanner.Result candidates,
                List<MappedBuildingConcern> mappedToReview) {
            this.candidates = candidates;
            this.mappedToReview = mappedToReview;
        }
    }

    private static final class ViewportAnchor {
        private final JViewport viewport;
        private final Component component;
        private final int screenY;

        ViewportAnchor(JViewport viewport, Component component, int screenY) {
            this.viewport = viewport;
            this.component = component;
            this.screenY = screenY;
        }
    }

    private static final class CandidateHighlight implements MapViewPaintable {
        private final String label;
        private final BuildingCandidateScanner.Shape shape;
        private final ProjectionBounds bounds;

        CandidateHighlight(int candidateNumber, BuildingCandidateScanner.Shape shape,
                ProjectionBounds bounds) {
            this("Candidate " + candidateNumber, shape, bounds);
        }

        CandidateHighlight(String label, BuildingCandidateScanner.Shape shape,
                ProjectionBounds bounds) {
            this.label = label;
            this.shape = shape;
            this.bounds = bounds;
        }

        @Override
        public void paint(Graphics2D graphics, MapView mapView, Bounds visibleBounds) {
            Point first = mapView.getPoint(bounds.getMin());
            Point second = mapView.getPoint(bounds.getMax());
            int x = Math.min(first.x, second.x);
            int y = Math.min(first.y, second.y);
            int width = Math.max(8, Math.abs(first.x - second.x));
            int height = Math.max(8, Math.abs(first.y - second.y));

            Graphics2D marked = (Graphics2D) graphics.create();
            try {
                marked.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                marked.setColor(new Color(255, 55, 25, 55));
                if (shape == BuildingCandidateScanner.Shape.ROUND) {
                    marked.fillOval(x, y, width, height);
                } else {
                    marked.fillRect(x, y, width, height);
                }
                marked.setStroke(new BasicStroke(4.0f));
                marked.setColor(new Color(255, 45, 20));
                if (shape == BuildingCandidateScanner.Shape.ROUND) {
                    marked.drawOval(x, y, width, height);
                } else {
                    marked.drawRect(x, y, width, height);
                }

                FontMetrics metrics = marked.getFontMetrics();
                int labelX = x;
                int labelY = Math.max(metrics.getAscent() + 5, y - 7);
                marked.setColor(new Color(115, 20, 10, 220));
                marked.fillRoundRect(labelX - 4, labelY - metrics.getAscent() - 3,
                        metrics.stringWidth(label) + 8, metrics.getHeight() + 4, 6, 6);
                marked.setColor(Color.WHITE);
                marked.drawString(label, labelX, labelY);
            } finally {
                marked.dispose();
            }
        }
    }
}
