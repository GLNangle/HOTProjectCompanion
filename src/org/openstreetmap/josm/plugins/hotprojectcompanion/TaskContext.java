package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Display-ready, read-only context for one HOT task. */
final class TaskContext {
    private final String whatToMap;
    private final String imageryGuidance;
    private final String previousFeedback;
    private final String uploadDetails;
    private final String inheritableFeedback;
    private final boolean splitTask;
    private final boolean hasDetailedFeedback;
    private final List<InstructionImage> instructionImages;
    private final String authorisedImagery;

    TaskContext(String whatToMap, String imageryGuidance, String previousFeedback, String uploadDetails,
            String inheritableFeedback, boolean splitTask, boolean hasDetailedFeedback,
            List<InstructionImage> instructionImages, String authorisedImagery) {
        this.whatToMap = whatToMap;
        this.imageryGuidance = imageryGuidance;
        this.previousFeedback = previousFeedback;
        this.uploadDetails = uploadDetails;
        this.inheritableFeedback = inheritableFeedback;
        this.splitTask = splitTask;
        this.hasDetailedFeedback = hasDetailedFeedback;
        this.instructionImages = Collections.unmodifiableList(new ArrayList<>(instructionImages));
        this.authorisedImagery = authorisedImagery == null ? "" : authorisedImagery.trim();
    }

    String getWhatToMap() {
        return whatToMap;
    }

    String getImageryGuidance() {
        return imageryGuidance;
    }

    String getPreviousFeedback() {
        return previousFeedback;
    }

    String getUploadDetails() {
        return uploadDetails;
    }

    String getInheritableFeedback() {
        return inheritableFeedback;
    }

    boolean isSplitTask() {
        return splitTask;
    }

    boolean hasDetailedFeedback() {
        return hasDetailedFeedback;
    }

    List<InstructionImage> getInstructionImages() {
        return instructionImages;
    }

    String getAuthorisedImagery() {
        return authorisedImagery;
    }
}
