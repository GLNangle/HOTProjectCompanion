package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

/** Cautious local matching between mapper questions and loaded HOT guidance. */
final class TaskQuestionAnswerer {
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "about", "all", "am", "an", "and", "are", "be", "can", "could",
            "do", "does", "for", "from", "how", "i", "in", "is", "it", "map",
            "mapped", "mapper", "mapping", "may", "of", "on", "or", "should",
            "that", "the", "this", "to", "we", "what", "when", "where", "which",
            "with", "would"));
    private static final List<String> NEGATIVE_MARKERS = Arrays.asList(
            "do not", "don't", "must not", "should not", "shouldn't", "never",
            "exclude", "excluded", "ignore", "not map", "not include");
    private static final List<String> POSITIVE_MARKERS = Arrays.asList(
            "map ", "map all", "include", "must", "should", "use ", "trace", "add ");

    private TaskQuestionAnswerer() {
    }

    static Answer answer(TaskContext context, String question) {
        if (context == null) {
            return Answer.notFound("Load a HOT task before asking a question.");
        }
        if (isMappingOverviewQuestion(question)) {
            return mappingOverview(context);
        }
        Set<String> questionTerms = meaningfulTerms(question);
        if (questionTerms.isEmpty()) {
            return Answer.notFound("Ask a specific question about what to map, imagery or previous feedback.");
        }

        List<Passage> passages = new ArrayList<>();
        addPassages(passages, "Project and task instructions", context.getWhatToMap());
        addPassages(passages, "Required imagery", context.getImageryGuidance());
        addPassages(passages, "Previous feedback", context.getPreviousFeedback());
        for (Passage passage : passages) {
            passage.score(questionTerms);
        }
        passages.removeIf(passage -> passage.matches == 0);
        passages.sort(Comparator.comparingInt(Passage::scoreValue).reversed());
        if (passages.isEmpty()) {
            return Answer.notFound("Not specified in the loaded task guidance.");
        }

        Passage best = passages.get(0);
        double coverage = best.matches / (double) questionTerms.size();
        boolean yesNoQuestion = isYesNoQuestion(question);
        if (yesNoQuestion && coverage < 0.75) {
            return Answer.notFound("Not specified in the loaded task guidance.",
                    evidence(passages));
        }

        String lower = best.text.toLowerCase(Locale.ROOT);
        String guidance = sentenceCase(best.text);
        if (yesNoQuestion && containsAny(lower, NEGATIVE_MARKERS)) {
            return new Answer(Outcome.NO,
                    "No. " + guidance, evidence(passages));
        }
        if (yesNoQuestion && containsAny(lower, POSITIVE_MARKERS)) {
            return new Answer(Outcome.YES,
                    "Yes. " + guidance, evidence(passages));
        }
        return new Answer(Outcome.RELATED,
                yesNoQuestion
                        ? "No clear yes or no. " + guidance
                        : guidance,
                evidence(passages));
    }

    private static List<Passage> evidence(List<Passage> passages) {
        List<Passage> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Passage passage : passages) {
            String key = passage.source + "\n" + passage.text;
            if (seen.add(key)) {
                result.add(passage);
            }
            if (result.size() == 1) {
                break;
            }
        }
        return result;
    }

    private static void addPassages(List<Passage> target, String source, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String piece : text.split("(?<=[.!?])\\s+|\\R+|\\s*;\\s*|\\s+(?i:but|however)\\s+")) {
            String cleaned = piece.trim();
            if (!cleaned.isEmpty()) {
                target.add(new Passage(source, cleaned));
            }
        }
    }

    private static Set<String> meaningfulTerms(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String term : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            String canonical = canonical(term);
            if (canonical.length() > 2 && !STOP_WORDS.contains(canonical)) {
                result.add(canonical);
            }
        }
        return result;
    }

    private static String canonical(String term) {
        if (term.endsWith("ies") && term.length() > 4) {
            return term.substring(0, term.length() - 3) + "y";
        }
        if (term.endsWith("s") && !term.endsWith("ss") && term.length() > 4) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }

    private static boolean isYesNoQuestion(String question) {
        String lower = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("should ") || lower.startsWith("do ")
                || lower.startsWith("does ") || lower.startsWith("can ")
                || lower.startsWith("could ") || lower.startsWith("may ")
                || lower.startsWith("is ") || lower.startsWith("are ");
    }

    private static boolean isMappingOverviewQuestion(String question) {
        Set<String> terms = overviewTerms(question);
        if (terms.isEmpty()) {
            return false;
        }
        if (terms.contains("imagery") || terms.contains("image") || terms.contains("source")
                || terms.contains("offset") || terms.contains("alignment")
                || terms.contains("feedback") || terms.contains("comment")
                || terms.contains("upload") || terms.contains("changeset")) {
            return false;
        }
        boolean mapIntent = terms.contains("map");
        boolean questionCue = terms.contains("what") || terms.contains("which")
                || terms.contains("tell") || terms.contains("show")
                || terms.contains("explain") || terms.contains("need")
                || terms.contains("supposed") || terms.contains("required")
                || terms.contains("target") || terms.contains("instruction")
                || terms.contains("object") || terms.contains("thing")
                || terms.contains("feature") || terms.contains("task");
        if (mapIntent && (questionCue || terms.size() <= 3)) {
            return true;
        }
        // Natural but incomplete English often omits "map", for example
        // "what do I do here?" or "what this task wants?".
        boolean broadActionQuestion = terms.contains("what")
                && (terms.contains("do") || terms.contains("need") || terms.contains("want"))
                && (terms.contains("here") || terms.contains("task") || terms.size() <= 4);
        return broadActionQuestion;
    }

    private static Set<String> overviewTerms(String question) {
        if (question == null) {
            return Collections.emptySet();
        }
        String normalised = Normalizer.normalize(question.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        Set<String> result = new LinkedHashSet<>();
        for (String raw : normalised.split("[^\\p{L}\\p{N}]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            String term = raw;
            if (term.equals("wat") || term.equals("wht") || term.equals("whats")) {
                term = "what";
            } else if (term.equals("wich")) {
                term = "which";
            } else if (term.startsWith("map") || term.equals("trace") || term.equals("tracing")) {
                term = "map";
            } else if (term.equals("objects")) {
                term = "object";
            } else if (term.equals("things")) {
                term = "thing";
            } else if (term.equals("features")) {
                term = "feature";
            } else if (term.startsWith("instruct")) {
                term = "instruction";
            } else if (term.equals("wants") || term.equals("wanted")) {
                term = "want";
            }
            result.add(term);
        }
        return result;
    }

    private static Answer mappingOverview(TaskContext context) {
        List<Passage> instructions = new ArrayList<>();
        addPassages(instructions, "Project and task instructions", context.getWhatToMap());
        if (instructions.isEmpty()) {
            return Answer.notFound("The loaded task does not provide mapping instructions.");
        }
        instructions.sort(Comparator.comparingInt(TaskQuestionAnswerer::overviewScore).reversed());
        StringBuilder summary = new StringBuilder();
        for (Passage passage : instructions) {
            String text = sentenceCase(passage.text);
            if (summary.length() > 0 && summary.length() + text.length() + 1 > 280) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(text);
            if (summary.length() >= 140 || countSentences(summary.toString()) == 2) {
                break;
            }
        }
        Passage source = instructions.get(0);
        return new Answer(Outcome.RELATED, summary.toString(), Collections.singletonList(source));
    }

    private static int overviewScore(Passage passage) {
        String lower = passage.text.toLowerCase(Locale.ROOT);
        int score = containsAny(lower, NEGATIVE_MARKERS) || containsAny(lower, POSITIVE_MARKERS)
                ? 100 : 0;
        if (lower.contains("building") || lower.contains("road") || lower.contains("highway")
                || lower.contains("waterway") || lower.contains("land use")
                || lower.contains("landuse") || lower.contains("residential")) {
            score += 40;
        }
        return score;
    }

    private static int countSentences(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '.' || character == '!' || character == '?') {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String sentenceCase(String text) {
        if (text == null || text.isEmpty() || !Character.isLowerCase(text.charAt(0))) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    enum Outcome {
        YES,
        NO,
        RELATED,
        NOT_FOUND
    }

    static final class Answer {
        private final Outcome outcome;
        private final String summary;
        private final List<Passage> evidence;

        Answer(Outcome outcome, String summary, List<Passage> evidence) {
            this.outcome = outcome;
            this.summary = summary;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        }

        static Answer notFound(String summary) {
            return notFound(summary, Collections.emptyList());
        }

        static Answer notFound(String summary, List<Passage> evidence) {
            return new Answer(Outcome.NOT_FOUND, summary, evidence);
        }

        Outcome getOutcome() {
            return outcome;
        }

        String getSummary() {
            return summary;
        }

        List<Passage> getEvidence() {
            return evidence;
        }
    }

    static final class Passage {
        private final String source;
        private final String text;
        private int matches;

        Passage(String source, String text) {
            this.source = source;
            this.text = text;
        }

        private void score(Set<String> questionTerms) {
            Set<String> passageTerms = meaningfulTerms(text);
            matches = 0;
            for (String term : questionTerms) {
                if (passageTerms.contains(term)) {
                    matches++;
                }
            }
        }

        private int scoreValue() {
            return matches * 100 - Math.min(99, text.length() / 8);
        }

        String getSource() {
            return source;
        }

        String getText() {
            return text;
        }
    }
}
