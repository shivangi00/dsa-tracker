package dev.shivangi.dsatracker.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackComplexityAnalyserTest {

    private final ComplexityAnalyser estimate = new HeuristicComplexityAnalyser();

    @Test
    void usesThePrimaryWhenItWorks() {
        ComplexityAnalyser primary = (code, lang) ->
                new ComplexityAnalysis("O(1)", "O(1)", List.of("from primary"), "high", ComplexityAnalysis.CLAUDE);
        ComplexityAnalysis a = new FallbackComplexityAnalyser(primary, estimate).analyse("x = 1", CodeLanguage.PYTHON);
        assertEquals(ComplexityAnalysis.CLAUDE, a.source());
    }

    @Test
    void fallsBackToTheEstimateAndSaysSo() {
        ComplexityAnalyser broken = (code, lang) -> {
            throw new IllegalStateException("Claude API answered 529");
        };
        ComplexityAnalysis a = new FallbackComplexityAnalyser(broken, estimate)
                .analyse("for x in nums:\n    print(x)\n", CodeLanguage.PYTHON);
        assertEquals("O(n)", a.time());
        assertEquals(ComplexityAnalysis.ESTIMATE, a.source());
        assertTrue(a.reasons().get(0).contains("couldn't be reached"));
    }
}
