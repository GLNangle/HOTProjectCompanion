package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.Collections;

/** Regression checks for cautious task-question matching. */
public final class TaskQuestionAnswererTest {
    private TaskQuestionAnswererTest() {
    }

    public static void main(String[] args) {
        TaskContext allowed = context(
                "Map all visible buildings. Map buildings under construction as building=construction.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        TaskQuestionAnswerer.Answer allowedAnswer = TaskQuestionAnswerer.answer(allowed,
                "Should I map buildings under construction?");
        requireOutcome(TaskQuestionAnswerer.Outcome.YES, allowedAnswer);
        requireEquals("Yes. Map buildings under construction as building=construction.",
                allowedAnswer.getSummary());
        requireEquals(1, allowedAnswer.getEvidence().size());

        TaskContext excluded = context(
                "Map completed buildings. Do not map buildings under construction.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        TaskQuestionAnswerer.Answer excludedAnswer = TaskQuestionAnswerer.answer(excluded,
                "Should I map buildings under construction?");
        requireOutcome(TaskQuestionAnswerer.Outcome.NO, excludedAnswer);
        requireEquals("No. Do not map buildings under construction.",
                excludedAnswer.getSummary());

        TaskContext mixedClause = context(
                "Do not map temporary roads, but map buildings under construction as building=construction.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        TaskQuestionAnswerer.Answer mixedAnswer = TaskQuestionAnswerer.answer(mixedClause,
                "Should I map buildings under construction?");
        requireOutcome(TaskQuestionAnswerer.Outcome.YES, mixedAnswer);
        requireEquals("Yes. Map buildings under construction as building=construction.",
                mixedAnswer.getSummary());

        TaskContext unclear = context(
                "Map all visible buildings.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        TaskQuestionAnswerer.Answer unclearAnswer = TaskQuestionAnswerer.answer(unclear,
                "Should I map buildings under construction?");
        requireOutcome(TaskQuestionAnswerer.Outcome.NOT_FOUND, unclearAnswer);
        requireEquals("Not specified in the loaded task guidance.", unclearAnswer.getSummary());

        TaskQuestionAnswerer.Answer imagery = TaskQuestionAnswerer.answer(unclear,
                "What imagery should I use?");
        requireOutcome(TaskQuestionAnswerer.Outcome.RELATED, imagery);
        requireEquals("Authorised imagery: Esri World Imagery", imagery.getSummary());
        if (imagery.getEvidence().isEmpty()
                || !imagery.getEvidence().get(0).getText().contains("Esri World Imagery")) {
            throw new AssertionError("Expected authorised imagery evidence");
        }

        TaskContext overview = context(
                "Map all visible buildings. Do not map roads. Check the western edge carefully.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        TaskQuestionAnswerer.Answer overviewAnswer = TaskQuestionAnswerer.answer(overview,
                "What am I mapping?");
        requireOutcome(TaskQuestionAnswerer.Outcome.RELATED, overviewAnswer);
        requireEquals("Map all visible buildings. Do not map roads.",
                overviewAnswer.getSummary());
        requireEquals(1, overviewAnswer.getEvidence().size());

        TaskQuestionAnswerer.Answer alternateOverview = TaskQuestionAnswerer.answer(overview,
                "What do I need to map?");
        requireEquals(overviewAnswer.getSummary(), alternateOverview.getSummary());

        String[] accessibleOverviewQuestions = {
            "What I need map?",
            "Which things are mapped?",
            "Mapping target?",
            "Tell me the mapping instructions",
            "Wat am I maping?",
            "What should be mapped here?",
            "What do I do here?",
            "What this task wants?"
        };
        for (String accessibleQuestion : accessibleOverviewQuestions) {
            TaskQuestionAnswerer.Answer accessibleAnswer = TaskQuestionAnswerer.answer(
                    overview, accessibleQuestion);
            requireOutcome(TaskQuestionAnswerer.Outcome.RELATED, accessibleAnswer);
            requireEquals(overviewAnswer.getSummary(), accessibleAnswer.getSummary());
        }

        TaskQuestionAnswerer.Answer specificImagery = TaskQuestionAnswerer.answer(overview,
                "What imagery am I mapping with?");
        requireEquals("Authorised imagery: Esri World Imagery", specificImagery.getSummary());
        System.out.println("TaskQuestionAnswererTest: all tests passed");
    }

    private static TaskContext context(String instructions, String imagery, String feedback) {
        return new TaskContext(instructions, imagery, feedback, "", "", false, false,
                Collections.emptyList(), "Esri World Imagery");
    }

    private static void requireOutcome(TaskQuestionAnswerer.Outcome expected,
            TaskQuestionAnswerer.Answer actual) {
        if (expected != actual.getOutcome()) {
            throw new AssertionError("Expected " + expected + " but got "
                    + actual.getOutcome() + ": " + actual.getSummary());
        }
    }

    private static void requireEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
