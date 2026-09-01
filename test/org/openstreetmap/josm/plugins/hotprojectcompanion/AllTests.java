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
        BuildingImageAnalyserTest.main(args);
        BuildingCandidateScannerTest.main(args);
        CandidateReviewNavigationTest.main(args);
        CandidateReviewDecisionsTest.main(args);
        MappedBuildingFilterTest.run();
        SidebarButtonsTest.run();
        LearningProfileTest.main(args);
        System.out.println("HOT Project Companion: all tests passed");
    }
}
