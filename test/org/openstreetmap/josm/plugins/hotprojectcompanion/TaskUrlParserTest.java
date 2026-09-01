package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** Small dependency-free test runner for the URL parser. */
public final class TaskUrlParserTest {
    private TaskUrlParserTest() {
    }

    public static void main(String[] args) {
        acceptsPathTask();
        acceptsQueryTask();
        acceptsStagingTask();
        rejectsOtherHosts();
        rejectsProjectOnlyUrl();
        System.out.println("TaskUrlParserTest: all tests passed");
    }

    private static void acceptsPathTask() {
        TaskReference result = TaskUrlParser.parse("https://tasks.hotosm.org/projects/12345/tasks/67");
        check(result.getProjectId() == 12345, "project ID from path");
        check(result.getTaskId() == 67, "task ID from path");
    }

    private static void acceptsQueryTask() {
        TaskReference result = TaskUrlParser.parse("https://tasks.hotosm.org/projects/12345/tasks/?task=67");
        check(result.getProjectId() == 12345, "project ID from query URL");
        check(result.getTaskId() == 67, "task ID from query");
    }

    private static void acceptsStagingTask() {
        TaskReference result = TaskUrlParser.parse("https://tasks-stage.hotosm.org/projects/5/tasks/9");
        check(result.getProjectId() == 5 && result.getTaskId() == 9, "staging URL");
    }

    private static void rejectsOtherHosts() {
        expectFailure("https://example.org/projects/1/tasks/2");
    }

    private static void rejectsProjectOnlyUrl() {
        expectFailure("https://tasks.hotosm.org/projects/1");
    }

    private static void expectFailure(String url) {
        try {
            TaskUrlParser.parse(url);
            throw new AssertionError("Expected URL to be rejected: " + url);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}
