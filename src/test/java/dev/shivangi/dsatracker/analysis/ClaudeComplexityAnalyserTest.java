package dev.shivangi.dsatracker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading Claude's answer. No network: parse() is given API responses directly. */
class ClaudeComplexityAnalyserTest {

    private final ClaudeComplexityAnalyser claude = new ClaudeComplexityAnalyser(new ObjectMapper(), "test-key", "test-model");

    private static String response(String text) {
        return "{\"content\":[{\"type\":\"text\",\"text\":" + new ObjectMapper().valueToTree(text) + "}]}";
    }

    @Test
    void readsTheJsonAnswer() throws Exception {
        ComplexityAnalysis a = claude.parse(response("""
                {"time": "O(n)", "space": "O(n)", "reasons": ["One pass over nums", "A hash map of seen values"], "confidence": "high"}"""));
        assertEquals("O(n)", a.time());
        assertEquals("O(n)", a.space());
        assertEquals("high", a.confidence());
        assertEquals(ComplexityAnalysis.CLAUDE, a.source());
        assertEquals("One pass over nums", a.reasons().get(0));
        assertTrue(a.reasons().get(a.reasons().size() - 1).contains("Claude"));
    }

    @Test
    void toleratesTextAroundTheJson() throws Exception {
        ComplexityAnalysis a = claude.parse(response("Here you go:\n{\"time\":\"O(log n)\",\"space\":\"O(1)\",\"reasons\":[]}\nDone."));
        assertEquals("O(log n)", a.time());
        assertEquals("medium", a.confidence());
    }

    @Test
    void rejectsAnswersThatAreNotBigO() {
        assertThrows(IllegalStateException.class,
                () -> claude.parse(response("{\"time\":\"linear\",\"space\":\"O(1)\",\"reasons\":[]}")));
        assertThrows(IllegalStateException.class, () -> claude.parse(response("I can't help with that.")));
    }
}
