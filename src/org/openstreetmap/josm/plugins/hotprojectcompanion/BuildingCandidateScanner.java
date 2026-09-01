package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Conservative, dependency-free proposal scan for roof-like regions in a task image. */
final class BuildingCandidateScanner {
    private static final int HIGH_CONFIDENCE = 72;
    private static final int MIN_CONFIDENCE = 58;
    private static final int MIN_BASELINE_FOR_LEARNED_ADMISSION = 56;
    private static final int MAX_RESULTS = 16;

    private BuildingCandidateScanner() {
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings) {
        return scan(image, taskBoundary, mappedBuildings, null);
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile) {
        if (image == null || taskBoundary == null || taskBoundary.npoints < 3) {
            throw new IllegalArgumentException("A task image and boundary are required");
        }
        IntegralImage integral = new IntegralImage(image);
        List<Candidate> proposals = new ArrayList<>();
        int shortest = Math.min(image.getWidth(), image.getHeight());
        int[] sizes = {10, 14, 19, 26, 36, 50, 68};
        for (int size : sizes) {
            if (size > shortest / 2) {
                continue;
            }
            int stride = Math.max(3, size / 4);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals, size, size, stride,
                    learningProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    (int) Math.round(size * 1.45), size, stride, learningProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    size, (int) Math.round(size * 1.45), stride, learningProfile);
            scanCircles(integral, taskBoundary, mappedBuildings, proposals, size, stride,
                    learningProfile);
        }

