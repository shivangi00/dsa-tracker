package dev.shivangi.dsatracker.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Tries the first analyser (Claude); if it fails for any reason, uses the second (built-in). */
public final class FallbackComplexityAnalyser implements ComplexityAnalyser {

    private static final Logger log = LoggerFactory.getLogger(FallbackComplexityAnalyser.class);

    private final ComplexityAnalyser primary;
    private final ComplexityAnalyser fallback;

    public FallbackComplexityAnalyser(ComplexityAnalyser primary, ComplexityAnalyser fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ComplexityAnalysis analyse(String code, CodeLanguage language) {
        try {
            return primary.analyse(code, language);
        } catch (RuntimeException e) {
            log.warn("Claude analysis failed, using the built-in estimate: {}", e.getMessage());
            ComplexityAnalysis estimate = fallback.analyse(code, language);
            List<String> reasons = new ArrayList<>(estimate.reasons());
            reasons.add(0, "Claude couldn't be reached just now, so this is the built-in estimate.");
            return new ComplexityAnalysis(estimate.time(), estimate.space(), reasons, estimate.confidence(), estimate.source());
        }
    }
}
