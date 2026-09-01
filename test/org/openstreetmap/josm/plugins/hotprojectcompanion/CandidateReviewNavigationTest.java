package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.Rectangle;

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

        int anchored = CandidateReviewNavigation.anchoredViewY(
                500, 120, 300, 2400, 800);
        check(anchored == 320,
                "scrolling upward by a removed row's height should keep the next row stationary");

        int clamped = CandidateReviewNavigation.anchoredViewY(
                40, 20, 300, 2400, 800);
        check(clamped == 0, "anchored scrolling should stop at the top of the panel");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
