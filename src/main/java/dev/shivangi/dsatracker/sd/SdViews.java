package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.service.Plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What the system design page receives. Built by {@link SdService}. */
public final class SdViews {

    private SdViews() {
    }

    public record Dashboard(LocalDate today, PlanInfo plan, Stats stats, List<TopicRow> topics,
                            List<ProblemRow> problems, List<DueRow> due, Map<SdKind, Map<Rating, Integer>> firstGaps) {
    }

    public record PlanInfo(int day, int days, LocalDate start, LocalDate end, LocalDate lastNewDay, int lastNewDayNo,
                           boolean newOpen) {
    }

    /**
     * @param quizAccuracy percent right across every saved quiz; null before the first
     * @param averageScore average of each scored problem's best version; null before the first
     */
    public record Stats(int topicsStudied, int topicsTotal, int topicsRevised, int problemsDesigned,
                        int problemsTotal, int problemsRevised, int dueToday, Integer quizAccuracy,
                        Double averageScore) {
    }

    public record TopicRow(String key, String section, String name, String url, Item item) {
    }

    public record ProblemRow(String key, String level, String name, String url, boolean scored, Item item) {
    }

    public record DueRow(SdKind kind, String key, String name, long itemId, int revision, long overdueDays) {
    }

    /**
     * One topic studied or problem designed.
     *
     * @param previewGaps for a due item: the gap each rating would give (0 = completes it)
     * @param bestScore   problems: the best coverage score of any saved version
     */
    public record Item(long id, SdKind kind, String key, LocalDate startedOn, Rating firstRating, int revisionsDone,
                       int revisions, LocalDate nextDueOn, boolean dueToday, long overdueDays, boolean fullyRevised,
                       Map<Rating, Integer> previewGaps, List<AttemptView> attempts, Double bestScore) {
    }

    public record AttemptView(long id, int revision, int tryNo, LocalDate attemptedOn, Rating rating, String notes,
                              String excalidrawUrl, boolean editable, Integer quizScore, Integer quizTotal,
                              List<AnswerView> answers) {
    }

    public record AnswerView(long id, int versionNo, DesignSections sections, Double score, Double structure,
                             List<String> hits, List<String> missed) {
    }

    /** A problem with its rubric points (what a strong answer covers) and reference design. */
    public record ProblemDetail(String key, String level, String name, String url, boolean scored,
                                List<String> rubric, SdCatalog.Reference reference) {
        static ProblemDetail of(SdCatalog.Problem p) {
            return new ProblemDetail(p.key(), p.level(), p.name(), p.url(), p.scored(),
                    p.rubric().stream().map(SdCatalog.RubricPoint::point).toList(), p.reference());
        }
    }

    /** FIRST: this quiz marks the topic studied. REVIEW: it's a due revision. PRACTICE: nothing is saved. */
    public enum QuizMode { FIRST, REVIEW, PRACTICE }

    public record QuizView(String topicKey, String name, String url, QuizMode mode, int revision,
                           List<Quiz.Asked> questions) {
    }

    public record QuizResult(int correct, int total, int percent, Rating rating, QuizMode mode, boolean saved,
                             List<Quiz.Graded> questions, Item item) {
    }

    public record ScoreView(boolean scored, Double score, Double structure, Double rubric, int partsWritten,
                            List<String> hits, List<String> missed) {
        static ScoreView of(DesignSections s, DesignScorer.Score score) {
            return score == null
                    ? new ScoreView(false, null, null, null, s.filled(), List.of(), List.of())
                    : new ScoreView(true, score.total(), score.structure(), score.rubric(), s.filled(),
                    score.hits(), score.missed());
        }
    }

    // ---- builders ----

    static Item item(SdItem i, List<SdAttempt> attempts, Map<Long, List<SdAnswer>> answersByAttempt,
                     SdCatalog catalog, SpacedRepetitionPolicy policy, Plan plan, LocalDate today) {
        ScheduleState s = i.schedule();
        SdCatalog.Problem problem = i.getKind() == SdKind.PROBLEM ? catalog.problem(i.getItemKey()).orElse(null) : null;
        List<AttemptView> views = new ArrayList<>();
        Double best = null;
        for (SdAttempt a : attempts) {
            List<AnswerView> answers = new ArrayList<>();
            for (SdAnswer ans : answersByAttempt.getOrDefault(a.getId(), List.of())) {
                answers.add(answer(ans, problem));
                if (ans.getScore() != null && (best == null || ans.getScore() > best)) {
                    best = ans.getScore();
                }
            }
            views.add(new AttemptView(a.getId(), a.getRevision(), a.getTryNo(), a.getAttemptedOn(), a.getRating(),
                    a.getNotes(), a.getExcalidrawUrl(), a.isEditableOn(today), a.getQuizScore(), a.getQuizTotal(),
                    List.copyOf(answers)));
        }
        return new Item(i.getId(), i.getKind(), i.getItemKey(), i.getStartedOn(), i.getFirstRating(),
                i.revisionsDone(), i.getKind().revisions(), i.getNextDueOn(), s.isDueOn(today),
                policy.overdueDays(s, today), i.isFullyRevised(), policy.previewGaps(s, today, plan.end()),
                List.copyOf(views), best);
    }

    static AnswerView answer(SdAnswer a, SdCatalog.Problem problem) {
        List<String> hits = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        if (a.getScore() != null && problem != null && problem.scored()) {
            List<Integer> covered = a.coveredPoints();
            for (int i = 0; i < problem.rubric().size(); i++) {
                (covered.contains(i) ? hits : missed).add(problem.rubric().get(i).point());
            }
        }
        return new AnswerView(a.getId(), a.getVersionNo(), a.sections(), a.getScore(), a.getStructureScore(),
                List.copyOf(hits), List.copyOf(missed));
    }
}
