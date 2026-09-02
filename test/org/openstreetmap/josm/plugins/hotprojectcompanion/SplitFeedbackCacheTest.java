package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Dependency-free tests for retained split-task feedback. */
public final class SplitFeedbackCacheTest {
    private SplitFeedbackCacheTest() {
    }

    public static void main(String[] args) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MemoryStore store = new MemoryStore();
        SplitFeedbackCache first = cache(store, start);
        first.remember(TaskReference.forHotTask(100, 10), context("Parent A comment"));

        SplitFeedbackCache.Entry childA = cache(store, start.plusSeconds(24 * 60 * 60))
                .recentForSplitChild(TaskReference.forHotTask(100, 11));
        requireContains(SplitFeedbackCache.appendInherited("Split task", childA), "Parent A comment");

        cache(store, start.plusSeconds(2 * 24 * 60 * 60))
                .remember(TaskReference.forHotTask(100, 20), context("Parent B comment"));

        String childAAgain = SplitFeedbackCache.appendInherited("Split task",
                cache(store, start.plusSeconds(3 * 24 * 60 * 60))
                        .recentForSplitChild(TaskReference.forHotTask(100, 11)));
        requireContains(childAAgain, "Parent A comment");

        String childB = SplitFeedbackCache.appendInherited("Split task",
                cache(store, start.plusSeconds(3 * 24 * 60 * 60))
                        .recentForSplitChild(TaskReference.forHotTask(100, 21)));
        requireContains(childB, "Parent B comment");

        if (cache(store, start.plusSeconds(31L * 24 * 60 * 60))
                .recentForSplitChild(TaskReference.forHotTask(100, 11)) != null) {
            throw new AssertionError("Feedback older than 30 days must expire");
        }
        if (cache(store, start.plusSeconds(3 * 24 * 60 * 60))
                .recentForSplitChild(TaskReference.forHotTask(200, 21)) != null) {
            throw new AssertionError("Feedback must not cross project boundaries");
        }
    }

    private static SplitFeedbackCache cache(MemoryStore store, Instant instant) {
        return new SplitFeedbackCache(store, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static TaskContext context(String feedback) {
        return new TaskContext("", "", feedback, "", feedback, false, true,
                Collections.emptyList(), "");
    }

    private static void requireContains(String actual, String expected) {
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected text not found: " + expected + "\nActual: " + actual);
        }
    }

    private static final class MemoryStore implements PluginPreferences.Store {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
