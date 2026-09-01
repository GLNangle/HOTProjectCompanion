package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** Dependency-free tests runnable with plain Java assertions. */
public final class TaskLayerNameParserTest {
    private TaskLayerNameParserTest() {
    }

    public static void main(String[] args) {
        TaskReference reference = TaskLayerNameParser.parse(
                "Boundary for task: 168 of TM Project #58879 – Do not edit or upload");
        assertReference(reference, 58879, 168);

        reference = TaskLayerNameParser.parse(
                "Boundary   for task: 4 of TM project #123 - Do not edit or upload");
        assertReference(reference, 123, 4);

        if (TaskLayerNameParser.parse("Esri World Imagery") != null) {
            throw new AssertionError("Unrelated layer must not be recognised");
        }
        if (TaskLayerNameParser.parse(null) != null) {
            throw new AssertionError("Null layer name must not be recognised");
        }
    }

    private static void assertReference(TaskReference reference, long projectId, long taskId) {
        if (reference == null || reference.getProjectId() != projectId || reference.getTaskId() != taskId) {
            throw new AssertionError("Unexpected parsed task reference");
        }
    }
}
