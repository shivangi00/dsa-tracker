package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.analysis.Recommendation;

import java.util.List;

/**
 * An analysis as the API returns it: the stored {@link ComplexityAnalysis} plus how it compares
 * with the best known approaches ({@code recommendation} is null outside NeetCode 150) and the
 * problem's interview talking points ({@code interviewTips}, empty outside NeetCode 150).
 */
public record AnalysisView(String time, String space, List<String> reasons, String confidence, String source,
                           Recommendation recommendation, List<String> interviewTips) {


    /** Null when there's no analysis. */
    public static AnalysisView of(ComplexityAnalysis a, Integer catalogId, ApproachRecommender approaches) {
        if (a == null) {
            return null;
        }
        return new AnalysisView(a.time(), a.space(), a.reasons(), a.confidence(), a.source(),
                approaches.recommend(catalogId, a).orElse(null), approaches.tips(catalogId));
    }
}
