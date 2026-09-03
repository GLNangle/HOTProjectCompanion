package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;

public final class BuildingCandidateScannerTest {
    private BuildingCandidateScannerTest() {
    }

    public static void main(String[] args) {
        detectsRectangularAndRoundRoofCandidates();
        detectsLShapedRoofCandidate();
        exploratoryDetectsUnequalRectangularLShape();
        exploratoryFindsClearElongatedRoof();
        excludesAlreadyMappedCandidate();
        doesNotInventCandidatesOnFeaturelessImagery();
        rejectsUnshadowedGroundPatch();
        rejectsShadowedTexturedGroundPatch();
        rejectsRoadBandWithDarkEdge();
        rejectsVegetationPatch();
        exploratoryRetainsOnlyStrongGreenRoofEvidence();
        assessesMappedOutlinesForReview();
        safelyAssessesOutlinesTouchingImageEdges();
        usesScaleAwareCandidateSizes();
        appliesSensitivityModesPredictably();
        describesSensitivityModesClearly();
        excludesPreviouslyReviewedLocations();
        classifiesMappedFootprints();
        System.out.println("BuildingCandidateScannerTest: all tests passed");
    }

    private static void usesScaleAwareCandidateSizes() {
        int[] closeZoom = BuildingCandidateScanner.candidateSizes(0.10, 900);
        require(closeZoom[0] >= 25,
                "high zoom does not search for physically implausible micro-buildings");
        require(closeZoom[closeZoom.length - 1] >= 160,
                "high zoom retains larger plausible building templates");
        int[] normalZoom = BuildingCandidateScanner.candidateSizes(0.50, 900);
        require(normalZoom[0] == 12,
                "normal zoom retains small rural building templates");
    }

