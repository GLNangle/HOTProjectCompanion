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
        requireOutcome(TaskQuestionAnswerer.Outcome.YES,
                TaskQuestionAnswerer.answer(allowed,
                        "Should I map buildings under construction?"));

        TaskContext excluded = context(
                "Map completed buildings. Do not map buildings under construction.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        requireOutcome(TaskQuestionAnswerer.Outcome.NO,
                TaskQuestionAnswerer.answer(excluded,
                        "Should I map buildings under construction?"));

        TaskContext unclear = context(
                "Map all visible buildings.",
                "Authorised imagery: Esri World Imagery",
                "No previous task comments were returned.");
        requireOutcome(TaskQuestionAnswerer.Outcome.NOT_FOUND,
                TaskQuestionAnswerer.answer(unclear,
                        "Should I map buildings under construction?"));

        TaskQuestionAnswerer.Answer imagery = TaskQuestionAnswerer.answer(unclear,
                "What imagery should I use?");
        requireOutcome(TaskQuestionAnswerer.Outcome.RELATED, imagery);
        if (imagery.getEvidence().isEmpty()
                || !imagery.getEvidence().get(0).getText().contains("Esri World Imagery")) {
            throw new AssertionError("Expected authorised imagery evidence");
        }
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
}
