package dev.dsatracker.dsa.analysis;

import java.util.List;

/**
 * The result of analysing a solution.
 *
 * @param time       e.g. "O(n log n)"
 * @param space      e.g. "O(n)"
 * @param reasons    the steps that led there, in plain words, so you can check the reasoning
 * @param confidence "high", "medium" or "low"
 * @param source     "estimate" (the built-in analyser), "claude", or "yours" (your own answer)
 */
public record ComplexityAnalysis(String time, String space, List<String> reasons, String confidence, String source) {

    public static final String ESTIMATE = "estimate";
    public static final String CLAUDE = "claude";
    public static final String YOURS = "yours";

    public ComplexityAnalysis {
        reasons = List.copyOf(reasons);
    }
}
