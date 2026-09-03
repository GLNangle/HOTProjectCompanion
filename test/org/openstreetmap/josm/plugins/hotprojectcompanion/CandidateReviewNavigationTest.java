package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;

import org.openstreetmap.josm.data.ProjectionBounds;

final class CandidateReviewNavigationTest {
    private CandidateReviewNavigationTest() {
    }

    static void main(String[] args) {
        Rectangle small = CandidateReviewNavigation.reviewRectangle(
                new Rectangle(400, 300, 12, 10), new Rectangle(0, 0, 1000, 800));
        check(small.width >= 100 && small.height >= 100,
                "a small candidate should receive a useful close-up area");

        Rectangle edge = CandidateReviewNavigation.reviewRectangle(
                new Rectangle(2, 3, 20, 18), new Rectangle(0, 0, 500, 400));
        check(edge.x == 0 && edge.y == 0,
                "review bounds should be clipped at the map edge");
        check(new Rectangle(0, 0, 500, 400).contains(edge),
                "review bounds must remain inside the current map view");

        ProjectionBounds adaptive = CandidateReviewNavigation.adaptiveReviewBounds(
                new ProjectionBounds(100, 200, 120, 210), 1000, 500, 0);
        check(close(adaptive.getMin().east(), 30)
                        && close(adaptive.getMax().east(), 190),
                "adaptive review should honour the map aspect ratio");
        check(close(adaptive.getMin().north(), 165)
                        && close(adaptive.getMax().north(), 245),
                "adaptive review should remain centred on the candidate");

        ProjectionBounds closer = CandidateReviewNavigation.adaptiveReviewBounds(
                new ProjectionBounds(100, 200, 120, 210), 1000, 500, -1);
        check((closer.getMax().east() - closer.getMin().east())
                        < (adaptive.getMax().east() - adaptive.getMin().east()),
                "closer review should show a smaller surrounding area");
        check(CandidateReviewNavigation.zoomPercentage(-1) == 125,
                "closer step should report a useful zoom percentage");
        check(CandidateReviewNavigation.zoomPercentage(1) == 80,
                "wider step should report a useful zoom percentage");

        int anchored = CandidateReviewNavigation.anchoredViewY(
                500, 120, 300, 2400, 800);
        check(anchored == 320,
                "scrolling upward by a removed row's height should keep the next row stationary");

        int clamped = CandidateReviewNavigation.anchoredViewY(
                40, 20, 300, 2400, 800);
        check(clamped == 0, "anchored scrolling should stop at the top of the panel");

        ProjectionBounds scanArea = new ProjectionBounds(100, 200, 500, 600);
        Rectangle originalPixels = new Rectangle(80, 50, 120, 90);
        ProjectionBounds storedArea = TaskReconnaissancePanel.projectionBoundsFor(
                originalPixels, scanArea, 400, 300);
        Rectangle restoredPixels = TaskReconnaissancePanel.pixelBoundsFor(
                storedArea, scanArea, 400, 300);
        check(Math.abs(restoredPixels.x - originalPixels.x) <= 1
                        && Math.abs(restoredPixels.y - originalPixels.y) <= 1
                        && Math.abs(restoredPixels.width - originalPixels.width) <= 1
                        && Math.abs(restoredPixels.height - originalPixels.height) <= 1,
                "reviewed locations should survive rescans independently of the live viewport");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001;
    }
}
