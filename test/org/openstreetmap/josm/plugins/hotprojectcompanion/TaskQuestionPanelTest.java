package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.awt.event.FocusEvent;

/** Regression checks for the task-question typing focus policy. */
public final class TaskQuestionPanelTest {
    private TaskQuestionPanelTest() {
    }

    public static void main(String[] args) {
        long keyTime = 1_000_000_000L;
        require(TaskQuestionPanel.shouldRecoverFocus(keyTime, keyTime + 50_000_000L,
                FocusEvent.Cause.UNKNOWN), "unexpected shortcut focus loss should be recovered");
        require(TaskQuestionPanel.shouldRecoverFocus(keyTime, keyTime + 50_000_000L,
                FocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER),
                "cleared focus during typing should be recovered");
        require(!TaskQuestionPanel.shouldRecoverFocus(keyTime, keyTime + 50_000_000L,
                FocusEvent.Cause.MOUSE_EVENT), "a deliberate click must retain focus");
        require(!TaskQuestionPanel.shouldRecoverFocus(keyTime, keyTime + 50_000_000L,
                FocusEvent.Cause.TRAVERSAL_FORWARD), "Tab traversal must retain focus");
        require(!TaskQuestionPanel.shouldRecoverFocus(keyTime, keyTime + 900_000_000L,
                FocusEvent.Cause.UNKNOWN), "stale focus changes must not be recovered");
        require(TaskQuestionPanel.safeCaretPosition(4, 8) == 4,
                "a valid caret position must be preserved");
        require(TaskQuestionPanel.safeCaretPosition(12, 8) == 8,
                "a caret beyond changed text must move to its end");
        require(TaskQuestionPanel.safeCaretPosition(-2, 8) == 0,
                "a negative caret position must be clamped");
        System.out.println("TaskQuestionPanelTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
