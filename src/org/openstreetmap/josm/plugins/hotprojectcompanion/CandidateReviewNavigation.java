package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;

/** Pure screen-space navigation calculations for candidate review. */
final class CandidateReviewNavigation {
    private static final int MINIMUM_PADDING = 45;
    private static final double DEFAULT_CONTEXT_FACTOR = 4.0;
    private static final double STEP_FACTOR = 1.25;

    private CandidateReviewNavigation() {
    }

    static Rectangle reviewRectangle(Rectangle candidate, Rectangle view) {
        int padding = Math.max(MINIMUM_PADDING,
                Math.max(candidate.width, candidate.height) * 2);
        Rectangle expanded = new Rectangle(candidate.x - padding, candidate.y - padding,
                candidate.width + padding * 2, candidate.height + padding * 2);
        Rectangle clipped = expanded.intersection(view);
        if (clipped.width < 1 || clipped.height < 1) {
            throw new IllegalArgumentException("The candidate is outside the current map view.");
        }
        return clipped;
    }

    /**
     * Frames a candidate at a consistent visual size regardless of the zoom used
     * for the original scan. The longest candidate dimension occupies roughly
     * one {@code contextFactor} of the map view's shorter screen dimension.
     */
    static ProjectionBounds adaptiveReviewBounds(ProjectionBounds candidate,
            int viewWidth, int viewHeight, int zoomStep) {
        if (candidate == null || viewWidth < 1 || viewHeight < 1) {
            throw new IllegalArgumentException("A valid candidate and map view are required.");
        }
        EastNorth min = candidate.getMin();
        EastNorth max = candidate.getMax();
        double candidateWidth = Math.abs(max.east() - min.east());
        double candidateHeight = Math.abs(max.north() - min.north());
        double longest = Math.max(candidateWidth, candidateHeight);
        if (!Double.isFinite(longest) || longest <= 0) {
            throw new IllegalArgumentException("The candidate area is too small to frame.");
        }

        double contextFactor = DEFAULT_CONTEXT_FACTOR * Math.pow(STEP_FACTOR, zoomStep);
        double unitsPerPixel = longest * contextFactor / Math.min(viewWidth, viewHeight);
        double framedWidth = unitsPerPixel * viewWidth;
        double framedHeight = unitsPerPixel * viewHeight;
        double centreEast = (min.east() + max.east()) / 2.0;
        double centreNorth = (min.north() + max.north()) / 2.0;
        return new ProjectionBounds(centreEast - framedWidth / 2.0,
                centreNorth - framedHeight / 2.0,
                centreEast + framedWidth / 2.0,
                centreNorth + framedHeight / 2.0);
    }

    static int zoomPercentage(int zoomStep) {
        return (int) Math.round(100.0 / Math.pow(STEP_FACTOR, zoomStep));
    }

    static int anchoredViewY(int currentViewY, int currentAnchorScreenY,
            int desiredAnchorScreenY, int viewHeight, int extentHeight) {
        int requested = currentViewY + currentAnchorScreenY - desiredAnchorScreenY;
        return Math.max(0, Math.min(requested, Math.max(0, viewHeight - extentHeight)));
    }
}
