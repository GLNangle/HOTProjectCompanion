package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Read-only client for public HOT Tasking Manager project and task data. */
final class HotTaskingManagerClient {
    private static final String API_ROOT =
            "https://tasking-manager-tm4-production-api.hotosm.org/api/v2/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    TaskContext load(TaskReference reference) throws IOException, InterruptedException {
        String projectPath = "projects/" + reference.getProjectId() + "/";
        String taskPath = projectPath + "tasks/" + reference.getTaskId() + "/";
        String projectJson = get(projectPath);
        String taskJson = get(taskPath);
        return TaskContextParser.parse(projectJson, taskJson);
    }

    String loadTaskStatus(TaskReference reference) throws IOException, InterruptedException {
        String taskJson = get("projects/" + reference.getProjectId() + "/tasks/"
                + reference.getTaskId() + "/");
        Object parsed = MiniJson.parse(taskJson);
        if (!(parsed instanceof java.util.Map)) {
            throw new IOException("Unexpected Tasking Manager task response");
        }
        java.util.Map<?, ?> task = (java.util.Map<?, ?>) parsed;
        Object value = task.containsKey("taskStatus") ? task.get("taskStatus")
                : task.get("task_status");
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IOException("Tasking Manager response did not include a task status");
        }
        return value.toString().trim();
    }

    private String get(String relativePath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_ROOT + relativePath))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("Accept-Language", "en")
                .header("User-Agent", "JOSM-HOT-Project-Companion/1.0.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Tasking Manager returned HTTP " + response.statusCode());
        }
        return response.body();
    }
}
