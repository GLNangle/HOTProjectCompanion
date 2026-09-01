package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;

/** Pure screen-space navigation calculations for candidate review. */
final class CandidateReviewNavigation {
    private static final int MINIMUM_PADDING = 45;

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

    static int anchoredViewY(int currentViewY, int currentAnchorScreenY,
            int desiredAnchorScreenY, int viewHeight, int extentHeight) {
        int requested = currentViewY + currentAnchorScreenY - desiredAnchorScreenY;
        return Math.max(0, Math.min(requested, Math.max(0, viewHeight - extentHeight)));
    }
}
