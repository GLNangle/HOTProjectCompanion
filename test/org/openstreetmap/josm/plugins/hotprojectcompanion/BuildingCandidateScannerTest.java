package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Collections;

public final class BuildingCandidateScannerTest {
    private BuildingCandidateScannerTest() {
    }

    public static void main(String[] args) {
        detectsRectangularAndRoundRoofCandidates();
        excludesAlreadyMappedCandidate();
        doesNotInventCandidatesOnFeaturelessImagery();
        rejectsUnshadowedGroundPatch();
        rejectsVegetationPatch();
        assessesMappedOutlinesForReview();
        classifiesMappedFootprints();
        System.out.println("BuildingCandidateScannerTest: all tests passed");
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
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList());
        require(result.getCandidates().isEmpty(), "featureless imagery has no candidates");
    }

    private static void rejectsUnshadowedGroundPatch() {
        BufferedImage image = flatPatch(new Color(116, 112, 96),
                new Color(178, 166, 132), false);
        BuildingCandidateScanner.Result result = BuildingCandidateScanner.scan(image,
                rectangle(2, 2, image.getWidth() - 4, image.getHeight() - 4),
                Collections.emptyList());
        require(result.getCandidates().isEmpty(),
                "a bounded bright ground patch without a directional shadow is rejected");
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
