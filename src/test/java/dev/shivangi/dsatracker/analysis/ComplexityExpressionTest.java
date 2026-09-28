package dev.shivangi.dsatracker.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityExpressionTest {

    private static double log(String s) {
        return ComplexityExpression.evaluate(s).orElseThrow(() -> new AssertionError("couldn't read " + s)).log();
    }

    private static void same(String a, String b) {
        assertEquals(log(a), log(b), 0.01);
    }

    private static void smaller(String a, String b) {
        assertTrue(log(a) < log(b), a + " should be smaller than " + b);
    }

    @Test
    void constantsAreOne() {
        assertEquals(0.0, log("O(1)"), 1e-9);
        same("O(1)", "O(26)");
        same("O(n)", "O(2n)");
        same("O(n)", "O(n/2)");
        same("O(n)", "O(n + 1)");
    }

    @Test
    void readsTheUsualNotations() {
        same("O(n²)", "O(n^2)");
        same("O(n²)", "O(n*n)");
        same("O(m·n)", "O(n²)");
        same("O(m·n)", "O(m × n)");
        same("O(m·n)", "O(mn)");
        same("O(n log n)", "O(n * log(n))");
        same("O(n log n)", "O(nlogn)");
        same("O(log(m·n))", "O(log m + log n)");
        same("O(n·k log k)", "O(n * k * log k)");
        same("O(n)", "O(n α(n))");
        same("O(n)", "O(max(m, n))");
        same("O(√n)", "O(sqrt(n))");
    }

    @Test
    void ordersCommonClasses() {
        String[] ladder = {"O(1)", "O(log n)", "O(√n)", "O(n)", "O(n log n)", "O(n²)", "O(n³)", "O(2ⁿ)", "O(n!)"};
        for (int i = 1; i < ladder.length; i++) {
            smaller(ladder[i - 1], ladder[i]);
        }
        smaller("O(n · 2ⁿ)", "O(n · 4ⁿ)");
        smaller("O(n log k)", "O(n log n)");
        smaller("O(n·k)", "O(n²)");
    }

    @Test
    void exponentialsAreFlagged() {
        assertTrue(ComplexityExpression.evaluate("O(2ⁿ)").orElseThrow().exponential());
        assertTrue(ComplexityExpression.evaluate("O(m·n·3^L)").orElseThrow().exponential());
        assertTrue(ComplexityExpression.evaluate("O(n!)").orElseThrow().exponential());
        assertFalse(ComplexityExpression.evaluate("O(n³)").orElseThrow().exponential());
    }

    @Test
    void ignoresWordsOutsideTheBrackets() {
        same("O(n) average", "O(n)");
        same("amortised O(1) per call", "O(1)");
    }

    @Test
    void refusesWhatItCantRead() {
        assertTrue(ComplexityExpression.evaluate("").isEmpty());
        assertTrue(ComplexityExpression.evaluate(null).isEmpty());
        assertTrue(ComplexityExpression.evaluate("O(n").isEmpty());
        assertTrue(ComplexityExpression.evaluate("O(n ?? m)").isEmpty());
    }
}
