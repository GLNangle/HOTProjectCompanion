package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local, explainable image measurements for a selected possible building. */
final class BuildingImageAnalyser {
    private static final double CONSISTENCY_WEIGHT = 0.10;
    private static final double CONTRAST_WEIGHT = 0.20;
    private static final double BOUNDARY_WEIGHT = 0.20;
    private static final double SHADOW_WEIGHT = 0.30;
    private static final double REFERENCE_WEIGHT = 0.20;

    private BuildingImageAnalyser() {
    }

    static Result analyse(BufferedImage image, Polygon outline, List<ReferenceImage> references) {
        if (image == null || outline == null || outline.npoints < 3) {
            throw new IllegalArgumentException("A captured image and complete outline are required");
        }
        boolean[] mask = createMask(image.getWidth(), image.getHeight(), outline);
        PixelFeatures inside = features(image, mask, true, outline.getBounds());
        PixelFeatures outside = surroundingFeatures(image, mask, outline.getBounds());
        if (inside.count < 30 || outside.count < 30) {
            throw new IllegalArgumentException("The selected outline is too small in the current view. Zoom in and try again.");
        }

        double shape = shapeScore(outline);
        double consistency = consistencyScore(inside);
        double contrast = clamp(colourDistance(inside, outside) / 82.0);
        double boundary = boundaryScore(image, mask, outline);
        double shadow = shadowScore(image, mask, outline, inside);
        ReferenceComparison comparison = compareReferences(inside, references);

        double weightedBase = consistency * CONSISTENCY_WEIGHT + contrast * CONTRAST_WEIGHT
                + boundary * BOUNDARY_WEIGHT + shadow * SHADOW_WEIGHT;
        double total = comparison.hasLabelledEvidence
                ? weightedBase + comparison.evidenceScore * REFERENCE_WEIGHT
                : weightedBase / (1.0 - REFERENCE_WEIGHT);
        int score = (int) Math.round(clamp(total) * 100.0);
        String band = score >= 70 ? "Likely building" : score >= 45 ? "Uncertain" : "Unlikely building";

        List<String> supporting = new ArrayList<>();
        List<String> cautions = new ArrayList<>();
        if (shape <= 0.38) {
            cautions.add("the mapped outline geometry itself may need review");
        }
        addReason(consistency, 0.66, 0.38, "the interior has a fairly consistent roof-like appearance",
                "the interior is visually irregular or heavily textured", supporting, cautions);
        addReason(contrast, 0.58, 0.28, "the possible roof differs from its surroundings",
                "the possible roof has little visual contrast with its surroundings", supporting, cautions);
        addReason(boundary, 0.58, 0.28, "a visible image boundary follows much of the outline",
                "the imagery does not show a strong boundary along the outline", supporting, cautions);
        addReason(shadow, 0.55, 0.22, "a directional dark band provides a possible structure shadow cue",
                "no clear directional shadow cue is visible beside the outline", supporting, cautions);
        if (comparison.hasLabelledEvidence) {
            addReason(comparison.evidenceScore, 0.62, 0.38,
                    "its visual pattern resembles a project building example",
                    "it resembles a project non-building example more closely", supporting, cautions);
        } else if (references == null || references.isEmpty()) {
            cautions.add("the task has no usable example images for automatic comparison");
        } else {
            cautions.add("the task images are not clearly labelled as building or non-building examples");
        }

        return new Result(score, band, percent(shape), percent(consistency), percent(contrast),
                percent(boundary), percent(shadow),
                comparison.hasLabelledEvidence ? percent(comparison.evidenceScore) : -1,
                supporting, cautions, comparison.closestDescription, comparison.comparedCount,
                comparison.labelledCount);
    }

