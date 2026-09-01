package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Collections;

/** Dependency-free checks for automatic building image analysis and imagery-name safety. */
public final class BuildingImageAnalyserTest {
    private BuildingImageAnalyserTest() {
    }

    public static void main(String[] args) {
        scoresClearRoofEvidence();
        flagsWeakVisualEvidence();
        doesNotTreatMapperDrawnShapeAsBuildingEvidence();
        usesOnlyClearlyLabelledExamplesForScoring();
        matchesOnlyPlausibleAuthorisedImagery();
        System.out.println("BuildingImageAnalyserTest: all tests passed");
    }

    private static void scoresClearRoofEvidence() {
        BufferedImage image = new BufferedImage(140, 140, BufferedImage.TYPE_INT_RGB);
        fill(image, new Color(64, 126, 69));
        fillRectangle(image, 102, 42, 124, 112, new Color(35, 43, 31));
        fillRectangle(image, 38, 98, 112, 120, new Color(35, 43, 31));
        fillRectangle(image, 38, 42, 102, 98, new Color(188, 92, 67));
        Polygon outline = rectangle(38, 42, 102, 98);

        BufferedImage example = new BufferedImage(80, 70, BufferedImage.TYPE_INT_RGB);
        fill(example, new Color(188, 92, 67));
        BuildingImageAnalyser.ReferenceImage reference =
                new BuildingImageAnalyser.ReferenceImage(example, "Building roof example");
        BuildingImageAnalyser.Result result = BuildingImageAnalyser.analyse(
                image, outline, Collections.singletonList(reference));

        require(result.getScore() >= 70, "clear roof should be likely");
        require("Likely building".equals(result.getBand()), "likely band");
        require(result.getReferenceScore() >= 70, "labelled example contributes");
        require(result.getShadowScore() >= 35, "directional shadow contributes");
        require(result.getSupporting().size() >= 3, "clear evidence is explained");
    }

    private static void flagsWeakVisualEvidence() {
        BufferedImage image = new BufferedImage(140, 140, BufferedImage.TYPE_INT_RGB);
        fill(image, new Color(92, 119, 78));
        Polygon outline = new Polygon(new int[] {35, 105, 70}, new int[] {100, 100, 35}, 3);
        BuildingImageAnalyser.Result result = BuildingImageAnalyser.analyse(
                image, outline, Collections.emptyList());

        require(result.getScore() < 55, "featureless patch should not be likely");
        require(result.getShadowScore() < 20, "featureless patch has no shadow cue");
        require(!result.getCautions().isEmpty(), "weak evidence is explained");
        require(result.getReferenceScore() == -1, "missing examples are not invented");
    }

    private static void doesNotTreatMapperDrawnShapeAsBuildingEvidence() {
        BufferedImage image = new BufferedImage(140, 140, BufferedImage.TYPE_INT_RGB);
        fill(image, new Color(92, 119, 78));
        BuildingImageAnalyser.Result rectangle = BuildingImageAnalyser.analyse(
                image, rectangle(35, 35, 105, 105), Collections.emptyList());
        BuildingImageAnalyser.Result triangle = BuildingImageAnalyser.analyse(image,
                new Polygon(new int[] {35, 105, 70}, new int[] {105, 105, 35}, 3),
                Collections.emptyList());
        require(rectangle.getShapeScore() != triangle.getShapeScore(), "geometry diagnostic still changes");
        require(Math.abs(rectangle.getScore() - triangle.getScore()) <= 1,
                "mapper-drawn geometry does not change building score");
    }

    private static void usesOnlyClearlyLabelledExamplesForScoring() {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        fill(image, new Color(75, 130, 76));
        fillRectangle(image, 35, 35, 85, 85, new Color(170, 100, 70));
        BufferedImage neutral = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        fill(neutral, new Color(170, 100, 70));
        BuildingImageAnalyser.Result result = BuildingImageAnalyser.analyse(image,
                rectangle(35, 35, 85, 85), Collections.singletonList(
                        new BuildingImageAnalyser.ReferenceImage(neutral, "Example from the task instructions")));
        require(result.getComparedReferences() == 1, "neutral example is compared");
        require(result.getLabelledReferences() == 0, "neutral example is not assumed positive");
        require(result.getReferenceScore() == -1, "neutral example does not alter score");
    }

    private static void matchesOnlyPlausibleAuthorisedImagery() {
        require(BuildingCheckPanel.imageryMatches("Esri World Imagery", "Esri World Imagery"), "exact imagery name");
        require(BuildingCheckPanel.imageryMatches("EsriWorldImagery", "Esri World Imagery"), "HOT imagery identifier");
        require(BuildingCheckPanel.imageryMatches("Use Esri World Imagery", "Esri World Imagery"), "embedded imagery name");
        require(!BuildingCheckPanel.imageryMatches("Esri World Imagery", "Maxar Premium"), "different imagery rejected");
    }

    private static Polygon rectangle(int left, int top, int right, int bottom) {
        return new Polygon(new int[] {left, right, right, left},
                new int[] {top, top, bottom, bottom}, 4);
    }

    private static void fill(BufferedImage image, Color colour) {
        fillRectangle(image, 0, 0, image.getWidth(), image.getHeight(), colour);
    }

    private static void fillRectangle(BufferedImage image, int left, int top, int right, int bottom, Color colour) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                image.setRGB(x, y, colour.getRGB());
            }
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}
