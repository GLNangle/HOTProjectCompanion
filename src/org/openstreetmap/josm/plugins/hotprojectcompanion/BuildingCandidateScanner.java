package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Conservative, dependency-free proposal scan for roof-like regions in a task image. */
final class BuildingCandidateScanner {
    private static final int HIGH_CONFIDENCE = 78;

    private BuildingCandidateScanner() {
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings) {
        return scan(image, taskBoundary, mappedBuildings, null);
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile) {
        return scan(image, taskBoundary, mappedBuildings, learningProfile, null);
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile) {
        return scan(image, taskBoundary, mappedBuildings, learningProfile, geometryProfile,
                Double.NaN);
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile,
            double metresPerPixel) {
        return scan(image, taskBoundary, mappedBuildings, learningProfile, geometryProfile,
                metresPerPixel, ScanMode.BALANCED, Collections.emptyList());
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile,
            double metresPerPixel, ScanMode mode, List<Rectangle> reviewedRegions) {
        return scan(image, taskBoundary, mappedBuildings, learningProfile, geometryProfile,
                metresPerPixel, mode, reviewedRegions, null);
    }

    static Result scan(BufferedImage image, Polygon taskBoundary, List<Polygon> mappedBuildings,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile,
            double metresPerPixel, ScanMode mode, List<Rectangle> reviewedRegions,
            SharedLearningProfile sharedProfile) {
        if (image == null || taskBoundary == null || taskBoundary.npoints < 3) {
            throw new IllegalArgumentException("A task image and boundary are required");
        }
        ScanMode effectiveMode = mode == null ? ScanMode.CONSERVATIVE : mode;
        List<Rectangle> exclusions = reviewedRegions == null
                ? Collections.emptyList() : reviewedRegions;
        IntegralImage integral = new IntegralImage(image);
        List<Candidate> proposals = new ArrayList<>();
        int shortest = Math.min(image.getWidth(), image.getHeight());
        int[] sizes = candidateSizes(metresPerPixel, shortest);
        for (int size : sizes) {
            if (size > shortest / 2) {
                continue;
            }
            // The hard edge-coverage gates need a reasonably close fit. A finer
            // stride avoids missing a real roof simply because the coarse grid
            // landed a few pixels outside its perimeter.
            // Conservative needs precise placement to meet its strict threshold.
            // Exploratory now uses the same placement precision: lower thresholds
            // were ineffective when its coarser grid skipped across a clear roof.
            int strideDivisor = effectiveMode == ScanMode.BALANCED ? 8 : 12;
            int stride = Math.max(3, size / strideDivisor);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals, size, size, stride,
                    learningProfile, geometryProfile, effectiveMode, exclusions, sharedProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    (int) Math.round(size * 1.33), size, stride,
                    learningProfile, geometryProfile, effectiveMode, exclusions, sharedProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    size, (int) Math.round(size * 1.33), stride,
                    learningProfile, geometryProfile, effectiveMode, exclusions, sharedProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    (int) Math.round(size * 1.45), size, stride, learningProfile, geometryProfile,
                    effectiveMode, exclusions, sharedProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    size, (int) Math.round(size * 1.45), stride, learningProfile, geometryProfile,
                    effectiveMode, exclusions, sharedProfile);
            if (effectiveMode == ScanMode.EXPLORATORY) {
                scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                        (int) Math.round(size * 1.70), size, stride,
                        learningProfile, geometryProfile, effectiveMode, exclusions,
                        sharedProfile);
                scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                        size, (int) Math.round(size * 1.70), stride,
                        learningProfile, geometryProfile, effectiveMode, exclusions,
                        sharedProfile);
            }
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    size * 2, size, stride, learningProfile, geometryProfile,
                    effectiveMode, exclusions, sharedProfile);
            scanRectangles(integral, taskBoundary, mappedBuildings, proposals,
                    size, size * 2, stride, learningProfile, geometryProfile,
                    effectiveMode, exclusions, sharedProfile);
            if (size >= 26) {
                scanLShapes(integral, taskBoundary, mappedBuildings, proposals, size, size,
                        stride, learningProfile, effectiveMode, exclusions, sharedProfile,
                        0.50, 0.50);
                if (effectiveMode == ScanMode.EXPLORATORY) {
                    int elongated = (int) Math.round(size * 1.33);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals,
                            elongated, size, stride, learningProfile, effectiveMode, exclusions,
                            sharedProfile, 0.50, 0.50);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals,
                            size, elongated, stride, learningProfile, effectiveMode, exclusions,
                            sharedProfile, 0.50, 0.50);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals, size, size,
                            stride, learningProfile, effectiveMode, exclusions, sharedProfile,
                            0.38, 0.62);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals, size, size,
                            stride, learningProfile, effectiveMode, exclusions, sharedProfile,
                            0.62, 0.38);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals,
                            elongated, size, stride, learningProfile, effectiveMode, exclusions,
                            sharedProfile, 0.38, 0.62);
                    scanLShapes(integral, taskBoundary, mappedBuildings, proposals,
                            size, elongated, stride, learningProfile, effectiveMode, exclusions,
                            sharedProfile, 0.62, 0.38);
                }
            }
            if (size >= 19) {
                scanCircles(integral, taskBoundary, mappedBuildings, proposals, size, stride,
                        learningProfile, geometryProfile, effectiveMode, exclusions,
                        sharedProfile);
            }
        }

        proposals.sort(Comparator.comparingInt(Candidate::getConfidence).reversed());
        List<Candidate> retained = new ArrayList<>();
        for (Candidate proposal : proposals) {
            boolean duplicate = false;
            for (Candidate existing : retained) {
                // A small high-scoring fragment inside a long roof is not a
                // duplicate of the correctly sized whole-building proposal.
                if (overlap(proposal.bounds, existing.bounds) > 0.42
                        || centreDistance(proposal.bounds, existing.bounds)
                        < Math.min(proposal.bounds.width, existing.bounds.width) * 0.55) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                retained.add(proposal);
                if (retained.size() >= effectiveMode.maxResults) {
                    break;
                }
            }
        }
        return new Result(retained);
    }

    private static void scanLShapes(IntegralImage integral, Polygon boundary,
            List<Polygon> mapped, List<Candidate> proposals, int width, int height, int stride,
            LearningProfile learningProfile, ScanMode mode, List<Rectangle> reviewedRegions,
            SharedLearningProfile sharedProfile, double armFractionX,
            double armFractionY) {
        int marginX = Math.max(3, width / 4);
        int marginY = Math.max(3, height / 4);
        for (int y = marginY; y + height + marginY < integral.height; y += stride) {
            for (int x = marginX; x + width + marginX < integral.width; x += stride) {
                Rectangle box = new Rectangle(x, y, width, height);
                if (!insideBoundary(boundary, box) || overlapsMapped(mapped, box)
                        || overlapsReviewed(reviewedRegions, box)) {
                    continue;
                }
                for (LCorner corner : LCorner.values()) {
                    ScoredEvidence measurement = lShapeConfidence(integral, box, corner, mode,
                            armFractionX, armFractionY);
                    double localConfidence = learningProfile == null ? measurement.score
                            : learningProfile.adjust(measurement.score, measurement.evidence);
                    double confidence = sharedProfile == null ? localConfidence
                            : sharedProfile.adjust(localConfidence, measurement.evidence);
                    int score = percent(confidence);
                    boolean flexibleTemplate = width != height
                            || Math.abs(armFractionX - 0.50) > 0.01
                            || Math.abs(armFractionY - 0.50) > 0.01;
                    int templateMargin = flexibleTemplate ? 4 : 0;
                    if (score >= mode.minimumConfidence + templateMargin
                            && percent(measurement.score)
                                    >= mode.minimumBaseline + templateMargin) {
                        proposals.add(new Candidate(Shape.L_SHAPED, score,
                                percent(measurement.score), percent(localConfidence), box,
                                measurement.evidence, corner,
                                armFractionX, armFractionY));
                    }
                }
            }
        }
    }

    static int[] candidateSizes(double metresPerPixel, int shortestImageSide) {
        if (!Double.isFinite(metresPerPixel) || metresPerPixel <= 0) {
            return new int[] {14, 19, 26, 36, 50, 68};
        }
        double[] plausibleMetres = {2.5, 4, 6, 9, 13, 19, 28, 42};
        int maximum = Math.max(14, Math.min(180, shortestImageSide / 2));
        List<Integer> result = new ArrayList<>();
        for (double metres : plausibleMetres) {
            int pixels = Math.max(12, Math.min(maximum,
                    (int) Math.round(metres / metresPerPixel)));
            if (!result.contains(pixels)) {
                result.add(pixels);
            }
        }
        int[] sizes = new int[result.size()];
        for (int index = 0; index < result.size(); index++) {
            sizes[index] = result.get(index);
        }
        Arrays.sort(sizes);
        return sizes;
    }

    static Evidence evidenceFor(BufferedImage image, Shape shape, Rectangle bounds) {
        Assessment assessment = assess(image, shape, bounds);
        return assessment == null ? null : assessment.evidence;
    }

    static Assessment assess(BufferedImage image, Shape shape, Rectangle bounds) {
        return assess(image, shape, bounds, ScanMode.BALANCED);
    }

    static Assessment assess(BufferedImage image, Shape shape, Rectangle bounds,
            ScanMode mode) {
        if (image == null || shape == null || bounds == null || bounds.width < 4
                || bounds.height < 4) {
            return null;
        }
        ScanMode effectiveMode = mode == null ? ScanMode.BALANCED : mode;
        IntegralImage integral = new IntegralImage(image);
        Rectangle clipped = bounds.intersection(new Rectangle(1, 1,
                Math.max(0, image.getWidth() - 2), Math.max(0, image.getHeight() - 2)));
        if (clipped.width < 4 || clipped.height < 4) {
            return null;
        }
        ScoredEvidence measurement = shape == Shape.ROUND
                ? circleConfidence(integral, clipped, effectiveMode)
                : rectangleConfidence(integral, clipped, effectiveMode);
        return new Assessment(percent(measurement.score), measurement.evidence);
    }

    private static void scanRectangles(IntegralImage integral, Polygon boundary,
            List<Polygon> mapped, List<Candidate> proposals, int width, int height, int stride,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile,
            ScanMode mode, List<Rectangle> reviewedRegions,
            SharedLearningProfile sharedProfile) {
        int marginX = Math.max(3, width / 4);
        int marginY = Math.max(3, height / 4);
        for (int y = marginY; y + height + marginY < integral.height; y += stride) {
            for (int x = marginX; x + width + marginX < integral.width; x += stride) {
                Rectangle box = new Rectangle(x, y, width, height);
                if (!insideBoundary(boundary, box) || overlapsMapped(mapped, box)
                        || overlapsReviewed(reviewedRegions, box)) {
                    continue;
                }
                ScoredEvidence measurement = rectangleConfidence(integral, box, mode);
                Rectangle displayBox = adjustedBox(box, integral, boundary, mapped,
                        geometryProfile, false);
                if (!displayBox.equals(box)) {
                    ScoredEvidence adjusted = rectangleConfidence(integral, displayBox, mode);
                    if (adjusted.evidence == null
                            || adjusted.score < measurement.score - 0.06
                            || percent(adjusted.score) < mode.minimumBaseline) {
                        displayBox = box;
                    } else {
                        measurement = adjusted;
                    }
                }
                double localConfidence = learningProfile == null ? measurement.score
                        : learningProfile.adjust(measurement.score, measurement.evidence);
                double confidence = sharedProfile == null ? localConfidence
                        : sharedProfile.adjust(localConfidence, measurement.evidence);
                int score = percent(confidence);
                if (score >= mode.minimumConfidence
                        && percent(measurement.score) >= mode.minimumBaseline) {
                    proposals.add(new Candidate(Shape.RECTANGULAR, score,
                            percent(measurement.score), percent(localConfidence), displayBox,
                            measurement.evidence));
                }
            }
        }
    }

    private static void scanCircles(IntegralImage integral, Polygon boundary,
            List<Polygon> mapped, List<Candidate> proposals, int diameter, int stride,
            LearningProfile learningProfile, GeometryLearningProfile geometryProfile,
            ScanMode mode, List<Rectangle> reviewedRegions,
            SharedLearningProfile sharedProfile) {
        int margin = Math.max(3, diameter / 4);
        for (int y = margin; y + diameter + margin < integral.height; y += stride) {
            for (int x = margin; x + diameter + margin < integral.width; x += stride) {
                Rectangle box = new Rectangle(x, y, diameter, diameter);
                if (!insideBoundary(boundary, box) || overlapsMapped(mapped, box)
                        || overlapsReviewed(reviewedRegions, box)) {
                    continue;
                }
                ScoredEvidence measurement = circleConfidence(integral, box, mode);
                Rectangle displayBox = adjustedBox(box, integral, boundary, mapped,
                        geometryProfile, true);
                if (!displayBox.equals(box)) {
                    ScoredEvidence adjusted = circleConfidence(integral, displayBox, mode);
                    if (adjusted.evidence == null
                            || adjusted.score < measurement.score - 0.06
                            || percent(adjusted.score) < mode.minimumBaseline) {
                        displayBox = box;
                    } else {
                        measurement = adjusted;
                    }
                }
                double localConfidence = learningProfile == null ? measurement.score
                        : learningProfile.adjust(measurement.score, measurement.evidence);
                double confidence = sharedProfile == null ? localConfidence
                        : sharedProfile.adjust(localConfidence, measurement.evidence);
                int score = percent(confidence);
                if (score >= mode.minimumConfidence
                        && percent(measurement.score) >= mode.minimumBaseline) {
                    proposals.add(new Candidate(Shape.ROUND, score,
                            percent(measurement.score), percent(localConfidence), displayBox,
                            measurement.evidence));
                }
            }
        }
    }

    private static Rectangle adjustedBox(Rectangle original, IntegralImage image,
            Polygon boundary, List<Polygon> mapped, GeometryLearningProfile profile,
            boolean keepSquare) {
        if (profile == null || !profile.hasActiveAdjustment()) {
            return original;
        }
        Rectangle adjusted = profile.adjust(original,
                new Rectangle(0, 0, image.width, image.height));
        if (keepSquare && adjusted.width != adjusted.height) {
            int diameter = Math.max(4, (adjusted.width + adjusted.height) / 2);
            adjusted = new Rectangle((int) Math.round(adjusted.getCenterX() - diameter / 2.0),
                    (int) Math.round(adjusted.getCenterY() - diameter / 2.0), diameter, diameter)
                    .intersection(new Rectangle(0, 0, image.width, image.height));
        }
        return insideBoundary(boundary, adjusted) && !overlapsMapped(mapped, adjusted)
                ? adjusted : original;
    }

    private static ScoredEvidence rectangleConfidence(IntegralImage image, Rectangle box,
            ScanMode mode) {
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
        double insideGradient = image.gradientMean(inside);
        double outerMean = image.ringMean(outer, box);
        double colourConsistency = clamp(1.0 - Math.max(0, insideStd - 7.0) / 47.0);
        double textureConsistency = clamp(1.0 - Math.max(0, insideGradient - 5.0) / 24.0);
        double consistency = Math.sqrt(colourConsistency * textureConsistency);
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
        double strongestEdge = Math.max(Math.max(topEdge, bottomEdge),
                Math.max(leftEdge, rightEdge));
        double weakestEdge = Math.min(Math.min(topEdge, bottomEdge),
                Math.min(leftEdge, rightEdge));
        double edgeBalance = clamp(weakestEdge / Math.max(1.0, strongestEdge));
        double edgeCoverage = rectangleEdgeCoverage(image, box);
        double geometry = clamp(weakestOpposingPair / 46.0)
                * Math.sqrt(edgeBalance) * Math.sqrt(edgeCoverage);

        double[] sides = sideMeans(image, box, paddingX, paddingY);
        double shadow = shadowCue(insideMean, sides);
        boolean vegetation = strongVegetation(image, inside);
        double colourEdgeCoverage = vegetation ? rectangleColourEdgeCoverage(image, box) : 0;
        boolean greenRoof = vegetation && strongGreenRoofEvidence(mode, consistency,
                contrast, shadow, colourEdgeCoverage);
        if (greenRoof) {
            // Green roofs can have weak luminance contrast even when their colour boundary
            // is crisp. This alternate path remains restricted to Exploratory mode and
            // still requires continuous edges, a uniform interior and a strong shadow.
            boundary = Math.max(boundary, colourEdgeCoverage * 0.62);
            geometry = Math.max(geometry, colourEdgeCoverage * 0.58);
            edgeBalance = Math.max(edgeBalance, colourEdgeCoverage);
            edgeCoverage = Math.max(edgeCoverage, colourEdgeCoverage);
        }
        Evidence evidence = new Evidence(consistency, contrast, boundary, shadow, geometry);
        if ((vegetation && !greenRoof) || consistency < mode.minimumConsistency
                || contrast < mode.minimumContrast || boundary < mode.minimumBoundary
                || edgeBalance < mode.minimumEdgeBalance
                || edgeCoverage < mode.minimumEdgeCoverage
                || geometry < mode.minimumGeometry || shadow < mode.minimumShadow) {
            return new ScoredEvidence(0, evidence);
        }
        return new ScoredEvidence(clamp(consistency * 0.08 + contrast * 0.18 + boundary * 0.24
                + shadow * 0.32 + geometry * 0.18), evidence);
    }

    /** Scores a six-sided, axis-aligned L rather than relaxing the rectangle rules. */
    private static ScoredEvidence lShapeConfidence(IntegralImage image, Rectangle box,
            LCorner corner, ScanMode mode, double armFractionX, double armFractionY) {
        int armX = Math.max(4, Math.min(box.width - 4,
                (int) Math.round(box.width * armFractionX)));
        int armY = Math.max(4, Math.min(box.height - 4,
                (int) Math.round(box.height * armFractionY)));
        Rectangle vertical;
        Rectangle horizontal;
        Rectangle notch;
        switch (corner) {
        case TOP_RIGHT:
            vertical = new Rectangle(box.x, box.y, armX, box.height);
            horizontal = new Rectangle(box.x, box.y + box.height - armY, box.width, armY);
            notch = new Rectangle(box.x + armX, box.y, box.width - armX,
                    box.height - armY);
            break;
        case BOTTOM_RIGHT:
            vertical = new Rectangle(box.x, box.y, armX, box.height);
            horizontal = new Rectangle(box.x, box.y, box.width, armY);
            notch = new Rectangle(box.x + armX, box.y + armY, box.width - armX,
                    box.height - armY);
            break;
        case TOP_LEFT:
            vertical = new Rectangle(box.x + box.width - armX, box.y, armX, box.height);
            horizontal = new Rectangle(box.x, box.y + box.height - armY, box.width, armY);
            notch = new Rectangle(box.x, box.y, box.width - armX, box.height - armY);
            break;
        case BOTTOM_LEFT:
        default:
            vertical = new Rectangle(box.x + box.width - armX, box.y, armX, box.height);
            horizontal = new Rectangle(box.x, box.y, box.width, armY);
            notch = new Rectangle(box.x, box.y + armY, box.width - armX,
                    box.height - armY);
            break;
        }

        Rectangle joint = vertical.intersection(horizontal);
        double verticalArea = vertical.width * (double) vertical.height;
        double horizontalArea = horizontal.width * (double) horizontal.height;
        double jointArea = joint.width * (double) joint.height;
        double occupiedArea = verticalArea + horizontalArea - jointArea;
        double verticalMean = image.mean(vertical);
        double horizontalMean = image.mean(horizontal);
        double jointMean = image.mean(joint);
        double roofMean = (verticalMean * verticalArea + horizontalMean * horizontalArea
                - jointMean * jointArea) / occupiedArea;
        double wingDifference = Math.abs(verticalMean - horizontalMean);
        double roofStd = Math.max(image.stdDev(vertical), image.stdDev(horizontal));
        double roofGradient = Math.max(image.gradientMean(vertical), image.gradientMean(horizontal));
        double colourConsistency = clamp(1.0 - Math.max(0, roofStd - 7.0) / 47.0);
        double textureConsistency = clamp(1.0 - Math.max(0, roofGradient - 5.0) / 24.0);
        double wingConsistency = clamp(1.0 - wingDifference / 24.0);
        double consistency = Math.cbrt(colourConsistency * textureConsistency * wingConsistency);

        int padding = Math.max(3, sizeOf(box) / 4);
        Rectangle outer = new Rectangle(box.x - padding, box.y - padding,
                box.width + padding * 2, box.height + padding * 2);
        double outerMean = image.ringMean(outer, box);
        double notchContrast = Math.abs(roofMean - image.mean(notch));
        double outsideContrast = Math.abs(roofMean - outerMean);
        double contrast = clamp((notchContrast * 0.65 + outsideContrast * 0.35) / 52.0);
        boolean vegetation = strongVegetation(image, vertical)
                || strongVegetation(image, horizontal);
        if (wingDifference > 18
                || notchContrast < (mode == ScanMode.EXPLORATORY ? 12 : 16)
                || consistency < mode.minimumConsistency || contrast < mode.minimumContrast) {
            return new ScoredEvidence(0,
                    new Evidence(consistency, contrast, 0, 0, 0));
        }
        double boundary = clamp(lBoundaryMean(image, box, corner, armX, armY) / 54.0);
        double edgeCoverage = lEdgeCoverage(image, box, corner, armX, armY);
        double innerEdgeCoverage = lInnerEdgeCoverage(image, box, corner, armX, armY);
        double geometry = Math.sqrt(boundary * edgeCoverage)
                * Math.sqrt(innerEdgeCoverage) * clamp(notchContrast / 42.0);
        double shadow = shadowCue(roofMean, sideMeans(image, box, padding, padding));
        Evidence evidence = new Evidence(consistency, contrast, boundary, shadow, geometry);
        if (vegetation || boundary < mode.minimumBoundary
                || edgeCoverage < mode.minimumEdgeCoverage
                || innerEdgeCoverage < (mode == ScanMode.CONSERVATIVE ? 0.68
                        : mode == ScanMode.BALANCED ? 0.56 : 0.42)
                || geometry < mode.minimumGeometry
                || shadow < mode.minimumShadow
                        * (mode == ScanMode.EXPLORATORY ? 1.0 : 0.50)) {
            return new ScoredEvidence(0, evidence);
        }
        // The concave corner is highly specific evidence, while a right-angled
        // irregular roof's
        // shadow is often split across two wings. Weight its six-edge geometry more
        // heavily than the rectangle detector weights a single shadow band.
        return new ScoredEvidence(clamp(consistency * 0.14 + contrast * 0.24
                + boundary * 0.20 + shadow * 0.16 + geometry * 0.26), evidence);
    }

    private static boolean strongGreenRoofEvidence(ScanMode mode, double consistency,
            double contrast, double shadow, double colourEdgeCoverage) {
        return mode == ScanMode.EXPLORATORY
                && consistency >= 0.74
                && contrast >= 0.30
                && shadow >= 0.42
                && colourEdgeCoverage >= 0.72;
    }

    private static int sizeOf(Rectangle box) {
        return Math.min(box.width, box.height);
    }

    private static double lBoundaryMean(IntegralImage image, Rectangle box, LCorner corner,
            int armX, int armY) {
        Polygon polygon = lPolygon(box, corner, armX, armY);
        Samples gradients = new Samples();
        samplePolygonEdges(image, polygon, gradients, false);
        return gradients.mean();
    }

    private static double lEdgeCoverage(IntegralImage image, Rectangle box, LCorner corner,
            int armX, int armY) {
        Polygon polygon = lPolygon(box, corner, armX, armY);
        Samples covered = new Samples();
        samplePolygonEdges(image, polygon, covered, true);
        return covered.mean();
    }

    private static double lInnerEdgeCoverage(IntegralImage image, Rectangle box,
            LCorner corner, int armX, int armY) {
        int left = box.x;
        int top = box.y;
        int right = box.x + box.width;
        int bottom = box.y + box.height;
        int middleX = (corner == LCorner.TOP_RIGHT || corner == LCorner.BOTTOM_RIGHT)
                ? box.x + armX : box.x + box.width - armX;
        int middleY = (corner == LCorner.TOP_RIGHT || corner == LCorner.TOP_LEFT)
                ? box.y + box.height - armY : box.y + armY;
        Samples covered = new Samples();
        switch (corner) {
        case TOP_RIGHT:
            sampleLineCoverage(image, middleX, top, middleX, middleY, covered);
            sampleLineCoverage(image, middleX, middleY, right, middleY, covered);
            break;
        case BOTTOM_RIGHT:
            sampleLineCoverage(image, middleX, middleY, right, middleY, covered);
            sampleLineCoverage(image, middleX, middleY, middleX, bottom, covered);
            break;
        case TOP_LEFT:
            sampleLineCoverage(image, middleX, top, middleX, middleY, covered);
            sampleLineCoverage(image, left, middleY, middleX, middleY, covered);
            break;
        case BOTTOM_LEFT:
        default:
            sampleLineCoverage(image, left, middleY, middleX, middleY, covered);
            sampleLineCoverage(image, middleX, middleY, middleX, bottom, covered);
            break;
        }
        return covered.mean();
    }

    private static void sampleLineCoverage(IntegralImage image, int x1, int y1,
            int x2, int y2, Samples covered) {
        int count = Math.max(5, Math.min(24, Math.max(Math.abs(x2 - x1),
                Math.abs(y2 - y1))));
        for (int index = 0; index < count; index++) {
            double fraction = (index + 0.5) / count;
            int x = (int) Math.round(x1 + (x2 - x1) * fraction);
            int y = (int) Math.round(y1 + (y2 - y1) * fraction);
            double strongest = 0;
            for (int offsetY = -2; offsetY <= 2; offsetY++) {
                for (int offsetX = -2; offsetX <= 2; offsetX++) {
                    strongest = Math.max(strongest, image.gradient(x + offsetX, y + offsetY));
                }
            }
            covered.add(strongest >= 16 ? 1 : 0);
        }
    }

    private static void samplePolygonEdges(IntegralImage image, Polygon polygon,
            Samples samples, boolean coverageOnly) {
        for (int edge = 0; edge < polygon.npoints; edge++) {
            int next = (edge + 1) % polygon.npoints;
            int x1 = polygon.xpoints[edge];
            int y1 = polygon.ypoints[edge];
            int x2 = polygon.xpoints[next];
            int y2 = polygon.ypoints[next];
            int count = Math.max(4, Math.min(24, Math.max(Math.abs(x2 - x1),
                    Math.abs(y2 - y1))));
            for (int index = 0; index < count; index++) {
                double fraction = (index + 0.5) / count;
                int x = (int) Math.round(x1 + (x2 - x1) * fraction);
                int y = (int) Math.round(y1 + (y2 - y1) * fraction);
                double strongest = 0;
                for (int offsetY = -2; offsetY <= 2; offsetY++) {
                    for (int offsetX = -2; offsetX <= 2; offsetX++) {
                        strongest = Math.max(strongest, image.gradient(x + offsetX, y + offsetY));
                    }
                }
                samples.add(coverageOnly ? (strongest >= 16 ? 1 : 0) : strongest);
            }
        }
    }

    private static Polygon lPolygon(Rectangle box, LCorner corner, int armX, int armY) {
        int left = box.x;
        int top = box.y;
        int right = box.x + box.width;
        int bottom = box.y + box.height;
        int middleX = (corner == LCorner.TOP_RIGHT || corner == LCorner.BOTTOM_RIGHT)
                ? left + armX : right - armX;
        int middleY = (corner == LCorner.TOP_RIGHT || corner == LCorner.TOP_LEFT)
                ? bottom - armY : top + armY;
        switch (corner) {
        case TOP_RIGHT:
            return new Polygon(new int[] {left, middleX, middleX, right, right, left},
                    new int[] {top, top, middleY, middleY, bottom, bottom}, 6);
        case BOTTOM_RIGHT:
            return new Polygon(new int[] {left, right, right, middleX, middleX, left},
                    new int[] {top, top, middleY, middleY, bottom, bottom}, 6);
        case TOP_LEFT:
            return new Polygon(new int[] {middleX, right, right, left, left, middleX},
                    new int[] {top, top, bottom, bottom, middleY, middleY}, 6);
        case BOTTOM_LEFT:
        default:
            return new Polygon(new int[] {left, right, right, middleX, middleX, left},
                    new int[] {top, top, bottom, bottom, middleY, middleY}, 6);
        }
    }

    private static ScoredEvidence circleConfidence(IntegralImage image, Rectangle box,
            ScanMode mode) {
        double centreX = box.getCenterX();
        double centreY = box.getCenterY();
        double radius = Math.min(box.width, box.height) / 2.0;
        Samples inside = new Samples();
        Samples insideTexture = new Samples();
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
                    insideTexture.add(image.gradient(x, y));
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
        double colourConsistency = clamp(1.0 - Math.max(0, inside.stdDev() - 7.0) / 47.0);
        double textureConsistency = clamp(1.0
                - Math.max(0, insideTexture.mean() - 5.0) / 24.0);
        double consistency = Math.sqrt(colourConsistency * textureConsistency);
        double contrast = clamp(Math.abs(inside.mean() - outside.mean()) / 52.0);
        double edge = clamp(boundary.mean() / 54.0);
        double shadow = shadowCue(inside.mean(), sectorMeans(sectors, sectorCounts));
        double edgeCoverage = circleEdgeCoverage(image, centreX, centreY, radius);
        double circularBoundary = clamp(edge * (0.55 + contrast * 0.25
                + edgeCoverage * 0.20));
        Rectangle centre = new Rectangle(
                (int) Math.round(centreX - radius * 0.68),
                (int) Math.round(centreY - radius * 0.68),
                Math.max(2, (int) Math.round(radius * 1.36)),
                Math.max(2, (int) Math.round(radius * 1.36)));
        boolean vegetation = strongVegetation(image, centre);
        Evidence evidence = new Evidence(consistency, contrast, edge, shadow,
                circularBoundary);
        if (vegetation || consistency < mode.minimumConsistency
                || contrast < mode.minimumContrast || edge < mode.minimumBoundary
                || edgeCoverage < mode.minimumCircleCoverage
                || circularBoundary < mode.minimumGeometry || shadow < mode.minimumShadow) {
            return new ScoredEvidence(0, evidence);
        }
        return new ScoredEvidence(clamp(consistency * 0.08 + contrast * 0.18 + edge * 0.24
                + shadow * 0.32 + circularBoundary * 0.18), evidence);
    }

    private static double rectangleEdgeCoverage(IntegralImage image, Rectangle box) {
        int samples = Math.max(8, Math.min(28, Math.max(box.width, box.height)));
        int covered = 0;
        int total = samples * 4;
        for (int index = 0; index < samples; index++) {
            double fraction = (index + 0.5) / samples;
            int x = box.x + (int) Math.round(fraction * box.width);
            int y = box.y + (int) Math.round(fraction * box.height);
            if (maxGradient(image, x, box.y, false) >= 16) covered++;
            if (maxGradient(image, x, box.y + box.height, false) >= 16) covered++;
            if (maxGradient(image, box.x, y, true) >= 16) covered++;
            if (maxGradient(image, box.x + box.width, y, true) >= 16) covered++;
        }
        return covered / (double) total;
    }

    private static double rectangleColourEdgeCoverage(IntegralImage image, Rectangle box) {
        int samples = Math.max(8, Math.min(28, Math.max(box.width, box.height)));
        int covered = 0;
        int total = samples * 4;
        for (int index = 0; index < samples; index++) {
            double fraction = (index + 0.5) / samples;
            int x = box.x + (int) Math.round(fraction * box.width);
            int y = box.y + (int) Math.round(fraction * box.height);
            if (maxColourGradient(image, x, box.y, false) >= 18) covered++;
            if (maxColourGradient(image, x, box.y + box.height, false) >= 18) covered++;
            if (maxColourGradient(image, box.x, y, true) >= 18) covered++;
            if (maxColourGradient(image, box.x + box.width, y, true) >= 18) covered++;
        }
        return covered / (double) total;
    }

    private static double circleEdgeCoverage(IntegralImage image, double centreX,
            double centreY, double radius) {
        int samples = 36;
        int covered = 0;
        for (int index = 0; index < samples; index++) {
            double angle = index * Math.PI * 2.0 / samples;
            double directionX = Math.cos(angle);
            double directionY = Math.sin(angle);
            double strongest = 0;
            for (int offset = -2; offset <= 2; offset++) {
                int x = (int) Math.round(centreX + directionX * (radius + offset));
                int y = (int) Math.round(centreY + directionY * (radius + offset));
                strongest = Math.max(strongest, image.gradient(x, y));
            }
            if (strongest >= 16) {
                covered++;
            }
        }
        return covered / (double) samples;
    }

    private static double maxGradient(IntegralImage image, int x, int y,
            boolean horizontalNormal) {
        double strongest = 0;
        for (int offset = -2; offset <= 2; offset++) {
            strongest = Math.max(strongest, horizontalNormal
                    ? image.gradient(x + offset, y)
                    : image.gradient(x, y + offset));
        }
        return strongest;
    }

    private static double maxColourGradient(IntegralImage image, int x, int y,
            boolean horizontalNormal) {
        double strongest = 0;
        for (int offset = -2; offset <= 2; offset++) {
            strongest = Math.max(strongest, horizontalNormal
                    ? image.colourGradient(x + offset, y)
                    : image.colourGradient(x, y + offset));
        }
        return strongest;
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

    private static boolean overlapsReviewed(List<Rectangle> reviewed, Rectangle candidate) {
        if (reviewed == null || reviewed.isEmpty()) {
            return false;
        }
        double centreX = candidate.getCenterX();
        double centreY = candidate.getCenterY();
        for (Rectangle area : reviewed) {
            if (area != null && (area.contains(centreX, centreY)
                    || candidate.contains(area.getCenterX(), area.getCenterY())
                    || overlap(candidate, area) > 0.12)) {
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
        ROUND,
        L_SHAPED
    }

    enum LCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    enum ScanMode {
        CONSERVATIVE("Conservative", 68, 66, 8,
                0.65, 0.28, 0.31, 0.34, 0.40, 0.55, 0.57, 0.25),
        BALANCED("Balanced (Recommended)", 61, 60, 12,
                0.60, 0.22, 0.28, 0.26, 0.34, 0.46, 0.50, 0.24),
        EXPLORATORY("Exploratory", 52, 49, 18,
                0.52, 0.14, 0.20, 0.14, 0.24, 0.34, 0.38, 0.16);

        private final String label;
        private final int minimumConfidence;
        private final int minimumBaseline;
        private final int maxResults;
        private final double minimumConsistency;
        private final double minimumContrast;
        private final double minimumBoundary;
        private final double minimumShadow;
        private final double minimumEdgeBalance;
        private final double minimumEdgeCoverage;
        private final double minimumCircleCoverage;
        private final double minimumGeometry;

        ScanMode(String label, int minimumConfidence, int minimumBaseline, int maxResults,
                double minimumConsistency, double minimumContrast, double minimumBoundary,
                double minimumShadow, double minimumEdgeBalance, double minimumEdgeCoverage,
                double minimumCircleCoverage, double minimumGeometry) {
            this.label = label;
            this.minimumConfidence = minimumConfidence;
            this.minimumBaseline = minimumBaseline;
            this.maxResults = maxResults;
            this.minimumConsistency = minimumConsistency;
            this.minimumContrast = minimumContrast;
            this.minimumBoundary = minimumBoundary;
            this.minimumShadow = minimumShadow;
            this.minimumEdgeBalance = minimumEdgeBalance;
            this.minimumEdgeCoverage = minimumEdgeCoverage;
            this.minimumCircleCoverage = minimumCircleCoverage;
            this.minimumGeometry = minimumGeometry;
        }

        static ScanMode fromPreference(String value) {
            try {
                return value == null ? CONSERVATIVE : valueOf(value);
            } catch (IllegalArgumentException exception) {
                return CONSERVATIVE;
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static final class Candidate {
        private final Shape shape;
        private final int confidence;
        private final int baselineConfidence;
        private final int localConfidence;
        private final Rectangle bounds;
        private final Evidence evidence;
        private final LCorner lCorner;
        private final double armFractionX;
        private final double armFractionY;

        Candidate(Shape shape, int confidence, int baselineConfidence, Rectangle bounds,
                Evidence evidence) {
            this(shape, confidence, baselineConfidence, confidence, bounds, evidence, null,
                    0.50, 0.50);
        }

        Candidate(Shape shape, int confidence, int baselineConfidence, Rectangle bounds,
                Evidence evidence, LCorner lCorner) {
            this(shape, confidence, baselineConfidence, confidence, bounds, evidence, lCorner,
                    0.50, 0.50);
        }

        Candidate(Shape shape, int confidence, int baselineConfidence, int localConfidence,
                Rectangle bounds, Evidence evidence) {
            this(shape, confidence, baselineConfidence, localConfidence, bounds, evidence, null,
                    0.50, 0.50);
        }

        Candidate(Shape shape, int confidence, int baselineConfidence, int localConfidence,
                Rectangle bounds,
                Evidence evidence, LCorner lCorner, double armFractionX,
                double armFractionY) {
            this.shape = shape;
            this.confidence = confidence;
            this.baselineConfidence = baselineConfidence;
            this.localConfidence = localConfidence;
            this.bounds = new Rectangle(bounds);
            this.evidence = evidence;
            this.lCorner = lCorner;
            this.armFractionX = armFractionX;
            this.armFractionY = armFractionY;
        }

        Shape getShape() { return shape; }
        int getConfidence() { return confidence; }
        int getBaselineConfidence() { return baselineConfidence; }
        int getLocalConfidence() { return localConfidence; }
        Rectangle getBounds() { return new Rectangle(bounds); }
        Evidence getEvidence() { return evidence; }
        LCorner getLCorner() { return lCorner; }
        double getArmFractionX() { return armFractionX; }
        double getArmFractionY() { return armFractionY; }
        boolean isHighConfidence() { return confidence >= HIGH_CONFIDENCE; }
        String explanation() { return evidence == null ? "limited visual evidence"
                : evidence.explanation(); }
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

        String explanation() {
            List<String> reasons = new ArrayList<>();
            if (boundary >= 0.60 && geometry >= 0.50) {
                reasons.add("coherent roof boundary");
            } else if (boundary >= 0.42) {
                reasons.add("visible boundary contrast");
            }
            if (shadow >= 0.52) {
                reasons.add("directional shadow");
            } else if (shadow >= 0.30) {
                reasons.add("possible one-sided shadow");
            }
            if (consistency >= 0.72) {
                reasons.add("consistent interior texture");
            }
            if (contrast >= 0.45) {
                reasons.add("clear separation from surroundings");
            }
            if (reasons.isEmpty()) {
                reasons.add("combined boundary, contrast and shadow evidence");
            }
            return String.join(", ", reasons);
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
        private final double[] colourGradients;
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
            colourGradients = new double[width * height];
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
                    int index = y * width + x;
                    int left = index - 1;
                    int right = index + 1;
                    int up = index - width;
                    int down = index + width;
                    double horizontalColour = Math.sqrt(
                            square(reds[right] - reds[left])
                            + square(greens[right] - greens[left])
                            + square(blues[right] - blues[left])) / 2.0;
                    double verticalColour = Math.sqrt(
                            square(reds[down] - reds[up])
                            + square(greens[down] - greens[up])
                            + square(blues[down] - blues[up])) / 2.0;
                    colourGradients[index] = Math.hypot(horizontalColour,
                            verticalColour) / Math.sqrt(2.0);
                }
            }
            sum = integral(values, false);
            sumSquares = integral(values, true);
            gradientSum = integral(gradients, false);
            redSum = integral(reds, false);
            greenSum = integral(greens, false);
            blueSum = integral(blues, false);
        }

        double value(int x, int y) {
            int safeX = Math.max(0, Math.min(width - 1, x));
            int safeY = Math.max(0, Math.min(height - 1, y));
            return values[safeY * width + safeX];
        }

        double gradient(int x, int y) {
            int safeX = Math.max(0, Math.min(width - 1, x));
            int safeY = Math.max(0, Math.min(height - 1, y));
            return gradients[safeY * width + safeX];
        }

        double colourGradient(int x, int y) {
            int safeX = Math.max(0, Math.min(width - 1, x));
            int safeY = Math.max(0, Math.min(height - 1, y));
            return colourGradients[safeY * width + safeX];
        }

        private static double square(double value) {
            return value * value;
        }

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