        proposals.sort(Comparator.comparingInt(Candidate::getConfidence).reversed());
        List<Candidate> retained = new ArrayList<>();
        for (Candidate proposal : proposals) {
            boolean duplicate = false;
            for (Candidate existing : retained) {
                if (overlap(proposal.bounds, existing.bounds) > 0.28
                        || centreDistance(proposal.bounds, existing.bounds)
                        < Math.min(proposal.bounds.width, existing.bounds.width) * 0.55) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                retained.add(proposal);
                if (retained.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        return new Result(retained);
    }

    static Evidence evidenceFor(BufferedImage image, Shape shape, Rectangle bounds) {
        Assessment assessment = assess(image, shape, bounds);
        return assessment == null ? null : assessment.evidence;
    }

    static Assessment assess(BufferedImage image, Shape shape, Rectangle bounds) {
        if (image == null || shape == null || bounds == null || bounds.width < 4
                || bounds.height < 4) {
            return null;
        }
        IntegralImage integral = new IntegralImage(image);
        Rectangle clipped = bounds.intersection(new Rectangle(1, 1,
                Math.max(0, image.getWidth() - 2), Math.max(0, image.getHeight() - 2)));
        if (clipped.width < 4 || clipped.height < 4) {
            return null;
        }
        ScoredEvidence measurement = shape == Shape.ROUND
                ? circleConfidence(integral, clipped)
                : rectangleConfidence(integral, clipped);
        return new Assessment(percent(measurement.score), measurement.evidence);
    }

    private static void scanRectangles(IntegralImage integral, Polygon boundary,
            List<Polygon> mapped, List<Candidate> proposals, int width, int height, int stride,
            LearningProfile learningProfile) {
        int marginX = Math.max(3, width / 4);
        int marginY = Math.max(3, height / 4);
        for (int y = marginY; y + height + marginY < integral.height; y += stride) {
            for (int x = marginX; x + width + marginX < integral.width; x += stride) {
                Rectangle box = new Rectangle(x, y, width, height);
                if (!insideBoundary(boundary, box) || overlapsMapped(mapped, box)) {
                    continue;
                }
                ScoredEvidence measurement = rectangleConfidence(integral, box);
                double confidence = learningProfile == null ? measurement.score
                        : learningProfile.adjust(measurement.score, measurement.evidence);
                int score = percent(confidence);
                if (score >= MIN_CONFIDENCE
                        && percent(measurement.score) >= MIN_BASELINE_FOR_LEARNED_ADMISSION) {
                    proposals.add(new Candidate(Shape.RECTANGULAR, score,
                            percent(measurement.score), box, measurement.evidence));
                }
            }
        }
    }

    private static void scanCircles(IntegralImage integral, Polygon boundary,
            List<Polygon> mapped, List<Candidate> proposals, int diameter, int stride,
            LearningProfile learningProfile) {
        int margin = Math.max(3, diameter / 4);
        for (int y = margin; y + diameter + margin < integral.height; y += stride) {
            for (int x = margin; x + diameter + margin < integral.width; x += stride) {
                Rectangle box = new Rectangle(x, y, diameter, diameter);
                if (!insideBoundary(boundary, box) || overlapsMapped(mapped, box)) {
                    continue;
                }
                ScoredEvidence measurement = circleConfidence(integral, box);
                double confidence = learningProfile == null ? measurement.score
                        : learningProfile.adjust(measurement.score, measurement.evidence);
                int score = percent(confidence);
                if (score >= MIN_CONFIDENCE
                        && percent(measurement.score) >= MIN_BASELINE_FOR_LEARNED_ADMISSION) {
                    proposals.add(new Candidate(Shape.ROUND, score,
                            percent(measurement.score), box, measurement.evidence));
                }
            }
        }
    }

    private static ScoredEvidence rectangleConfidence(IntegralImage image, Rectangle box) {
        int insetX = Math.max(2, box.width / 5);
        int insetY = Math.max(2, box.height / 5);
        Rectangle inside = new Rectangle(box.x + insetX, box.y + insetY,
                Math.max(2, box.width - insetX * 2), Math.max(2, box.height - insetY * 2));
        int paddingX = Math.max(3, box.width / 4);
        int paddingY = Math.max(3, box.height / 4);
        Rectangle outer = new Rectangle(box.x - paddingX, box.y - paddingY,
                box.width + paddingX * 2, box.height + paddingY * 2);
        double insideMean = image.mean(inside);
        double insideStd = image.stdDev(inside);
        double outerMean = image.ringMean(outer, box);
        double consistency = clamp(1.0 - Math.max(0, insideStd - 7.0) / 47.0);
        double contrast = clamp(Math.abs(insideMean - outerMean) / 52.0);

        int edge = Math.max(2, Math.min(5, Math.min(box.width, box.height) / 5));
        double topEdge = image.gradientMean(new Rectangle(box.x, box.y - edge / 2, box.width, edge));
        double bottomEdge = image.gradientMean(new Rectangle(box.x, box.y + box.height - edge / 2,
                box.width, edge));
        double leftEdge = image.gradientMean(new Rectangle(box.x - edge / 2, box.y, edge, box.height));
        double rightEdge = image.gradientMean(new Rectangle(box.x + box.width - edge / 2, box.y,
                edge, box.height));
        double boundary = clamp((topEdge + bottomEdge + leftEdge + rightEdge) / 4.0 / 54.0);
        double weakestOpposingPair = Math.min((topEdge + bottomEdge) / 2.0,
                (leftEdge + rightEdge) / 2.0);
        double geometry = clamp(weakestOpposingPair / 42.0);

        double[] sides = sideMeans(image, box, paddingX, paddingY);
        double shadow = shadowCue(insideMean, sides);
        boolean vegetation = strongVegetation(image, inside);
        Evidence evidence = new Evidence(consistency, contrast, boundary, shadow, geometry);
        if (vegetation || contrast < 0.16 || boundary < 0.30
                || geometry < 0.28 || shadow < 0.18) {
            return new ScoredEvidence(0, evidence);
        }
        return new ScoredEvidence(clamp(consistency * 0.10 + contrast * 0.20 + boundary * 0.25
                + shadow * 0.30 + geometry * 0.15), evidence);
    }

    private static ScoredEvidence circleConfidence(IntegralImage image, Rectangle box) {
        double centreX = box.getCenterX();
        double centreY = box.getCenterY();
        double radius = Math.min(box.width, box.height) / 2.0;
        Samples inside = new Samples();
        Samples outside = new Samples();
        Samples boundary = new Samples();
        double[] sectors = new double[8];
        int[] sectorCounts = new int[8];
        int startX = Math.max(1, box.x - Math.max(3, box.width / 4));
        int endX = Math.min(image.width - 2, box.x + box.width + Math.max(3, box.width / 4));
        int startY = Math.max(1, box.y - Math.max(3, box.height / 4));
        int endY = Math.min(image.height - 2, box.y + box.height + Math.max(3, box.height / 4));
        int step = Math.max(1, box.width / 18);
        for (int y = startY; y <= endY; y += step) {
            for (int x = startX; x <= endX; x += step) {
                double distance = Math.hypot(x - centreX, y - centreY) / radius;
                if (distance <= 0.68) {
                    inside.add(image.value(x, y));
                } else if (distance >= 0.92 && distance <= 1.10) {
                    boundary.add(image.gradient(x, y));
                } else if (distance > 1.10 && distance <= 1.45) {
                    double value = image.value(x, y);
                    outside.add(value);
                    int sector = Math.min(7, (int) Math.floor((Math.atan2(y - centreY, x - centreX)
                            + Math.PI) / (Math.PI / 4.0)));
                    sectors[sector] += value;
                    sectorCounts[sector]++;
                }
            }
        }
        if (inside.count < 12 || outside.count < 12 || boundary.count < 8) {
            return new ScoredEvidence(0, null);
        }
        double consistency = clamp(1.0 - Math.max(0, inside.stdDev() - 7.0) / 47.0);
        double contrast = clamp(Math.abs(inside.mean() - outside.mean()) / 52.0);
        double edge = clamp(boundary.mean() / 54.0);
        double shadow = shadowCue(inside.mean(), sectorMeans(sectors, sectorCounts));
        double circularBoundary = clamp(edge * (0.65 + contrast * 0.35));
        Rectangle centre = new Rectangle(
                (int) Math.round(centreX - radius * 0.68),
                (int) Math.round(centreY - radius * 0.68),
                Math.max(2, (int) Math.round(radius * 1.36)),
                Math.max(2, (int) Math.round(radius * 1.36)));
        boolean vegetation = strongVegetation(image, centre);
        Evidence evidence = new Evidence(consistency, contrast, edge, shadow,
                circularBoundary);
        if (vegetation || contrast < 0.16 || edge < 0.30
                || circularBoundary < 0.24 || shadow < 0.18) {
            return new ScoredEvidence(0, evidence);
        }
        return new ScoredEvidence(clamp(consistency * 0.10 + contrast * 0.20 + edge * 0.25
                + shadow * 0.30 + circularBoundary * 0.15), evidence);
    }

    private static double[] sideMeans(IntegralImage image, Rectangle box, int paddingX, int paddingY) {
        return new double[] {
            image.mean(new Rectangle(box.x, box.y - paddingY, box.width, paddingY)),
            image.mean(new Rectangle(box.x, box.y + box.height, box.width, paddingY)),
            image.mean(new Rectangle(box.x - paddingX, box.y, paddingX, box.height)),
            image.mean(new Rectangle(box.x + box.width, box.y, paddingX, box.height))
        };
    }

    private static double[] sectorMeans(double[] sums, int[] counts) {
        double[] means = new double[sums.length];
        for (int index = 0; index < sums.length; index++) {
            means[index] = counts[index] == 0 ? Double.NaN : sums[index] / counts[index];
        }
        return means;
    }

    private static double shadowCue(double roofMean, double[] surroundings) {
        List<Double> valid = new ArrayList<>();
        double darkest = Double.MAX_VALUE;
        int darkestIndex = -1;
        for (int index = 0; index < surroundings.length; index++) {
            if (!Double.isNaN(surroundings[index])) {
                valid.add(surroundings[index]);
                if (surroundings[index] < darkest) {
                    darkest = surroundings[index];
                    darkestIndex = index;
                }
            }
        }
        if (valid.size() < 3 || darkestIndex < 0) {
            return 0;
        }
        Collections.sort(valid);
        double median = valid.get(valid.size() / 2);
        int previousIndex = (darkestIndex + surroundings.length - 1) % surroundings.length;
        int nextIndex = (darkestIndex + 1) % surroundings.length;
        double adjacent = minValid(surroundings[previousIndex], surroundings[nextIndex], darkest);
        double band = (darkest + adjacent) / 2.0;
        double directionalDrop = Math.max(0, median - band);
        double roofDrop = Math.max(0, roofMean - band);
        // A bright patch surrounded by darker ground is not a shadow. Require a
        // coherent one-sided dark band as well as separation from the roof.
        double direction = clamp((directionalDrop - 5.0) / 24.0);
        double roofSeparation = clamp((roofDrop - 4.0) / 34.0);
        return direction * roofSeparation;
    }

    private static boolean strongVegetation(IntegralImage image, Rectangle inside) {
        double red = image.redMean(inside);
        double green = image.greenMean(inside);
        double blue = image.blueMean(inside);
        double other = (red + blue) / 2.0;
        return green - other > 11.0 && green / Math.max(1.0, other) > 1.12;
    }

    private static double minValid(double first, double second, double fallback) {
        if (Double.isNaN(first)) {
            return Double.isNaN(second) ? fallback : second;
        }
        return Double.isNaN(second) ? first : Math.min(first, second);
    }

    private static boolean insideBoundary(Polygon boundary, Rectangle box) {
        int centreX = (int) Math.round(box.getCenterX());
        int centreY = (int) Math.round(box.getCenterY());
        return boundary.contains(centreX, centreY)
                && boundary.contains(box.x, centreY)
                && boundary.contains(box.x + box.width, centreY)
                && boundary.contains(centreX, box.y)
                && boundary.contains(centreX, box.y + box.height);
    }

    private static boolean overlapsMapped(List<Polygon> mapped, Rectangle candidate) {
        if (mapped == null) {
            return false;
        }
        double centreX = candidate.getCenterX();
        double centreY = candidate.getCenterY();
        for (Polygon building : mapped) {
            Rectangle bounds = building.getBounds();
            if (building.contains(centreX, centreY)
                    || candidate.contains(bounds.getCenterX(), bounds.getCenterY())
                    || overlap(candidate, bounds) > 0.18) {
                return true;
            }
        }
        return false;
    }

    private static double overlap(Rectangle first, Rectangle second) {
        Rectangle intersection = first.intersection(second);
        if (intersection.isEmpty()) {
            return 0;
        }
        double intersectionArea = intersection.width * (double) intersection.height;
        double union = first.width * (double) first.height + second.width * (double) second.height
                - intersectionArea;
        return union <= 0 ? 0 : intersectionArea / union;
    }

    private static double centreDistance(Rectangle first, Rectangle second) {
        return Math.hypot(first.getCenterX() - second.getCenterX(),
                first.getCenterY() - second.getCenterY());
    }

    private static int percent(double value) {
        return (int) Math.round(clamp(value) * 100.0);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    enum Shape {
        RECTANGULAR,
        ROUND
    }

    static final class Candidate {
        private final Shape shape;
        private final int confidence;
        private final int baselineConfidence;
        private final Rectangle bounds;
        private final Evidence evidence;

        Candidate(Shape shape, int confidence, int baselineConfidence, Rectangle bounds,
                Evidence evidence) {
            this.shape = shape;
            this.confidence = confidence;
            this.baselineConfidence = baselineConfidence;
            this.bounds = new Rectangle(bounds);
            this.evidence = evidence;
        }

        Shape getShape() { return shape; }
        int getConfidence() { return confidence; }
        int getBaselineConfidence() { return baselineConfidence; }
        Rectangle getBounds() { return new Rectangle(bounds); }
        Evidence getEvidence() { return evidence; }
        boolean isHighConfidence() { return confidence >= HIGH_CONFIDENCE; }
    }

    static final class Evidence {
        private final double consistency;
        private final double contrast;
        private final double boundary;
        private final double shadow;
        private final double geometry;

        Evidence(double consistency, double contrast, double boundary, double shadow,
                double geometry) {
            this.consistency = clamp(consistency);
            this.contrast = clamp(contrast);
            this.boundary = clamp(boundary);
            this.shadow = clamp(shadow);
            this.geometry = clamp(geometry);
        }

        double[] values() {
            return new double[] {consistency, contrast, boundary, shadow, geometry};
        }
    }

    static final class Assessment {
        private final int score;
        private final Evidence evidence;

        Assessment(int score, Evidence evidence) {
            this.score = score;
            this.evidence = evidence;
        }

        int getScore() { return score; }
        Evidence getEvidence() { return evidence; }
    }

    private static final class ScoredEvidence {
        private final double score;
        private final Evidence evidence;

        ScoredEvidence(double score, Evidence evidence) {
            this.score = score;
            this.evidence = evidence;
        }
    }

    static final class Result {
        private final List<Candidate> candidates;

        Result(List<Candidate> candidates) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        List<Candidate> getCandidates() { return candidates; }
        int count(Shape shape, boolean highOnly) {
            int count = 0;
            for (Candidate candidate : candidates) {
                if (candidate.shape == shape && (!highOnly || candidate.isHighConfidence())) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class IntegralImage {
        private final int width;
        private final int height;
        private final double[] values;
        private final double[] gradients;
        private final double[] sum;
        private final double[] sumSquares;
        private final double[] gradientSum;
        private final double[] redSum;
        private final double[] greenSum;
        private final double[] blueSum;

        IntegralImage(BufferedImage image) {
            width = image.getWidth();
            height = image.getHeight();
            values = new double[width * height];
            gradients = new double[width * height];
            double[] reds = new double[width * height];
            double[] greens = new double[width * height];
            double[] blues = new double[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int index = y * width + x;
                    reds[index] = (rgb >> 16) & 0xff;
                    greens[index] = (rgb >> 8) & 0xff;
                    blues[index] = rgb & 0xff;
                    values[index] = 0.2126 * reds[index]
                            + 0.7152 * greens[index] + 0.0722 * blues[index];
                }
            }
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    double horizontal = values[y * width + x + 1] - values[y * width + x - 1];
                    double vertical = values[(y + 1) * width + x] - values[(y - 1) * width + x];
                    gradients[y * width + x] = Math.hypot(horizontal, vertical) / 2.0;
                }
            }
            sum = integral(values, false);
            sumSquares = integral(values, true);
            gradientSum = integral(gradients, false);
            redSum = integral(reds, false);
            greenSum = integral(greens, false);
            blueSum = integral(blues, false);
        }

        double value(int x, int y) { return values[y * width + x]; }
        double gradient(int x, int y) { return gradients[y * width + x]; }

        double redMean(Rectangle rectangle) { return channelMean(redSum, rectangle); }
        double greenMean(Rectangle rectangle) { return channelMean(greenSum, rectangle); }
        double blueMean(Rectangle rectangle) { return channelMean(blueSum, rectangle); }

        private double channelMean(double[] channel, Rectangle rectangle) {
            Rectangle clipped = clip(rectangle);
            return clipped.isEmpty() ? 0 : regionSum(channel, clipped) / area(clipped);
        }

        double mean(Rectangle rectangle) {
            Rectangle clipped = clip(rectangle);
            return clipped.isEmpty() ? Double.NaN : regionSum(sum, clipped) / area(clipped);
        }

        double gradientMean(Rectangle rectangle) {
            Rectangle clipped = clip(rectangle);
            return clipped.isEmpty() ? 0 : regionSum(gradientSum, clipped) / area(clipped);
        }

        double stdDev(Rectangle rectangle) {
            Rectangle clipped = clip(rectangle);
            if (clipped.isEmpty()) {
                return 255;
            }
            double mean = regionSum(sum, clipped) / area(clipped);
            double variance = regionSum(sumSquares, clipped) / area(clipped) - mean * mean;
            return Math.sqrt(Math.max(0, variance));
        }

        double ringMean(Rectangle outer, Rectangle inner) {
            Rectangle clippedOuter = clip(outer);
            Rectangle clippedInner = clip(inner);
            double count = area(clippedOuter) - area(clippedInner);
            return count <= 0 ? mean(clippedOuter)
                    : (regionSum(sum, clippedOuter) - regionSum(sum, clippedInner)) / count;
        }

        private Rectangle clip(Rectangle rectangle) {
            return rectangle.intersection(new Rectangle(0, 0, width, height));
        }

        private double[] integral(double[] source, boolean square) {
            double[] result = new double[(width + 1) * (height + 1)];
            for (int y = 1; y <= height; y++) {
                double row = 0;
                for (int x = 1; x <= width; x++) {
                    double value = source[(y - 1) * width + x - 1];
                    row += square ? value * value : value;
                    result[y * (width + 1) + x] = result[(y - 1) * (width + 1) + x] + row;
                }
            }
            return result;
        }

        private double regionSum(double[] source, Rectangle rectangle) {
            int stride = width + 1;
            int x1 = rectangle.x;
            int y1 = rectangle.y;
            int x2 = rectangle.x + rectangle.width;
            int y2 = rectangle.y + rectangle.height;
            return source[y2 * stride + x2] - source[y1 * stride + x2]
                    - source[y2 * stride + x1] + source[y1 * stride + x1];
        }

        private static double area(Rectangle rectangle) {
            return rectangle.width * (double) rectangle.height;
        }
    }

    private static final class Samples {
        private double sum;
        private double sumSquares;
        private int count;

        void add(double value) {
            sum += value;
            sumSquares += value * value;
            count++;
        }

        double mean() { return count == 0 ? 0 : sum / count; }
        double stdDev() {
            double mean = mean();
            return count == 0 ? 255 : Math.sqrt(Math.max(0, sumSquares / count - mean * mean));
        }
    }
}
