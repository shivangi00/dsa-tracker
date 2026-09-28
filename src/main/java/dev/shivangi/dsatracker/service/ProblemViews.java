package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.analysis.ComplexityExpression;
import dev.shivangi.dsatracker.domain.Attempt;
import dev.shivangi.dsatracker.domain.AttemptRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.SolutionVersion;
import dev.shivangi.dsatracker.domain.SolutionVersionRepository;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.web.AnalysisView;
import dev.shivangi.dsatracker.web.AttemptView;
import dev.shivangi.dsatracker.web.ProblemView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds {@link ProblemView}s with their attempt history, in two queries however many problems. */
@Service
public class ProblemViews {

    private final AttemptRepository attempts;
    private final SolutionVersionRepository versions;
    private final SpacedRepetitionPolicy policy;
    private final ApproachRecommender approaches;
    private final Clock clock;

    public ProblemViews(AttemptRepository attempts, SolutionVersionRepository versions, SpacedRepetitionPolicy policy,
                        ApproachRecommender approaches, Clock clock) {
        this.attempts = attempts;
        this.versions = versions;
        this.policy = policy;
        this.approaches = approaches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProblemView of(Problem p, Plan plan) {
        return of(List.of(p), plan).get(0);
    }

    /** Views in the same order as {@code problems}. */
    @Transactional(readOnly = true)
    public List<ProblemView> of(List<Problem> problems, Plan plan) {
        if (problems.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<Attempt> all = attempts.findByProblemIdInOrderByProblemIdAscRevisionAscTryNoAsc(
                problems.stream().map(Problem::getId).toList());
        Map<Long, List<SolutionVersion>> versionsByAttempt = all.isEmpty() ? Map.of()
                : versions.findByAttemptIdInOrderByAttemptIdAscVersionNoAsc(all.stream().map(Attempt::getId).toList())
                .stream().collect(Collectors.groupingBy(SolutionVersion::getAttemptId));
        Map<Long, List<Attempt>> attemptsByProblem = all.stream().collect(Collectors.groupingBy(Attempt::getProblemId));

        return problems.stream().map(p -> {
            ComplexityExpression.Size[] best = {null};   // best time so far across the whole history
            List<AttemptView> history = attemptsByProblem.getOrDefault(p.getId(), List.of()).stream()
                    .map(a -> new AttemptView(a.getId(), a.getRevision(), a.getTryNo(), a.getAttemptedOn(), a.getRating(),
                            a.getLearnings(), a.getExcalidrawUrl(), a.isEditableOn(today),
                            versionsByAttempt.getOrDefault(a.getId(), List.of()).stream()
                                    .map(v -> {
                                        ComplexityAnalysis analysis = v.analysis();
                                        boolean improved = false;
                                        if (analysis != null) {
                                            var time = ComplexityExpression.evaluate(analysis.time()).orElse(null);
                                            if (time != null) {
                                                improved = best[0] != null && ApproachRecommender.better(time, best[0]);
                                                if (best[0] == null || time.log() < best[0].log()) {
                                                    best[0] = time;
                                                }
                                            }
                                        }
                                        return new AttemptView.Version(v.getId(), v.getVersionNo(), v.getCode(),
                                                v.getCodeLanguage(), AnalysisView.of(analysis, p.getCatalogId(), approaches),
                                                improved);
                                    })
                                    .toList()))
                    .toList();
            return new ProblemView(p.getId(), p.getCatalogId(), p.getName(), p.getLink(), p.getSolvedOn(),
                    p.getDifficulty(), p.getIntervalDays(), p.getEase(), p.getReps(), p.getLapses(),
                    p.getLastReviewedOn(), p.getNextDueOn(), policy.overdueDays(p.schedule(), today),
                    p.getIntervalDays() >= SpacedRepetitionPolicy.MATURE_DAYS, p.revisionsDone(), p.isFullyRevised(),
                    p.getFirstRating(), policy.previewGaps(p.schedule(), today, plan.end()), history,
                    approaches.tips(p.getCatalogId()));
        }).toList();
    }
}
