package dev.shivangi.dsatracker.weekly;

import dev.shivangi.dsatracker.weekly.TestBuilder.ItemPlan;
import dev.shivangi.dsatracker.weekly.TestBuilder.PatternInfo;
import dev.shivangi.dsatracker.weekly.TestBuilder.Solved;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBuilderTest {

    // Patterns 1–3 are one topic, 4–5 another, 6–9 a third.
    private final List<PatternInfo> patterns = List.of(
            new PatternInfo(1, "Arrays"), new PatternInfo(2, "Arrays"), new PatternInfo(3, "Arrays"),
            new PatternInfo(4, "Stack"), new PatternInfo(5, "Stack"),
            new PatternInfo(6, "Graphs"), new PatternInfo(7, "Graphs"), new PatternInfo(8, "Graphs"),
            new PatternInfo(9, "Graphs"));

    private final Map<Integer, List<Integer>> practice = Map.of(
            1, List.of(101, 102), 2, List.of(201), 3, List.of(301),
            4, List.of(401), 5, List.of(), 6, List.of(601), 7, List.of(701),
            8, List.of(801), 9, List.of(901));

    private TestBuilder builder(long seed) {
        return new TestBuilder(new Random(seed));
    }

    @Test
    void onePracticeProblemPerPatternPractised() {
        List<Solved> week = List.of(new Solved(10, 1, 0), new Solved(11, 1, 0), new Solved(12, 4, 0));
        List<ItemPlan> items = builder(1).build(week, practice, patterns);
        assertEquals(2, items.size());
        assertEquals(new HashSet<>(List.of(1, 4)), new HashSet<>(items.stream().map(ItemPlan::patternId).toList()));
        assertTrue(List.of(101, 102).contains(items.stream().filter(i -> i.patternId() == 1).findFirst().get().practiceId()));
    }

    @Test
    void patternsWithNothingNewLeftAreSkipped() {
        List<ItemPlan> items = builder(2).build(List.of(new Solved(20, 5, 0)), practice, patterns);
        assertTrue(items.isEmpty());
    }

    @Test
    void atMostFiveQuestions() {
        List<Solved> week = List.of(new Solved(1, 1, 0), new Solved(2, 2, 0), new Solved(3, 3, 0),
                new Solved(4, 4, 0), new Solved(6, 6, 0), new Solved(7, 7, 0), new Solved(8, 8, 0));
        assertEquals(TestBuilder.MAX_ITEMS, builder(3).build(week, practice, patterns).size());
    }

    @Test
    void forgottenPatternsComeFirst() {
        List<Solved> week = List.of(new Solved(1, 1, 0), new Solved(2, 2, 0), new Solved(3, 3, 0),
                new Solved(4, 4, 0), new Solved(6, 6, 0), new Solved(7, 7, 3));
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(7, builder(seed).build(week, practice, patterns).get(0).patternId());
        }
    }

    @Test
    void theAnchorIsTheSolvedProblemForgottenMost() {
        List<Solved> week = List.of(new Solved(30, 1, 0), new Solved(31, 1, 2));
        assertEquals(31, builder(4).build(week, practice, patterns).get(0).anchorCatalogId());
    }

    @Test
    void fourDistinctOptionsIncludingTheAnswerAndSameTopicLookAlikes() {
        for (long seed = 0; seed < 20; seed++) {
            ItemPlan item = builder(seed).build(List.of(new Solved(1, 1, 0)), practice, patterns).get(0);
            List<Integer> options = item.optionPatternIds();
            assertEquals(4, options.size());
            assertEquals(4, new HashSet<>(options).size());
            assertTrue(options.contains(1));
            assertTrue(options.containsAll(List.of(2, 3)));   // both other Arrays patterns are used first
        }
    }
}
