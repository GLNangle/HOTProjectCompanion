package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTPS client for the opt-in, anonymous shared-learning service. */
final class SharedLearningClient {
    static final String SERVICE_ROOT =
            "https://hotprojectcompanionlearningservice.julietmoon-nightofthemoon.workers.dev/v1/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    Map<String, String> submit(List<SharedLearningStore.Example> examples,
            String installationId, String withdrawalToken)
            throws IOException, InterruptedException {
        if (examples == null || examples.isEmpty()) {
            return new LinkedHashMap<>();
        }
        String body = requestBody(examples, installationId, withdrawalToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVICE_ROOT + "contributions"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "JOSM-HOT-Project-Companion/1.0.3")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202) {
            throw new IOException("Shared-learning service returned HTTP "
                    + response.statusCode());
        }
        return receipts(response.body(), examples);
    }

    String fetchProfile() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SERVICE_ROOT + "profile"))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "JOSM-HOT-Project-Companion/1.0.3")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Shared-learning profile returned HTTP "
                    + response.statusCode());
        }
        SharedLearningProfile parsed = SharedLearningProfile.parse(response.body());
        if ("unavailable".equals(parsed.getStatus())) {
            throw new IOException("Shared-learning profile response was not recognised");
        }
        return response.body();
    }

    void withdraw(String serviceId, String withdrawalToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(SERVICE_ROOT + "contributions/" + serviceId))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("X-Withdrawal-Token", withdrawalToken)
                .header("User-Agent", "JOSM-HOT-Project-Companion/1.0.3")
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Shared-learning withdrawal returned HTTP "
                    + response.statusCode());
        }
    }

    static String requestBody(List<SharedLearningStore.Example> examples,
            String installationId, String withdrawalToken) {
        StringBuilder json = new StringBuilder();
        json.append("{\"installationId\":\"").append(escape(installationId))
                .append("\",\"withdrawalToken\":\"").append(escape(withdrawalToken))
                .append("\",\"items\":[");
        for (int index = 0; index < examples.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            SharedLearningStore.Example example = examples.get(index);
            double[] feature = example.getFeatures();
            EnumSet<GeometryEditOutcome> edits = example.getEdits();
            json.append("{\"clientEventId\":\"").append(escape(example.getEventId()))
                    .append("\",\"projectId\":").append(example.getProjectId())
                    .append(",\"taskId\":").append(example.getTaskId())
                    .append(",\"attemptStartedAt\":\"")
                    .append(Instant.ofEpochSecond(example.getAttemptEpoch()))
                    .append("\",\"imageryKey\":\"").append(escape(example.getImageryKey()))
                    .append("\",\"decision\":\"").append(escape(example.getDecision()))
                    .append("\",\"shape\":\"").append(escape(example.getShape()))
                    .append("\",\"evidence\":{")
                    .append("\"consistency\":").append(feature[0]).append(',')
                    .append("\"contrast\":").append(feature[1]).append(',')
                    .append("\"boundary\":").append(feature[2]).append(',')
                    .append("\"shadow\":").append(feature[3]).append(',')
                    .append("\"geometry\":").append(feature[4]).append("},\"edits\":{")
                    .append("\"moved\":").append(edits.contains(GeometryEditOutcome.MOVED))
                    .append(",\"rotated\":").append(edits.contains(GeometryEditOutcome.ROTATED))
                    .append(",\"reshaped\":").append(edits.contains(GeometryEditOutcome.RESHAPED))
                    .append(",\"resized\":").append(edits.contains(GeometryEditOutcome.RESIZED))
                    .append("}}");
        }
        return json.append("]}").toString();
    }

    private static Map<String, String> receipts(String body,
            List<SharedLearningStore.Example> submitted) throws IOException {
        try {
            Object parsed = MiniJson.parse(body);
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("Expected object");
            }
            Object acceptedValue = ((Map<?, ?>) parsed).get("accepted");
            if (!(acceptedValue instanceof List)) {
                throw new IllegalArgumentException("Expected accepted list");
            }
            List<?> accepted = (List<?>) acceptedValue;
            if (accepted.size() != submitted.size()) {
                throw new IllegalArgumentException("Receipt count mismatch");
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < accepted.size(); index++) {
                Object value = accepted.get(index);
                if (!(value instanceof Map)) {
                    throw new IllegalArgumentException("Expected receipt");
                }
                Object id = ((Map<?, ?>) value).get("id");
                Object status = ((Map<?, ?>) value).get("status");
                String serviceId = id == null ? "" : id.toString();
                if (!serviceId.matches("[a-f0-9]{40}") || !"pending".equals(status)) {
                    throw new IllegalArgumentException("Invalid receipt");
                }
                result.put(submitted.get(index).getEventId(), serviceId);
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Shared-learning service returned an unexpected receipt",
                    exception);
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        String text = value == null ? "" : value;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '\\': escaped.append("\\\\"); break;
                case '"': escaped.append("\\\""); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }
}