    private static boolean[] createMask(int width, int height, Polygon outline) {
        boolean[] mask = new boolean[width * height];
        Rectangle bounds = outline.getBounds().intersection(new Rectangle(0, 0, width, height));
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                mask[y * width + x] = outline.contains(x + 0.5, y + 0.5);
            }
        }
        return mask;
    }

    private static PixelFeatures surroundingFeatures(BufferedImage image, boolean[] mask, Rectangle outline) {
        int padding = Math.max(8, Math.min(40, Math.max(outline.width, outline.height) / 3));
        Rectangle region = new Rectangle(outline.x - padding, outline.y - padding,
                outline.width + padding * 2, outline.height + padding * 2)
                .intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        return features(image, mask, false, region);
    }

    private static PixelFeatures features(BufferedImage image, boolean[] mask, boolean wantedMask, Rectangle region) {
        PixelFeatures result = new PixelFeatures();
        Rectangle clipped = region.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        int targetSamples = 100_000;
        int step = Math.max(1, (int) Math.sqrt(Math.max(1,
                (clipped.width * (long) clipped.height) / targetSamples)));
        for (int y = clipped.y; y < clipped.y + clipped.height; y += step) {
            for (int x = clipped.x; x < clipped.x + clipped.width; x += step) {
                if (mask != null && mask[y * image.getWidth() + x] != wantedMask) {
                    continue;
                }
                result.add(image.getRGB(x, y));
                if (x + step < clipped.x + clipped.width && y + step < clipped.y + clipped.height
                        && (mask == null || (mask[y * image.getWidth() + x + step] == wantedMask
                        && mask[(y + step) * image.getWidth() + x] == wantedMask))) {
                    result.addGradient(image.getRGB(x + step, y), image.getRGB(x, y));
                    result.addGradient(image.getRGB(x, y + step), image.getRGB(x, y));
                }
            }
        }
        result.finish();
        return result;
    }

    private static double shapeScore(Polygon outline) {
        Rectangle bounds = outline.getBounds();
        if (bounds.width < 1 || bounds.height < 1) {
            return 0;
        }
        double areaTwice = 0;
        double perimeter = 0;
        for (int index = 0; index < outline.npoints; index++) {
            int next = (index + 1) % outline.npoints;
            areaTwice += outline.xpoints[index] * (double) outline.ypoints[next]
                    - outline.xpoints[next] * (double) outline.ypoints[index];
            perimeter += Math.hypot(outline.xpoints[next] - outline.xpoints[index],
                    outline.ypoints[next] - outline.ypoints[index]);
        }
        double area = Math.abs(areaTwice) / 2.0;
        double rectangularity = area / (bounds.width * (double) bounds.height);
        double compactness = perimeter == 0 ? 0 : 4 * Math.PI * area / (perimeter * perimeter);
        int vertices = outline.npoints;
        double vertexScore = vertices >= 4 && vertices <= 16 ? 1.0
                : vertices == 3 || vertices <= 24 ? 0.62 : 0.32;
        return clamp(clamp((rectangularity - 0.25) / 0.65) * 0.50
                + clamp((compactness - 0.18) / 0.68) * 0.25 + vertexScore * 0.25);
    }

    private static double consistencyScore(PixelFeatures inside) {
        double colourUniformity = clamp(1.0 - (inside.colourStdDev - 10.0) / 78.0);
        double textureSuitability = clamp(1.0 - Math.max(0, inside.edgeDensity - 0.16) / 0.42);
        return colourUniformity * 0.72 + textureSuitability * 0.28;
    }

    private static double boundaryScore(BufferedImage image, boolean[] mask, Polygon outline) {
        int width = image.getWidth();
        int height = image.getHeight();
        Rectangle bounds = outline.getBounds().intersection(new Rectangle(1, 1, width - 2, height - 2));
        double centreX = bounds.getCenterX();
        double centreY = bounds.getCenterY();
        double totalDifference = 0;
        int samples = 0;
        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};
        for (int y = bounds.y; y < bounds.y + bounds.height; y += 2) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x += 2) {
                if (!mask[y * width + x]) {
                    continue;
                }
                boolean boundaryPixel = false;
                for (int index = 0; index < dx.length; index++) {
                    if (!mask[(y + dy[index]) * width + x + dx[index]]) {
                        boundaryPixel = true;
                        break;
                    }
                }
                if (!boundaryPixel) {
                    continue;
                }
                double vx = x - centreX;
                double vy = y - centreY;
                double length = Math.max(1, Math.hypot(vx, vy));
                int innerX = clampCoordinate((int) Math.round(x - vx / length * 4), width);
                int innerY = clampCoordinate((int) Math.round(y - vy / length * 4), height);
                int outerX = clampCoordinate((int) Math.round(x + vx / length * 4), width);
                int outerY = clampCoordinate((int) Math.round(y + vy / length * 4), height);
                if (mask[innerY * width + innerX] && !mask[outerY * width + outerX]) {
                    totalDifference += rgbDistance(image.getRGB(innerX, innerY), image.getRGB(outerX, outerY));
                    samples++;
                }
            }
        }
        return samples < 8 ? 0.35 : clamp((totalDifference / samples) / 105.0);
    }

    private static double shadowScore(BufferedImage image, boolean[] mask, Polygon outline,
            PixelFeatures inside) {
        int width = image.getWidth();
        int height = image.getHeight();
        Rectangle bounds = outline.getBounds();
        int padding = Math.max(10, Math.min(36, Math.max(bounds.width, bounds.height) / 3));
        Rectangle region = new Rectangle(bounds.x - padding, bounds.y - padding,
                bounds.width + padding * 2, bounds.height + padding * 2)
                .intersection(new Rectangle(0, 0, width, height));
        double centreX = bounds.getCenterX();
        double centreY = bounds.getCenterY();
        double[] sums = new double[8];
        int[] counts = new int[8];
        int step = Math.max(1, Math.max(region.width, region.height) / 180);
        for (int y = region.y; y < region.y + region.height; y += step) {
            for (int x = region.x; x < region.x + region.width; x += step) {
                if (mask[y * width + x]) {
                    continue;
                }
                double angle = Math.atan2(y - centreY, x - centreX);
                int sector = (int) Math.floor((angle + Math.PI) / (Math.PI / 4.0));
                sector = Math.max(0, Math.min(7, sector));
                sums[sector] += luminance(image.getRGB(x, y));
                counts[sector]++;
            }
        }
        double[] means = new double[8];
        double[] valid = new double[8];
        int validCount = 0;
        int darkestSector = -1;
        double darkest = Double.MAX_VALUE;
        for (int sector = 0; sector < 8; sector++) {
            if (counts[sector] < 8) {
                means[sector] = Double.NaN;
                continue;
            }
            means[sector] = sums[sector] / counts[sector];
            valid[validCount++] = means[sector];
            if (means[sector] < darkest) {
                darkest = means[sector];
                darkestSector = sector;
            }
        }
        if (validCount < 5 || darkestSector < 0) {
            return 0;
        }
        Arrays.sort(valid, 0, validCount);
        double median = valid[validCount / 2];
        double previous = means[(darkestSector + 7) % 8];
        double next = means[(darkestSector + 1) % 8];
        double adjacent = Double.isNaN(previous) ? next : Double.isNaN(next) ? previous : Math.min(previous, next);
        if (Double.isNaN(adjacent)) {
            adjacent = darkest;
        }
        double darkBand = (darkest + adjacent) / 2.0;
        double directionalDrop = Math.max(0, median - darkBand);
        double roofDrop = Math.max(0, inside.meanLuminance - darkBand);
        double cue = directionalDrop * 0.75 + roofDrop * 0.25;
        return clamp((cue - 6.0) / 34.0);
    }

    private static ReferenceComparison compareReferences(PixelFeatures candidate,
            List<ReferenceImage> references) {
        if (references == null || references.isEmpty()) {
            return ReferenceComparison.empty();
        }
        double bestPositive = -1;
        double bestNegative = -1;
        double closest = -1;
        String closestDescription = "";
        int compared = 0;
        int labelled = 0;
        for (ReferenceImage reference : references) {
            if (reference == null || reference.image == null) {
                continue;
            }
            double similarity = bestPatchSimilarity(candidate, reference.image);
            compared++;
            if (similarity > closest) {
                closest = similarity;
                closestDescription = reference.description;
            }
            Polarity polarity = polarity(reference.description);
            if (polarity == Polarity.POSITIVE) {
                bestPositive = Math.max(bestPositive, similarity);
                labelled++;
            } else if (polarity == Polarity.NEGATIVE) {
                bestNegative = Math.max(bestNegative, similarity);
                labelled++;
            }
        }
        if (labelled == 0) {
            return new ReferenceComparison(false, 0.5, closestDescription, compared, 0);
        }
        double evidence;
        if (bestPositive >= 0 && bestNegative >= 0) {
            evidence = clamp(0.5 + (bestPositive - bestNegative) * 1.25);
        } else if (bestPositive >= 0) {
            evidence = bestPositive;
        } else {
            evidence = 1.0 - bestNegative;
        }
        return new ReferenceComparison(true, evidence, closestDescription, compared, labelled);
    }

    private static double bestPatchSimilarity(PixelFeatures candidate, BufferedImage reference) {
        int width = reference.getWidth();
        int height = reference.getHeight();
        List<Rectangle> regions = new ArrayList<>();
        regions.add(new Rectangle(0, 0, width, height));
        regions.add(relativeRegion(width, height, 0.20, 0.20, 0.60, 0.60));
        for (double centreY : new double[] {0.25, 0.50, 0.75}) {
            for (double centreX : new double[] {0.25, 0.50, 0.75}) {
                regions.add(relativeRegion(width, height, centreX - 0.24, centreY - 0.24, 0.48, 0.48));
            }
        }
        double best = 0;
        for (Rectangle region : regions) {
            PixelFeatures patch = features(reference, null, true, region);
            if (patch.count >= 20) {
                best = Math.max(best, featureSimilarity(candidate, patch));
            }
        }
        return best;
    }

    private static Rectangle relativeRegion(int width, int height, double x, double y, double w, double h) {
        int left = Math.max(0, (int) Math.round(width * x));
        int top = Math.max(0, (int) Math.round(height * y));
        int right = Math.min(width, (int) Math.round(width * (x + w)));
        int bottom = Math.min(height, (int) Math.round(height * (y + h)));
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private static double featureSimilarity(PixelFeatures first, PixelFeatures second) {
        double histogram = 0;
        for (int index = 0; index < first.histogram.length; index++) {
            histogram += Math.min(first.histogram[index], second.histogram[index]);
        }
        double luminance = 1.0 - Math.min(1.0, Math.abs(first.meanLuminance - second.meanLuminance) / 180.0);
        double variation = 1.0 - Math.min(1.0, Math.abs(first.colourStdDev - second.colourStdDev) / 90.0);
        double edges = 1.0 - Math.min(1.0, Math.abs(first.edgeDensity - second.edgeDensity) / 0.50);
        return clamp(histogram * 0.55 + luminance * 0.15 + variation * 0.15 + edges * 0.15);
    }

    private static Polarity polarity(String description) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (text.contains("not a building") || text.contains("non-building")
                || text.contains("do not map") || text.contains("don't map")
                || text.contains("false positive") || text.contains("tree") || text.contains("rock")
                || text.contains("vehicle") || text.contains("ground patch")) {
            return Polarity.NEGATIVE;
        }
        if (text.contains("building") || text.contains("roof") || text.contains("house")
                || text.contains("hut") || text.contains("structure")) {
            return Polarity.POSITIVE;
        }
        return Polarity.NEUTRAL;
    }

    private static double colourDistance(PixelFeatures first, PixelFeatures second) {
        return Math.sqrt(square(first.meanRed - second.meanRed) + square(first.meanGreen - second.meanGreen)
                + square(first.meanBlue - second.meanBlue));
    }

    private static double rgbDistance(int first, int second) {
        int red = ((first >> 16) & 0xff) - ((second >> 16) & 0xff);
        int green = ((first >> 8) & 0xff) - ((second >> 8) & 0xff);
        int blue = (first & 0xff) - (second & 0xff);
        return Math.sqrt(red * (double) red + green * (double) green + blue * (double) blue);
    }

    private static double luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private static void addReason(double value, double high, double low, String positive, String negative,
            List<String> supporting, List<String> cautions) {
        if (value >= high) {
            supporting.add(positive);
        } else if (value <= low) {
            cautions.add(negative);
        }
    }

    private static int percent(double value) {
        return (int) Math.round(clamp(value) * 100.0);
    }

    private static int clampCoordinate(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    static final class ReferenceImage {
        private final BufferedImage image;
        private final String description;

        ReferenceImage(BufferedImage image, String description) {
            this.image = image;
            this.description = description == null ? "" : description.trim();
        }
    }

    static final class Result {
        private final int score;
        private final String band;
        private final int shapeScore;
        private final int consistencyScore;
        private final int contrastScore;
        private final int boundaryScore;
        private final int shadowScore;
        private final int referenceScore;
        private final List<String> supporting;
        private final List<String> cautions;
        private final String closestDescription;
        private final int comparedReferences;
        private final int labelledReferences;

        Result(int score, String band, int shapeScore, int consistencyScore, int contrastScore,
                int boundaryScore, int shadowScore, int referenceScore, List<String> supporting,
                List<String> cautions, String closestDescription, int comparedReferences,
                int labelledReferences) {
            this.score = score;
            this.band = band;
            this.shapeScore = shapeScore;
            this.consistencyScore = consistencyScore;
            this.contrastScore = contrastScore;
            this.boundaryScore = boundaryScore;
            this.shadowScore = shadowScore;
            this.referenceScore = referenceScore;
            this.supporting = Collections.unmodifiableList(new ArrayList<>(supporting));
            this.cautions = Collections.unmodifiableList(new ArrayList<>(cautions));
            this.closestDescription = closestDescription == null ? "" : closestDescription;
            this.comparedReferences = comparedReferences;
            this.labelledReferences = labelledReferences;
        }

        int getScore() { return score; }
        String getBand() { return band; }
        int getShapeScore() { return shapeScore; }
        int getConsistencyScore() { return consistencyScore; }
        int getContrastScore() { return contrastScore; }
        int getBoundaryScore() { return boundaryScore; }
        int getShadowScore() { return shadowScore; }
        int getReferenceScore() { return referenceScore; }
        List<String> getSupporting() { return supporting; }
        List<String> getCautions() { return cautions; }
        String getClosestDescription() { return closestDescription; }
        int getComparedReferences() { return comparedReferences; }
        int getLabelledReferences() { return labelledReferences; }
    }

    private static final class PixelFeatures {
        private final double[] histogram = new double[64];
        private long count;
        private double red;
        private double green;
        private double blue;
        private double luminance;
        private double colourSquares;
        private long gradients;
        private long strongGradients;
        private double meanRed;
        private double meanGreen;
        private double meanBlue;
        private double meanLuminance;
        private double colourStdDev;
        private double edgeDensity;

        void add(int rgb) {
            int r = (rgb >> 16) & 0xff;
            int g = (rgb >> 8) & 0xff;
            int b = rgb & 0xff;
            double lum = r * 0.2126 + g * 0.7152 + b * 0.0722;
            count++;
            red += r;
            green += g;
            blue += b;
            luminance += lum;
            colourSquares += r * (double) r + g * (double) g + b * (double) b;
            histogram[(r / 64) * 16 + (g / 64) * 4 + b / 64]++;
        }

        void addGradient(int first, int second) {
            gradients++;
            if (rgbDistance(first, second) > 48) {
                strongGradients++;
            }
        }

        void finish() {
            if (count == 0) {
                return;
            }
            meanRed = red / count;
            meanGreen = green / count;
            meanBlue = blue / count;
            meanLuminance = luminance / count;
            double meanSquares = colourSquares / (count * 3.0);
            double squareMean = (meanRed * meanRed + meanGreen * meanGreen + meanBlue * meanBlue) / 3.0;
            colourStdDev = Math.sqrt(Math.max(0, meanSquares - squareMean));
            edgeDensity = gradients == 0 ? 0 : strongGradients / (double) gradients;
            for (int index = 0; index < histogram.length; index++) {
                histogram[index] /= count;
            }
        }
    }

    private static final class ReferenceComparison {
        private final boolean hasLabelledEvidence;
        private final double evidenceScore;
        private final String closestDescription;
        private final int comparedCount;
        private final int labelledCount;

        ReferenceComparison(boolean hasLabelledEvidence, double evidenceScore, String closestDescription,
                int comparedCount, int labelledCount) {
            this.hasLabelledEvidence = hasLabelledEvidence;
            this.evidenceScore = evidenceScore;
            this.closestDescription = closestDescription;
            this.comparedCount = comparedCount;
            this.labelledCount = labelledCount;
        }

        static ReferenceComparison empty() {
            return new ReferenceComparison(false, 0.5, "", 0, 0);
        }
    }

    private enum Polarity { POSITIVE, NEGATIVE, NEUTRAL }
}
