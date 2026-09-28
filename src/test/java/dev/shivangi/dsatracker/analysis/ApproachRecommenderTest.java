package dev.shivangi.dsatracker.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproachRecommenderTest {

    private static final Approach HASH_SET = new Approach("Hash set", "O(n)", "O(n)", "…", false);
    private static final Approach SORT = new Approach("Sort", "O(n log n)", "O(1)", "…", false);
    private static final Approach DP_ROW = new Approach("One-row DP", "O(m·n)", "O(n)", "…", false);
    private static final Approach CENTRES = new Approach("Expand around centres", "O(n²)", "O(1)", "…", false);
    private static final Approach MANACHER = new Approach("Manacher", "O(n)", "O(n)", "…", true);
    private static final Approach SUBSETS = new Approach("Backtracking", "O(n · 2ⁿ)", "O(n)", "…", false);
    private static final Approach CLIMB = new Approach("Two variables", "O(n)", "O(1)", "…", false);
    private static final Approach BITS = new Approach("Clear the lowest set bit", "O(log n)", "O(1)", "…", false);

    private final ApproachRecommender recommender = new ApproachRecommender(Map.of(
            1, KnownProblem.of(HASH_SET, SORT),
            111, KnownProblem.of(DP_ROW),
            103, KnownProblem.of(CENTRES, MANACHER),
            68, KnownProblem.of(SUBSETS),
            99, KnownProblem.of(CLIMB),
            145, new KnownProblem(List.of(BITS), "the input is one 32-bit number")));

    private static ComplexityAnalysis analysis(String time, String space) {
        return new ComplexityAnalysis(time, space, List.of(), "medium", ComplexityAnalysis.ESTIMATE);
    }

    private Recommendation recommend(int id, String time, String space) {
        return recommender.recommend(id, analysis(time, space)).orElseThrow();
    }

    @Test
    void recommendsAFasterApproach() {
        Recommendation r = recommend(1, "O(n²)", "O(1)");
        assertEquals(Recommendation.FASTER, r.verdict());
        assertSame(HASH_SET, r.approach());
        assertTrue(r.message().contains("O(n) time is possible; your solution is O(n²)"), r.message());
        assertTrue(r.message().contains("trades memory"), r.message());   // O(n) space vs your O(1)
    }

    @Test
    void recommendsTheFastestNotJustAFasterOne() {
        assertSame(HASH_SET, recommend(1, "O(n²)", "O(n)").approach());
        assertSame(HASH_SET, recommend(1, "O(n log n)", "O(1)").approach());
    }

    @Test
    void recommendsLessMemoryAtTheSameSpeed() {
        Recommendation r = recommend(111, "O(m·n)", "O(m·n)");
        assertEquals(Recommendation.LEANER, r.verdict());
        assertSame(DP_ROW, r.approach());
    }

    @Test
    void saysSoWhenTheSolutionIsAlreadyBest() {
        Recommendation r = recommend(1, "O(n)", "O(n)");
        assertEquals(Recommendation.OPTIMAL, r.verdict());
        assertSame(HASH_SET, r.approach());
        assertNull(r.further());
        assertEquals(Recommendation.OPTIMAL, recommend(99, "O(n)", "O(1)").verdict());
    }

    @Test
    void smallDifferencesDontCount() {
        assertEquals(Recommendation.OPTIMAL, recommend(1, "O(2n)", "O(n)").verdict());
        assertEquals(Recommendation.OPTIMAL, recommend(1, "O(n + k)", "O(n)").verdict());
    }

    @Test
    void mentionsAdvancedApproachesOnlyAsGoingFurther() {
        Recommendation slow = recommend(103, "O(n³)", "O(1)");
        assertSame(CENTRES, slow.approach());       // the usual answer first, not Manacher
        assertSame(MANACHER, slow.further());

        Recommendation usual = recommend(103, "O(n²)", "O(1)");
        assertEquals(Recommendation.OPTIMAL, usual.verdict());
        assertSame(MANACHER, usual.further());
    }

    @Test
    void doesntCompareTwoExponentials() {
        assertEquals(Recommendation.OPTIMAL, recommend(68, "O(3ⁿ)", "O(n)").verdict());
    }

    @Test
    void plainRecursionGetsTheDpApproach() {
        Recommendation r = recommend(99, "O(2ⁿ)", "O(n)");
        assertEquals(Recommendation.FASTER, r.verdict());
        assertSame(CLIMB, r.approach());
    }

    @Test
    void flagsAnAnalysisThatBeatsTheBestKnown() {
        assertEquals(Recommendation.CHECK, recommend(111, "O(n)", "O(1)").verdict());
    }

    @Test
    void nothingForUnknownProblemsOrUnreadableAnalyses() {
        assertTrue(recommender.recommend(null, analysis("O(n)", "O(1)")).isEmpty());
        assertTrue(recommender.recommend(500, analysis("O(n)", "O(1)")).isEmpty());
        assertTrue(recommender.recommend(1, analysis("unclear", "O(1)")).isEmpty());
        assertTrue(recommender.recommend(1, null).isEmpty());
    }

    @Test
    void anUnreadableSpaceStillGetsATimeRecommendation() {
        Recommendation r = recommend(1, "O(n²)", "depends");
        assertEquals(Recommendation.FASTER, r.verdict());
        assertTrue(!r.message().contains("trades memory"), r.message());
    }

    @Test
    void fixedSizeInputsArentCompared() {
        Recommendation r = recommend(145, "O(n)", "O(1)");     // a loop over 32 bits looks like O(n)
        assertEquals(Recommendation.FIXED, r.verdict());
        assertSame(BITS, r.approach());
        assertTrue(r.message().contains("32-bit"), r.message());
    }
}
