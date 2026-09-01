package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** In-memory mapper decisions for one reconnaissance result. */
final class CandidateReviewDecisions {
    enum Decision {
        UNREVIEWED,
        ACCEPTED,
        REJECTED,
        MAPPED
    }

    private final Decision[] decisions;

    CandidateReviewDecisions(int candidateCount) {
        if (candidateCount < 0) {
            throw new IllegalArgumentException("Candidate count cannot be negative.");
        }
        decisions = new Decision[candidateCount];
        for (int index = 0; index < decisions.length; index++) {
            decisions[index] = Decision.UNREVIEWED;
        }
    }

    int size() {
        return decisions.length;
    }

    Decision get(int candidateNumber) {
        return decisions[index(candidateNumber)];
    }

    void set(int candidateNumber, Decision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("Decision cannot be null.");
        }
        decisions[index(candidateNumber)] = decision;
    }

    int count(Decision decision) {
        int total = 0;
        for (Decision candidateDecision : decisions) {
            if (candidateDecision == decision) {
                total++;
            }
        }
        return total;
    }

    private int index(int candidateNumber) {
        if (candidateNumber < 1 || candidateNumber > decisions.length) {
            throw new IllegalArgumentException("Candidate number is outside the review list.");
        }
        return candidateNumber - 1;
    }
}
