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
            return Answer.notFound("The loaded project information does not mention the subject of this question.");
        }

        Passage best = passages.get(0);
        double coverage = best.matches / (double) questionTerms.size();
        boolean yesNoQuestion = isYesNoQuestion(question);
        if (yesNoQuestion && coverage < 0.75) {
            return Answer.notFound("The loaded project information contains only partially related guidance and does not specifically answer this question.",
                    evidence(passages));
        }

        String lower = best.text.toLowerCase(Locale.ROOT);
        if (yesNoQuestion && containsAny(lower, NEGATIVE_MARKERS)) {
            return new Answer(Outcome.NO,
                    "No — the project guidance appears to rule this out.", evidence(passages));
        }
        if (yesNoQuestion && containsAny(lower, POSITIVE_MARKERS)) {
            return new Answer(Outcome.YES,
                    "Yes — the project guidance appears to include this.", evidence(passages));
        }
        return new Answer(Outcome.RELATED,
                yesNoQuestion
                        ? "Related guidance was found, but it does not give a clear yes or no."
                        : "The most relevant loaded guidance is shown below.",
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
            if (result.size() == 3) {
                break;
            }
        }
        return result;
    }

    private static void addPassages(List<Passage> target, String source, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String piece : text.split("(?<=[.!?])\\s+|\\R+")) {
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

    private static boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
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
