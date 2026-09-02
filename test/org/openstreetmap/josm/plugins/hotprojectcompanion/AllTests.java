package org.openstreetmap.josm.plugins.hotprojectcompanion;

/** Runs the dependency-free local test suite in one JVM. */
public final class AllTests {
    private AllTests() {
    }

    public static void main(String[] args) {
        TaskUrlParserTest.main(args);
        TaskBoundaryGeometryTest.main(args);
        TaskLayerNameParserTest.main(args);
        TaskContextParserTest.main(args);
        TaskQuestionAnswererTest.main(args);
        SplitFeedbackCacheTest.main(args);
        MapCaptureTest.main(args);
        CollapsibleSectionTest.main(args);
        BuildingImageAnalyserTest.main(args);
        BuildingCandidateScannerTest.main(args);
        CandidateReviewNavigationTest.main(args);
        CandidateReviewDecisionsTest.main(args);
        MappedBuildingFilterTest.run();
        SidebarButtonsTest.run();
        TaskStatusTransitionTest.run();
        LearningProfileTest.main(args);
        GeometryLearningProfileTest.main(args);
        System.out.println("HOT Project Companion: all tests passed");
    }
}
