package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** Dependency-free tests for reconnaissance review decisions. */
public final class CandidateReviewDecisionsTest {
    private CandidateReviewDecisionsTest() {
    }

    public static void main(String[] args) {
        CandidateReviewDecisions decisions = new CandidateReviewDecisions(3);
        require(decisions.count(CandidateReviewDecisions.Decision.UNREVIEWED) == 3,
                "All candidates should begin unreviewed");

        decisions.set(1, CandidateReviewDecisions.Decision.ACCEPTED);
        decisions.set(2, CandidateReviewDecisions.Decision.REJECTED);
        require(decisions.count(CandidateReviewDecisions.Decision.ACCEPTED) == 1,
                "One candidate should be accepted");
        require(decisions.count(CandidateReviewDecisions.Decision.REJECTED) == 1,
                "One candidate should be rejected");
        require(decisions.count(CandidateReviewDecisions.Decision.UNREVIEWED) == 1,
                "One candidate should remain unreviewed");

        decisions.set(1, CandidateReviewDecisions.Decision.REJECTED);
        require(decisions.count(CandidateReviewDecisions.Decision.ACCEPTED) == 0,
                "Changing a decision should replace the previous decision");
        require(decisions.count(CandidateReviewDecisions.Decision.REJECTED) == 2,
                "Changed decision should be counted as rejected");
        require(decisions.get(3) == CandidateReviewDecisions.Decision.UNREVIEWED,
                "Unchanged candidate should remain unreviewed");

        decisions.set(3, CandidateReviewDecisions.Decision.MAPPED);
        require(decisions.count(CandidateReviewDecisions.Decision.MAPPED) == 1,
                "A confirmed mapped candidate should be counted separately");

        decisions.set(2, CandidateReviewDecisions.Decision.OUTSIDE_AREA);
        require(decisions.count(CandidateReviewDecisions.Decision.OUTSIDE_AREA) == 1,
                "An outside-area candidate should have a neutral decision");
        require(decisions.count(CandidateReviewDecisions.Decision.REJECTED) == 1,
                "An outside-area candidate must not remain counted as rejected");

        System.out.println("CandidateReviewDecisionsTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
