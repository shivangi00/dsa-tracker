package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ComplexityAnalyser;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.security.RateLimitRules;
import dev.shivangi.dsatracker.security.RateLimiter;
import dev.shivangi.dsatracker.security.TooManyRequestsException;
import org.springframework.stereotype.Service;

/**
 * "Analyse": read the saved code, work out its complexity, store the result.
 *
 * <p>Deliberately NOT one transaction. Analysis can call an outside service (Claude) that takes
 * seconds; holding a database connection that long would starve other requests of the small
 * connection pool. So: a short read, the analysis with no connection held, then a short write
 * that checks the code didn't change in between.
 */
@Service
public class AnalysisService {

    private final ProblemService problems;
    private final ComplexityAnalyser analyser;
    private final RateLimiter limiter;

    public AnalysisService(ProblemService problems, ComplexityAnalyser analyser, RateLimiter limiter) {
        this.problems = problems;
        this.analyser = analyser;
        this.limiter = limiter;
    }

    /**
     * Analyses code that isn't saved anywhere yet: the "Analyse" button in the Mark as done
     * window, before the problem exists. Same limits as saved analyses.
     */
    public ComplexityAnalysis preview(Long userId, String code, CodeLanguage language) {
        String solution = ProblemService.checkCode(code, language);
        if (solution == null) {
            throw new BadRequestException("Add your code first, then analyse it");
        }
        limit(userId);
        return analyser.analyse(solution, language);
    }

    private void limit(Long userId) {
        RateLimiter.Decision decision = limiter.tryAcquire(RateLimitRules.ANALYSES_PER_USER, String.valueOf(userId));
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfterSeconds());
        }
    }

    public Problem analyse(Long userId, long problemId) {
        ProblemService.CodeToAnalyse code = problems.codeToAnalyse(userId, problemId);

        limit(userId);

        ComplexityAnalysis result = analyser.analyse(code.code(), code.language());
        return problems.saveAnalysis(userId, problemId, code, result);
    }
}
