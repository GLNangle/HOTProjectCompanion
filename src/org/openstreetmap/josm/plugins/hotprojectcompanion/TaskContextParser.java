package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Tasking Manager JSON into concise mapper-facing guidance. */
final class TaskContextParser {
    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_-]+");
    private static final Pattern HTML_IMAGE = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern HTML_ATTRIBUTE = Pattern.compile(
            "(?is)([\\w:-]+)\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)\\]\\((\\S+?)(?:\\s+[\\\"']([^\\\"']*)[\\\"'])?\\)");
    private static final int MAX_INSTRUCTION_IMAGES = 6;

    private TaskContextParser() {
    }

    static TaskContext parse(String projectJson, String taskJson) {
        Map<String, Object> project = asMap(MiniJson.parse(projectJson));
        Map<String, Object> task = asMap(MiniJson.parse(taskJson));
        if (project == null || task == null) {
            throw new IllegalArgumentException("Unexpected Tasking Manager response");
        }

        Object projectInfoValue = value(project, "projectInfo", "project_info");
        Map<String, Object> projectInfo = asMap(projectInfoValue);
        if (projectInfo == null) {
            List<Object> locales = asList(projectInfoValue);
            projectInfo = locales.isEmpty() ? Collections.emptyMap() : asMap(locales.get(0));
        }
        if (projectInfo == null) {
            projectInfo = Collections.emptyMap();
        }

        String projectName = firstText(project, "projectName", "project_name", "name");
        if (isBlank(projectName)) {
            projectName = firstText(projectInfo, "name", "projectName", "project_name");
        }
        String instructions = firstText(projectInfo, "instructions", "description");
        if (isBlank(instructions)) {
            instructions = firstText(project, "instructions", "description");
        }
        String perTask = firstText(task, "perTaskInstructions", "per_task_instructions");
        String imagery = firstText(project, "imagery", "imageryUrl", "imagery_url");
        String changeset = firstText(project, "changesetComment", "changeset_comment");
        String changesetSource = firstText(project, "changesetSource", "changeset_source", "source");
        String mappingTypes = displayValue(value(project, "mappingTypes", "mapping_types"));

        String whatToMap = buildWhatToMap(projectName, mappingTypes, instructions, perTask);
        String imageryGuidance = buildImageryGuidance(imagery, instructions, perTask);
        FeedbackResult feedback = buildFeedback(task);
        String upload = buildUploadDetails(changeset, changesetSource, imagery);
        List<InstructionImage> instructionImages = extractInstructionImages(instructions, perTask);
        return new TaskContext(whatToMap, imageryGuidance, feedback.displayText, upload,
                feedback.inheritableText, feedback.splitTask, feedback.hasDetailedFeedback,
                instructionImages, clean(imagery));
    }

    private static String buildWhatToMap(String projectName, String mappingTypes, String instructions,
            String perTask) {
        StringBuilder text = new StringBuilder();
        appendLabel(text, "Project", clean(projectName));
        appendLabel(text, "Mapping types", clean(mappingTypes));
        if (!isBlank(instructions)) {
            appendBlock(text, clean(instructions));
        } else {
            appendBlock(text, "No general mapping instructions were returned. Open the project page before mapping.");
        }
        if (!isBlank(perTask)) {
            appendBlock(text, "TASK-SPECIFIC INSTRUCTIONS\n" + clean(perTask));
        }
        return text.toString().trim();
    }

    private static String buildImageryGuidance(String imagery, String instructions, String perTask) {
        String combinedInstructions = clean(instructions) + "\n" + clean(perTask);
        List<String> alignmentNotes = extractAlignmentNotes(combinedInstructions);
        StringBuilder text = new StringBuilder();
        appendLabel(text, "Authorised imagery", isBlank(imagery) ? "Not explicitly named in the API response" : clean(imagery));
        appendBlock(text, "IMPORTANT: Align only using imagery explicitly authorised by this project.");
        if (!alignmentNotes.isEmpty()) {
            appendBlock(text, "PROJECT OFFSET / ALIGNMENT NOTE\n" + String.join("\n", alignmentNotes));
            appendBlock(text, "Follow the project's instruction above. Never align against an unapproved imagery layer.");
        } else {
            appendBlock(text, "No explicit offset instruction was found. If the authorised imagery is displaced against reliable existing OSM features, adjust the imagery layer. Do not move mapped features merely to make them fit the imagery; move features only when the evidence shows their geometry is wrong.");
        }
        return text.toString().trim();
    }

    private static FeedbackResult buildFeedback(Map<String, Object> task) {
        List<Object> history = asList(value(task, "taskHistory", "task_history"));
        List<String> entries = new ArrayList<>();
        boolean invalidated = false;
        boolean splitTask = false;
        boolean hasDetailedFeedback = false;
        for (Object item : history) {
            Map<String, Object> event = asMap(item);
            if (event == null) {
                continue;
            }
            String action = firstText(event, "action");
            String actionText = firstText(event, "actionText", "action_text");
            String author = firstText(event, "actionBy", "action_by", "username", "userName");
            String date = firstText(event, "actionDate", "action_date");
            String state = (action + " " + actionText).toUpperCase(Locale.ROOT);
            if (state.contains("SPLIT")) {
                splitTask = true;
            }
            if (state.contains("INVALIDAT")) {
                invalidated = true;
                entries.add(eventHeading(author, date) + "Task was invalidated" + optionalText(actionText));
            } else if ("COMMENT".equalsIgnoreCase(action) && !isBlank(actionText)) {
                entries.add(eventHeading(author, date) + clean(actionText));
                hasDetailedFeedback = true;
            }

            List<Object> issues = asList(value(event, "issues", "mappingIssues", "mapping_issues"));
            for (Object issueValue : issues) {
                Map<String, Object> issue = asMap(issueValue);
                if (issue != null) {
                    String name = firstText(issue, "name", "issue");
                    String count = firstText(issue, "count");
                    if (!isBlank(name)) {
                        invalidated = true;
                        hasDetailedFeedback = true;
                        entries.add(eventHeading(author, date) + "Mapping issue: " + clean(name)
                                + (isBlank(count) ? "" : " (" + count + ")"));
                    }
                }
            }
        }

        StringBuilder text = new StringBuilder();
        String currentStatus = firstText(task, "taskStatus", "task_status");
        appendLabel(text, "Current task status", clean(currentStatus));
        if (splitTask) {
            appendBlock(text, "SPLIT TASK: This task was created from a larger task. Earlier feedback may refer to the original, larger boundary; apply only the parts relevant inside this child task.");
        }
        if (invalidated) {
            appendBlock(text, "⚠ THIS TASK HAS PREVIOUSLY BEEN INVALIDATED. Review the feedback below before mapping.");
        }
        if (entries.isEmpty()) {
            appendBlock(text, "No previous task comments or invalidation issues were returned.");
        } else {
            for (String entry : entries) {
                appendBlock(text, entry);
            }
        }
        StringBuilder inheritable = new StringBuilder();
        if (invalidated) {
            appendBlock(inheritable, "⚠ THE SOURCE TASK HAD PREVIOUSLY BEEN INVALIDATED.");
        }
        for (String entry : entries) {
            appendBlock(inheritable, entry);
        }
        return new FeedbackResult(text.toString().trim(), inheritable.toString().trim(),
                splitTask, hasDetailedFeedback);
    }

    private static String buildUploadDetails(String changeset, String source, String imagery) {
        StringBuilder text = new StringBuilder();
        appendLabel(text, "Changeset comment", clean(changeset));
        String hashtags = extractHashtags(changeset);
        appendLabel(text, "Hashtags", hashtags);
        String displayedSource = isBlank(source) ? imagery : source;
        appendLabel(text, "Source / authorised imagery", clean(displayedSource));
        if (isBlank(changeset)) {
            appendBlock(text, "No changeset comment was returned. Check the project page before uploading.");
        }
        return text.toString().trim();
    }

    private static List<String> extractAlignmentNotes(String text) {
        if (isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> notes = new ArrayList<>();
        for (String piece : text.split("(?<=[.!?])\\s+|\\R+")) {
            String lower = piece.toLowerCase(Locale.ROOT);
            if (lower.contains("offset") || lower.contains("align") || lower.contains("misalign")
                    || lower.contains("shift imagery") || lower.contains("shift the imagery")) {
                String cleaned = piece.trim();
                if (!cleaned.isEmpty() && !notes.contains(cleaned)) {
                    notes.add(cleaned);
                }
                if (notes.size() == 6) {
                    break;
                }
            }
        }
        return notes;
    }

    private static String extractHashtags(String changeset) {
        if (isBlank(changeset)) {
            return "Not provided";
        }
        List<String> tags = new ArrayList<>();
        Matcher matcher = HASHTAG.matcher(changeset);
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags.isEmpty() ? "None found" : String.join(" ", tags);
    }

    static List<InstructionImage> extractInstructionImages(String... instructionBlocks) {
        List<PositionedImage> found = new ArrayList<>();
        int blockOffset = 0;
        for (String block : instructionBlocks) {
            if (!isBlank(block)) {
                Matcher htmlMatcher = HTML_IMAGE.matcher(block);
                while (htmlMatcher.find()) {
                    Map<String, String> attributes = htmlAttributes(htmlMatcher.group(1));
                    String description = firstNonBlank(attributes.get("alt"), attributes.get("title"));
                    addInstructionImage(found, blockOffset + htmlMatcher.start(), attributes.get("src"), description);
                }
                Matcher markdownMatcher = MARKDOWN_IMAGE.matcher(block);
                while (markdownMatcher.find()) {
                    String description = firstNonBlank(markdownMatcher.group(1), markdownMatcher.group(3));
                    addInstructionImage(found, blockOffset + markdownMatcher.start(), markdownMatcher.group(2), description);
                }
                blockOffset += block.length();
            }
            blockOffset++;
        }
        found.sort(Comparator.comparingInt(image -> image.position));
        List<InstructionImage> images = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (PositionedImage image : found) {
            if (seenUrls.add(image.image.getUrl())) {
                images.add(image.image);
            }
            if (images.size() == MAX_INSTRUCTION_IMAGES) {
                break;
            }
        }
        return images;
    }

    private static void addInstructionImage(List<PositionedImage> target, int position, String rawUrl,
            String description) {
        String url = normaliseInstructionImageUrl(rawUrl);
        if (!url.isEmpty()) {
            target.add(new PositionedImage(position, new InstructionImage(url, clean(description))));
        }
    }

    private static String normaliseInstructionImageUrl(String rawUrl) {
        String url = decodeHtml(rawUrl).trim();
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return "https://tasks.hotosm.org" + url;
        }
        return url.regionMatches(true, 0, "https://", 0, 8) ? url : "";
    }

    private static Map<String, String> htmlAttributes(String text) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = HTML_ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            String attributeValue = firstNonBlank(matcher.group(2), matcher.group(3), matcher.group(4));
            attributes.put(matcher.group(1).toLowerCase(Locale.ROOT), decodeHtml(attributeValue));
        }
        return attributes;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String eventHeading(String author, String date) {
        String who = isBlank(author) ? "Unknown mapper/validator" : clean(author);
        String when = isBlank(date) ? "" : " · " + clean(date).replace('T', ' ');
        return who + when + "\n";
    }

    private static String optionalText(String value) {
        return isBlank(value) || "INVALIDATED".equalsIgnoreCase(value) ? "" : ": " + clean(value);
    }

    private static void appendLabel(StringBuilder target, String label, String value) {
        if (!isBlank(value)) {
            appendBlock(target, label + ": " + value);
        }
    }

    private static void appendBlock(StringBuilder target, String value) {
        if (isBlank(value)) {
            return;
        }
        if (target.length() > 0) {
            target.append("\n\n");
        }
        target.append(value.trim());
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String withoutMarkdownImages = MARKDOWN_IMAGE.matcher(value).replaceAll("$1");
        return withoutMarkdownImages.replaceAll("(?i)<br\\s*/?>|</(?:p|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\r", "")
                .trim();
    }

    private static String decodeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String displayValue(Object value) {
        if (value instanceof List<?>) {
            List<String> values = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            return String.join(", ", values);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstText(Map<String, Object> object, String... keys) {
        Object found = value(object, keys);
        return found == null ? "" : displayValue(found);
    }

    private static Object value(Map<String, Object> object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.containsKey(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List<?> ? (List<Object>) value : Collections.emptyList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private static final class FeedbackResult {
        private final String displayText;
        private final String inheritableText;
        private final boolean splitTask;
        private final boolean hasDetailedFeedback;

        private FeedbackResult(String displayText, String inheritableText, boolean splitTask,
                boolean hasDetailedFeedback) {
            this.displayText = displayText;
            this.inheritableText = inheritableText;
            this.splitTask = splitTask;
            this.hasDetailedFeedback = hasDetailedFeedback;
        }
    }

    private static final class PositionedImage {
        private final int position;
        private final InstructionImage image;

        private PositionedImage(int position, InstructionImage image) {
            this.position = position;
            this.image = image;
        }
    }
}