    private static void appliesSensitivityModesPredictably() {
        BufferedImage image = scene();
        Polygon boundary = rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4);
        BuildingCandidateScanner.Result conservative = BuildingCandidateScanner.scan(image,
                boundary, Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.BALANCED, Collections.emptyList());
        BuildingCandidateScanner.Result balanced = BuildingCandidateScanner.scan(image,
                boundary, Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.BALANCED, Collections.emptyList());
        BuildingCandidateScanner.Result exploratory = BuildingCandidateScanner.scan(image,
                boundary, Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.EXPLORATORY, Collections.emptyList());
        require(!conservative.getCandidates().isEmpty(),
                "conservative mode retains strong synthetic roof evidence");
        require(conservative.getCandidates().size() <= balanced.getCandidates().size(),
                "balanced mode is no stricter than conservative mode");
        require(balanced.getCandidates().size() <= exploratory.getCandidates().size(),
                "exploratory mode is no stricter than balanced mode");
        require(conservative.getCandidates().size() < exploratory.getCandidates().size(),
                "conservative and exploratory modes produce meaningfully different review lists");
        require(conservative.getCandidates().size() <= 8,
                "conservative mode uses a short review list");
        for (BuildingCandidateScanner.Candidate candidate : conservative.getCandidates()) {
            require(candidate.explanation() != null && !candidate.explanation().isEmpty(),
                    "retained candidates explain why they were shown");
        }
    }

    private static void describesSensitivityModesClearly() {
        require(BuildingCandidateScanner.ScanMode.BALANCED.toString().contains("Recommended"),
                "balanced mode is visibly recommended");
        require(TaskReconnaissancePanel.scanModeGuidance(
                BuildingCandidateScanner.ScanMode.CONSERVATIVE).contains("fewest"),
                "conservative guidance explains the shorter review list");
        String exploratory = TaskReconnaissancePanel.scanModeGuidance(
                BuildingCandidateScanner.ScanMode.EXPLORATORY);
        require(exploratory.contains("Expect more non-buildings"),
                "exploratory guidance warns about false detections");
        require(exploratory.contains("does not confirm"),
                "exploratory guidance says that highlights are not verdicts");
    }

    private static void excludesPreviouslyReviewedLocations() {
        BufferedImage image = scene();
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.BALANCED,
                Collections.singletonList(new Rectangle(0, 0,
                        image.getWidth(), image.getHeight())));
        require(result.getCandidates().isEmpty(),
                "reviewed regions are not proposed again during a rescan");
    }

    private static void detectsRectangularAndRoundRoofCandidates() {
        BufferedImage image = scene();
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList());
        require(result.count(BuildingCandidateScanner.Shape.RECTANGULAR, false) >= 1,
                "rectangular roof candidate detected");
        require(result.count(BuildingCandidateScanner.Shape.ROUND, false) >= 1,
                "round roof candidate detected");
    }

    private static void detectsLShapedRoofCandidate() {
        BufferedImage image = new BufferedImage(260, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(118, 126, 104));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(52, 55, 47));
            graphics.fillRect(54, 45, 36, 72);
            graphics.fillRect(54, 81, 72, 36);
            graphics.setColor(new Color(188, 174, 141));
            graphics.fillRect(48, 39, 36, 72);
            graphics.fillRect(48, 75, 72, 36);
        } finally {
            graphics.dispose();
        }
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.CONSERVATIVE, Collections.emptyList());
        require(result.count(BuildingCandidateScanner.Shape.L_SHAPED, false) >= 1,
                "connected perpendicular roof wings should produce an L-shaped candidate");
        BuildingCandidateScanner.Candidate candidate = result.getCandidates().stream()
                .filter(item -> item.getShape() == BuildingCandidateScanner.Shape.L_SHAPED)
                .findFirst().orElseThrow(() -> new AssertionError("missing L-shaped candidate"));
        require(candidate.getLCorner() == BuildingCandidateScanner.LCorner.TOP_RIGHT,
                "the candidate should preserve the missing-corner orientation");
    }

    private static void exploratoryDetectsUnequalRectangularLShape() {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(116, 124, 102));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(49, 53, 44));
            graphics.fillRect(71, 55, 34, 68);
            graphics.fillRect(71, 83, 90, 40);
            graphics.setColor(new Color(190, 176, 142));
            graphics.fillRect(64, 48, 34, 68);
            graphics.fillRect(64, 76, 90, 40);
        } finally {
            graphics.dispose();
        }
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.EXPLORATORY, Collections.emptyList());
        require(result.getCandidates().stream().anyMatch(candidate ->
                candidate.getShape() == BuildingCandidateScanner.Shape.L_SHAPED
                && candidate.getBounds().contains(105, 88)),
                "exploratory scan should find an elongated L with unequal wing depths");
    }

    private static void exploratoryFindsClearElongatedRoof() {
        BufferedImage image = new BufferedImage(300, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(121, 126, 105));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(49, 52, 45));
            graphics.fillRect(99, 80, 88, 53);
            graphics.setColor(new Color(190, 177, 143));
            graphics.fillRect(92, 73, 85, 50);
        } finally {
            graphics.dispose();
        }
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.EXPLORATORY, Collections.emptyList());
        require(result.getCandidates().stream().anyMatch(candidate ->
                candidate.getShape() == BuildingCandidateScanner.Shape.RECTANGULAR
                && candidate.getBounds().contains(134, 98)),
                "exploratory scan should find a clear mid-elongated roof");
    }

    private static void excludesAlreadyMappedCandidate() {
        BufferedImage image = scene();
        Polygon mappedRectangle = rectangle(36, 42, 48, 36);
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.singletonList(mappedRectangle));
        for (BuildingCandidateScanner.Candidate candidate : result.getCandidates()) {
            require(!mappedRectangle.contains(candidate.getBounds().getCenterX(),
                    candidate.getBounds().getCenterY()), "mapped candidate excluded");
        }
    }

    private static void doesNotInventCandidatesOnFeaturelessImagery() {
        BufferedImage image = new BufferedImage(220, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(120, 125, 110));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        for (BuildingCandidateScanner.ScanMode mode : BuildingCandidateScanner.ScanMode.values()) {
            BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                    rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                    Collections.emptyList(), null, null, Double.NaN, mode,
                    Collections.emptyList());
            require(result.getCandidates().isEmpty(),
                    "featureless imagery has no candidates in " + mode);
        }
    }

    private static void rejectsUnshadowedGroundPatch() {
        BufferedImage image = flatPatch(new Color(116, 112, 96),
                new Color(178, 166, 132), false);
        for (BuildingCandidateScanner.ScanMode mode : BuildingCandidateScanner.ScanMode.values()) {
            BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                    rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                    Collections.emptyList(), null, null, Double.NaN, mode,
                    Collections.emptyList());
            require(result.getCandidates().isEmpty(),
                    "an unshadowed bright ground patch is rejected in " + mode);
        }
    }

    private static void rejectsVegetationPatch() {
        BufferedImage image = flatPatch(new Color(126, 116, 92),
                new Color(62, 118, 58), true);
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList());
        require(result.getCandidates().isEmpty(),
                "a strongly green vegetation patch is rejected");
    }

    private static void exploratoryRetainsOnlyStrongGreenRoofEvidence() {
        BufferedImage image = new BufferedImage(280, 230, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(116, 126, 92));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(42, 58, 38));
            graphics.fillRect(92, 91, 70, 51);
            graphics.setColor(new Color(56, 174, 50));
            graphics.fillRect(84, 82, 68, 49);
        } finally {
            graphics.dispose();
        }
        Polygon boundary = rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4);
        BuildingCandidateScanner.Result balanced = BuildingCandidateScanner.scan(image,
                boundary, Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.BALANCED, Collections.emptyList());
        BuildingCandidateScanner.Result exploratory = BuildingCandidateScanner.scan(image,
                boundary, Collections.emptyList(), null, null, Double.NaN,
                BuildingCandidateScanner.ScanMode.EXPLORATORY, Collections.emptyList());
        require(balanced.getCandidates().isEmpty(),
                "balanced mode keeps the conservative vegetation safeguard");
        require(exploratory.getCandidates().stream().anyMatch(candidate ->
                candidate.getShape() == BuildingCandidateScanner.Shape.RECTANGULAR
                && candidate.getBounds().contains(116, 106)),
                "exploratory mode can retain a crisp green roof with continuous edges and shadow");
    }

    private static void rejectsShadowedTexturedGroundPatch() {
        BufferedImage image = flatPatch(new Color(116, 112, 96),
                new Color(178, 166, 132), false);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(64, 61, 50));
            graphics.fillRect(108, 122, 58, 10);
            for (int y = 82; y < 121; y += 4) {
                for (int x = 102; x < 157; x += 5) {
                    int shade = ((x + y) / 3) % 2 == 0 ? 115 : 225;
                    graphics.setColor(new Color(shade, shade - 7, shade - 18));
                    graphics.fillRect(x, y, 2, 2);
                }
            }
        } finally {
            graphics.dispose();
        }
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList());
        require(result.getCandidates().isEmpty(),
                "a textured bare patch with a dark side is rejected");
    }

    private static void rejectsRoadBandWithDarkEdge() {
        BufferedImage image = new BufferedImage(260, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(116, 112, 96));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(62, 59, 49));
            graphics.fillRect(0, 126, image.getWidth(), 9);
            graphics.setColor(new Color(182, 169, 137));
            graphics.fillRect(0, 88, image.getWidth(), 38);
        } finally {
            graphics.dispose();
        }
        for (BuildingCandidateScanner.ScanMode mode : BuildingCandidateScanner.ScanMode.values()) {
            BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                    rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                    Collections.emptyList(), null, null, Double.NaN, mode,
                    Collections.emptyList());
            require(result.getCandidates().isEmpty(),
                    "a road-like band with two long edges is rejected in " + mode);
        }
    }

    private static void assessesMappedOutlinesForReview() {
        BuildingCandidateScanner.Assessment roof = BuildingCandidateScanner.assess(scene(),
                BuildingCandidateScanner.Shape.RECTANGULAR,
                new java.awt.Rectangle(36, 42, 48, 36));
        BuildingCandidateScanner.Assessment ground = BuildingCandidateScanner.assess(
                flatPatch(new Color(116, 112, 96), new Color(178, 166, 132), false),
                BuildingCandidateScanner.Shape.RECTANGULAR,
                new java.awt.Rectangle(100, 80, 58, 42));
        require(roof != null && roof.getScore() >= 45,
                "roof-like mapped outline is not flagged as weak evidence");
        require(ground != null && ground.getScore() < 45,
                "unshadowed mapped patch is available for cautious review");
    }

    private static void safelyAssessesOutlinesTouchingImageEdges() {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(120, 115, 98));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(185, 172, 140));
            graphics.fillRect(0, 0, 18, 16);
            graphics.fillRect(image.getWidth() - 18, 0, 18, 16);
            graphics.fillRect(0, image.getHeight() - 16, 18, 16);
            graphics.fillRect(image.getWidth() - 18, image.getHeight() - 16, 18, 16);
        } finally {
            graphics.dispose();
        }
        java.awt.Rectangle[] edges = {
            new java.awt.Rectangle(0, 0, 18, 16),
            new java.awt.Rectangle(image.getWidth() - 18, 0, 18, 16),
            new java.awt.Rectangle(0, image.getHeight() - 16, 18, 16),
            new java.awt.Rectangle(image.getWidth() - 18,
                    image.getHeight() - 16, 18, 16)
        };
        for (java.awt.Rectangle edge : edges) {
            BuildingCandidateScanner.assess(image,
                    BuildingCandidateScanner.Shape.RECTANGULAR, edge);
            BuildingCandidateScanner.assess(image,
                    BuildingCandidateScanner.Shape.ROUND, edge);
        }
    }

    private static BufferedImage flatPatch(Color background, Color patch, boolean round) {
        BufferedImage image = new BufferedImage(260, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(patch);
            if (round) {
                graphics.fillOval(100, 75, 55, 55);
            } else {
                graphics.fillRect(100, 80, 58, 42);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void classifiesMappedFootprints() {
        Polygon square = rectangle(0, 0, 40, 30);
        require(BuildingShapeClassifier.classify(square, "yes", null)
                == BuildingShapeClassifier.Shape.RECTANGULAR, "square footprint classification");
        Polygon circle = new Polygon();
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI * 2 / 12;
            circle.addPoint(50 + (int) Math.round(Math.cos(angle) * 25),
                    50 + (int) Math.round(Math.sin(angle) * 25));
        }
        require(BuildingShapeClassifier.classify(circle, "yes", null)
                == BuildingShapeClassifier.Shape.ROUND, "round geometry classification");
        require(BuildingShapeClassifier.classify(square, "round", null)
                == BuildingShapeClassifier.Shape.ROUND, "round building tag classification");
    }

    private static BufferedImage scene() {
        BufferedImage image = new BufferedImage(260, 220, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(118, 126, 104));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(55, 58, 48));
            graphics.fillRect(43, 49, 51, 38);
            graphics.setColor(new Color(186, 174, 142));
            graphics.fillRect(36, 42, 48, 36);

            graphics.setColor(new Color(48, 52, 44));
            graphics.fillOval(159, 73, 52, 52);
            graphics.setColor(new Color(180, 165, 132));
            graphics.fillOval(151, 65, 50, 50);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Polygon rectangle(int x, int y, int width, int height) {
        return new Polygon(new int[] {x, x + width, x + width, x},
                new int[] {y, y, y + height, y + height}, 4);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
