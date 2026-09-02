package org.openstreetmap.josm.plugins.hotprojectcompanion;

final class TaskStatusTransitionTest {
    private TaskStatusTransitionTest() {
    }

    static void run() {
        LearningStore.TaskRecord initial = new LearningStore.TaskRecord(58879, 168);
        TaskStatusTransition baseline = TaskStatusTransition.between(initial, "MAPPED");
        require(baseline.getKind() == TaskStatusTransition.Kind.INITIAL_STATUS,
                "a first public status must not be called a validation outcome");

        LearningStore.TaskRecord mapped = new LearningStore.TaskRecord(58879, 169);
        setStatus(mapped, "MAPPED");
        TaskStatusTransition validated = TaskStatusTransition.between(mapped, "VALIDATED");
        require(validated.getKind() == TaskStatusTransition.Kind.VALIDATION_OUTCOME,
                "validated must be recognised as a validation outcome");

        TaskStatusTransition locked = TaskStatusTransition.between(mapped,
                "LOCKED_FOR_VALIDATION");
        require(locked.getKind() == TaskStatusTransition.Kind.STATUS_CHANGE,
                "a lock change must remain an ordinary task status change");

        TaskStatusTransition unchanged = TaskStatusTransition.between(mapped, "mapped");
        require(unchanged.getKind() == TaskStatusTransition.Kind.UNCHANGED,
                "case differences must not create a change");
        require("Locked for validation".equals(
                TaskStatusTransition.display("LOCKED_FOR_VALIDATION")),
                "raw API statuses should be readable in the UI");
    }

    private static void setStatus(LearningStore.TaskRecord record, String status) {
        try {
            java.lang.reflect.Field field = LearningStore.TaskRecord.class
                    .getDeclaredField("status");
            field.setAccessible(true);
            field.set(record, status);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
