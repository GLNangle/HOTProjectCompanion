package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** Dependency-free tests for Tasking Manager response parsing. */
public final class TaskContextParserTest {
    private TaskContextParserTest() {
    }

    public static void main(String[] args) {
        String project = "{"
                + "\"projectName\":\"Example project\","
                + "\"mappingTypes\":[\"BUILDINGS\",\"ROADS\"],"
                + "\"imagery\":\"Esri World Imagery\","
                + "\"changesetComment\":\"Mapping #hotosm-project-58879 #example\","
                + "\"projectInfo\":{\"instructions\":\"Map all buildings. Imagery offset: move Esri 2 m east.\"}"
                + "}";
        String task = "{"
                + "\"taskStatus\":\"READY\","
                + "\"perTaskInstructions\":\"Check the western edge.\","
                + "\"taskHistory\":["
                + "{\"action\":\"COMMENT\",\"actionText\":\"Several buildings were missed\","
                + "\"actionBy\":\"validator\",\"actionDate\":\"2026-08-30T10:00:00\"},"
                + "{\"action\":\"STATE_CHANGE\",\"actionText\":\"INVALIDATED\","
                + "\"actionBy\":\"validator\",\"actionDate\":\"2026-08-30T09:59:00\"}"
                + "]}";

        TaskContext context = TaskContextParser.parse(project, task);
        requireContains(context.getWhatToMap(), "Map all buildings");
        requireContains(context.getWhatToMap(), "Check the western edge");
        requireContains(context.getImageryGuidance(), "Esri World Imagery");
        requireContains(context.getImageryGuidance(), "Imagery offset");
        requireContains(context.getPreviousFeedback(), "PREVIOUSLY BEEN INVALIDATED");
        requireContains(context.getPreviousFeedback(), "Several buildings were missed");
        requireContains(context.getUploadDetails(), "#hotosm-project-58879 #example");

        String projectWithImages = "{"
                + "\"projectName\":\"Image project\","
                + "\"projectInfo\":{\"instructions\":\"Look for this pattern "
                + "<img alt='Building example' src='https://images.example.org/building.jpg'> "
                + "and ![Road example](https://images.example.org/road.png). "
                + "Ignore ![unsafe](http://localhost/private.png).\"}"
                + "}";
        TaskContext imageContext = TaskContextParser.parse(projectWithImages,
                "{\"taskStatus\":\"READY\",\"perTaskInstructions\":\""
                + "<img src='/media/task-tip.png' title='Task tip'>\"}");
        if (imageContext.getInstructionImages().size() != 3) {
            throw new AssertionError("Expected three supported instruction images");
        }
        requireEquals("Building example", imageContext.getInstructionImages().get(0).getDescription());
        requireEquals("https://images.example.org/building.jpg",
                imageContext.getInstructionImages().get(0).getUrl());
        requireEquals("Road example", imageContext.getInstructionImages().get(1).getDescription());
        requireEquals("https://tasks.hotosm.org/media/task-tip.png",
                imageContext.getInstructionImages().get(2).getUrl());
        if (!InstructionImageLoader.isSupportedUrl("https://images.example.org/example.png")
                || InstructionImageLoader.isSupportedUrl("http://images.example.org/example.png")
                || InstructionImageLoader.isSupportedUrl("https://localhost/example.png")
                || InstructionImageLoader.isSupportedUrl("https://127.0.0.1/example.png")
                || InstructionImageLoader.isSupportedUrl("https://192.168.1.5/example.png")
                || InstructionImageLoader.isSupportedUrl("https://172.20.1.5/example.png")) {
            throw new AssertionError("Instruction image URL restrictions are incorrect");
        }

        String splitTask = "{\"taskStatus\":\"READY\",\"taskHistory\":["
                + "{\"action\":\"STATE_CHANGE\",\"actionText\":\"SPLIT\",\"actionBy\":\"mapper\"}]}";
        TaskContext splitContext = TaskContextParser.parse(project, splitTask);
        if (!splitContext.isSplitTask()) {
            throw new AssertionError("Split task marker was not detected");
        }
        if (splitContext.hasDetailedFeedback()) {
            throw new AssertionError("Split marker alone must not count as detailed feedback");
        }
        String inherited = SplitFeedbackCache.appendInherited(splitContext.getPreviousFeedback(),
                new SplitFeedbackCache.Entry(168, "validator\nSeveral buildings were missed"));
        requireContains(inherited, "INHERITED FROM TASK 168");
        requireContains(inherited, "Several buildings were missed");

        Object parsed = MiniJson.parse("{\"escaped\":\"line\\n\\u263a\",\"number\":12,\"ok\":true}");
        if (!(parsed instanceof java.util.Map<?, ?>)) {
            throw new AssertionError("JSON object was not parsed");
        }
    }

    private static void requireContains(String actual, String expected) {
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected text not found: " + expected + "\nActual: " + actual);
        }
    }

    private static void requireEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + "\nActual: " + actual);
        }
    }
}
