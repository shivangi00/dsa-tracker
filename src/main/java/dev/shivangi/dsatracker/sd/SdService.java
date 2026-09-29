package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.config.AppProperties;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.service.BadRequestException;
import dev.shivangi.dsatracker.service.ConflictException;
import dev.shivangi.dsatracker.service.NotFoundException;
import dev.shivangi.dsatracker.service.Plan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The system design tracker, on the same 100-day plan as the NeetCode tracker:
 * <ul>
 *   <li><b>Topics</b>: after studying a topic on Hello Interview, a 5-question quiz marks it studied;
 *       three revisions follow, each another quiz. The score decides the rating (and so the next gap).</li>
 *   <li><b>Problems</b>: write your design in five parts; it's scored for coverage (free, built in)
 *       and you rate how it went. Two revisions follow, each a fresh design.</li>
 * </ul>
 * New topics and problems stop on day 85, as for NeetCode, so all revisions finish by day 100.
 * Every sitting is editable on its own day and frozen after.
 */
@Service
public class SdService {

    private final SdItemRepository items;
    private final SdAttemptRepository attempts;
    private final SdAnswerRepository answers;
    private final UserRepository users;
    private final SdCatalog catalog;
    private final Quiz quiz;
    private final DesignScorer scorer;
    private final AppProperties props;
    private final Clock clock;
    private final Map<SdKind, SpacedRepetitionPolicy> policies = new EnumMap<>(SdKind.class);

    public SdService(SdItemRepository items, SdAttemptRepository attempts, SdAnswerRepository answers,
                     UserRepository users, SdCatalog catalog, Quiz quiz, DesignScorer scorer,
                     AppProperties props, Clock clock) {
        this.items = items;
        this.attempts = attempts;
        this.answers = answers;
        this.users = users;
        this.catalog = catalog;
        this.quiz = quiz;
        this.scorer = scorer;
        this.props = props;
        this.clock = clock;
        for (SdKind kind : SdKind.values()) {
            policies.put(kind, new SpacedRepetitionPolicy(kind.revisions()));
        }
    }

    public Plan plan(Long userId) {
        LocalDate start = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user")).getStartDate();
        return Plan.of(start, props.planDays(), props.lastNewProblemDay());
    }

    // ---------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public SdViews.Dashboard dashboard(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Plan plan = plan(userId);
        List<SdItem> mine = items.findByUserId(userId);
        Map<String, SdViews.Item> views = itemViews(mine, plan, today);

        List<SdViews.TopicRow> topicRows = new ArrayList<>();
        for (SdCatalog.Topic t : catalog.topics()) {
            topicRows.add(new SdViews.TopicRow(t.key(), t.section(), t.name(), t.url(), views.get(id(SdKind.TOPIC, t.key()))));
        }
        List<SdViews.ProblemRow> problemRows = new ArrayList<>();
        for (SdCatalog.Problem p : catalog.problems()) {
            problemRows.add(new SdViews.ProblemRow(p.key(), p.level(), p.name(), p.url(), p.scored(),
                    views.get(id(SdKind.PROBLEM, p.key()))));
        }

        List<SdViews.DueRow> due = new ArrayList<>();
        int topicsStudied = 0, topicsRevised = 0, problemsDesigned = 0, problemsRevised = 0;
        int quizRight = 0, quizTotal = 0;
        List<Double> bestScores = new ArrayList<>();
        for (SdViews.Item v : views.values()) {
            if (v.kind() == SdKind.TOPIC) {
                topicsStudied++;
                topicsRevised += v.fullyRevised() ? 1 : 0;
                for (SdViews.AttemptView a : v.attempts()) {
                    if (a.quizTotal() != null) {
                        quizRight += a.quizScore();
                        quizTotal += a.quizTotal();
                    }
                }
            } else {
                problemsDesigned++;
                problemsRevised += v.fullyRevised() ? 1 : 0;
                if (v.bestScore() != null) {
                    bestScores.add(v.bestScore());
                }
            }
            if (v.dueToday()) {
                due.add(new SdViews.DueRow(v.kind(), v.key(), catalog.name(v.kind(), v.key()), v.id(),
                        Math.min(v.revisionsDone() + 1, v.revisions()), v.overdueDays()));
            }
        }
        due.sort(Comparator.comparingLong(SdViews.DueRow::overdueDays).reversed()
                .thenComparing(SdViews.DueRow::kind).thenComparing(SdViews.DueRow::name));

        Integer accuracy = quizTotal == 0 ? null : Math.round(100f * quizRight / quizTotal);
        Double average = bestScores.isEmpty() ? null
                : Math.round(bestScores.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
        SdViews.Stats stats = new SdViews.Stats(topicsStudied, catalog.topics().size(), topicsRevised,
                problemsDesigned, catalog.problems().size(), problemsRevised, due.size(), accuracy, average);

        Map<SdKind, Map<Rating, Integer>> firstGaps = new EnumMap<>(SdKind.class);
        policies.forEach((kind, policy) -> firstGaps.put(kind, policy.firstGaps(today, plan.end())));
        SdViews.PlanInfo planInfo = new SdViews.PlanInfo(plan.dayOf(today), props.planDays(), plan.start(), plan.end(),
                plan.lastNewDay(), props.lastNewProblemDay(), plan.newProblemsOpen(today));
        return new SdViews.Dashboard(today, planInfo, stats, topicRows, problemRows, due, firstGaps);
    }

    @Transactional(readOnly = true)
    public SdViews.Item item(Long userId, long itemId) {
        return view(find(userId, itemId));
    }

    public SdViews.ProblemDetail problem(String key) {
        return SdViews.ProblemDetail.of(requireProblem(key));
    }

    // ---------------------------------------------------------------- topics: quizzes

    /** A quiz for a topic: marks it studied (first time), counts as the due revision, or is practice. */
    @Transactional(readOnly = true)
    public SdViews.QuizView quiz(Long userId, String topicKey) {
        SdCatalog.Topic topic = requireTopic(topicKey);
        SdItem item = items.findByUserIdAndKindAndItemKey(userId, SdKind.TOPIC, topicKey).orElse(null);
        LocalDate today = LocalDate.now(clock);
        SdViews.QuizMode mode = mode(item, plan(userId), today);
        List<String> past = item == null ? List.of()
                : attempts.findByItemIdOrderByRevisionAscTryNoAsc(item.getId()).stream()
                .sorted(Comparator.comparing(SdAttempt::getAttemptedOn).thenComparing(SdAttempt::getId))
                .map(SdAttempt::getQuizDetail).toList();
        return new SdViews.QuizView(topic.key(), topic.name(), topic.url(), mode, revisionFor(item, mode),
                quiz.pick(topic, past));
    }

    /** Grades a quiz and, unless it's practice, saves it: the score sets the rating and the next review. */
    @Transactional
    public SdViews.QuizResult submitQuiz(Long userId, String topicKey, List<Quiz.Answer> given, String notes) {
        SdCatalog.Topic topic = requireTopic(topicKey);
        String cleanNotes = checkNotes(notes);
        Quiz.Result result;
        try {
            result = quiz.grade(topic, given);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        LocalDate today = LocalDate.now(clock);
        Plan plan = plan(userId);
        SdItem item = items.findByUserIdAndKindAndItemKey(userId, SdKind.TOPIC, topicKey).orElse(null);
        SdViews.QuizMode mode = mode(item, plan, today);

        if (mode == SdViews.QuizMode.PRACTICE) {
            return new SdViews.QuizResult(result.correct(), result.total(), result.percent(), result.rating(), mode,
                    false, result.questions(), item == null ? null : view(item));
        }
        SdAttempt attempt = mode == SdViews.QuizMode.FIRST
                ? start(userId, SdKind.TOPIC, topicKey, result.rating(), cleanNotes, null, today, plan)
                : revise(item, result.rating(), cleanNotes, null, today, plan);
        attempt.recordQuiz(result);
        SdItem saved = items.findById(attempt.getItemId()).orElseThrow();
        return new SdViews.QuizResult(result.correct(), result.total(), result.percent(), result.rating(), mode,
                true, result.questions(), view(saved));
    }

    // ---------------------------------------------------------------- problems: designs

    /** First design of a problem: your rating, and at least one of notes, a drawing link or a written design. */
    @Transactional
    public SdViews.Item markDesigned(Long userId, String problemKey, Rating rating, String notes, String excalidrawUrl,
                                     DesignSections sections) {
        SdCatalog.Problem problem = requireProblem(problemKey);
        LocalDate today = LocalDate.now(clock);
        Plan plan = plan(userId);
        if (items.findByUserIdAndKindAndItemKey(userId, SdKind.PROBLEM, problemKey).isPresent()) {
            throw new ConflictException("\"" + problem.name() + "\" is already designed. Revise it when it's due.");
        }
        if (!plan.newProblemsOpen(today)) {
            throw new ConflictException(newItemsClosed());
        }
        String cleanNotes = checkNotes(notes);
        String drawing = checkDrawing(excalidrawUrl);
        DesignSections design = checkSections(sections);
        if (cleanNotes == null && drawing == null && design == null) {
            throw new BadRequestException("Write your design, add notes or link your drawing first");
        }
        SdAttempt attempt = start(userId, SdKind.PROBLEM, problemKey, rating == null ? Rating.GOOD : rating,
                cleanNotes, drawing, today, plan);
        if (design != null) {
            answers.save(new SdAnswer(attempt.getId(), 1, design, scorer.score(problem, design)));
        }
        return view(items.findById(attempt.getItemId()).orElseThrow());
    }

    /** A problem's revision: a fresh design (optional), notes, and how it went. Topics revise with a quiz. */
    @Transactional
    public SdViews.Item reviseProblem(Long userId, long itemId, Rating rating, String notes, String excalidrawUrl,
                                      DesignSections sections) {
        SdItem item = find(userId, itemId);
        if (item.getKind() != SdKind.PROBLEM) {
            throw new BadRequestException("Topics are revised with their quiz");
        }
        if (rating == null) {
            throw new BadRequestException("Choose Again, Hard, Good or Easy");
        }
        String cleanNotes = checkNotes(notes);
        String drawing = checkDrawing(excalidrawUrl);
        DesignSections design = checkSections(sections);
        SdAttempt attempt = revise(item, rating, cleanNotes, drawing, LocalDate.now(clock), plan(userId));
        if (design != null) {
            answers.save(new SdAnswer(attempt.getId(), 1, design, scorer.score(requireProblem(item.getItemKey()), design)));
        }
        return view(item);
    }

    /** Edits a sitting's notes and drawing link, on its day only. */
    @Transactional
    public SdViews.Item editNotes(Long userId, long attemptId, String notes, String excalidrawUrl) {
        SdAttempt attempt = ownedAttempt(userId, attemptId);
        String cleanNotes = checkNotes(notes);
        String drawing = checkDrawing(excalidrawUrl);
        try {
            attempt.editNotes(cleanNotes, drawing, LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return view(items.findById(attempt.getItemId()).orElseThrow());
    }

    /** "Save": another version of the design in this sitting (its day only), scored. */
    @Transactional
    public SdViews.Item addAnswer(Long userId, long attemptId, DesignSections sections) {
        SdAttempt attempt = ownedAttempt(userId, attemptId);
        requireEditable(attempt);
        SdItem item = items.findById(attempt.getItemId()).orElseThrow();
        if (item.getKind() != SdKind.PROBLEM) {
            throw new BadRequestException("Only design problems keep written designs");
        }
        DesignSections design = checkSections(sections);
        if (design == null) {
            throw new BadRequestException("Write at least one part of your design first");
        }
        if (answers.countByAttemptId(attemptId) >= SdAnswer.MAX_PER_ATTEMPT) {
            throw new ConflictException("You can keep at most " + SdAnswer.MAX_PER_ATTEMPT
                    + " versions per attempt. Delete one to save another.");
        }
        int next = answers.maxVersionNo(attemptId) + 1;
        answers.save(new SdAnswer(attemptId, next, design, scorer.score(requireProblem(item.getItemKey()), design)));
        return view(item);
    }

    @Transactional
    public SdViews.Item deleteAnswer(Long userId, long answerId) {
        SdAnswer answer = answers.findOwned(answerId, userId)
                .orElseThrow(() -> new NotFoundException("No saved design with id " + answerId));
        SdAttempt attempt = attempts.findById(answer.getAttemptId()).orElseThrow();
        requireEditable(attempt);
        answers.delete(answer);
        return view(items.findById(attempt.getItemId()).orElseThrow());
    }

    /** "Analyse": scores a design without saving it. */
    public SdViews.ScoreView preview(String problemKey, DesignSections sections) {
        SdCatalog.Problem problem = requireProblem(problemKey);
        DesignSections design = checkSections(sections);
        if (design == null) {
            throw new BadRequestException("Write at least one part of your design first");
        }
        return SdViews.ScoreView.of(design, scorer.score(problem, design));
    }

    /** Undo: removes a topic or problem and its whole history. */
    @Transactional
    public void delete(Long userId, long itemId) {
        items.delete(find(userId, itemId));
    }

    // ---------------------------------------------------------------- the schedule

    private SdAttempt start(Long userId, SdKind kind, String key, Rating rating, String notes, String drawing,
                            LocalDate today, Plan plan) {
        SpacedRepetitionPolicy policy = policies.get(kind);
        ScheduleState s = policy.spread(policy.onFirstSolve(today, rating, plan.end()), today, dueCounts(userId, today));
        SdItem item = items.save(new SdItem(userId, kind, key, today, rating, s));
        return attempts.save(new SdAttempt(item.getId(), SdAttempt.FIRST, 1, today, rating, notes, drawing));
    }

    private SdAttempt revise(SdItem item, Rating rating, String notes, String drawing, LocalDate today, Plan plan) {
        SpacedRepetitionPolicy policy = policies.get(item.getKind());
        ScheduleState before = item.schedule();
        ScheduleState after;
        try {
            after = policy.onReview(before, today, rating, plan.end());
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        item.apply(policy.spread(after, today, dueCounts(item.getUserId(), today)));
        int revision = Math.min(before.reps() + 1, item.getKind().revisions());
        int tryNo = attempts.countByItemIdAndRevision(item.getId(), revision) + 1;
        return attempts.save(new SdAttempt(item.getId(), revision, tryNo, today, rating, notes, drawing));
    }

    private static SdViews.QuizMode mode(SdItem item, Plan plan, LocalDate today) {
        if (item == null) {
            return plan.newProblemsOpen(today) ? SdViews.QuizMode.FIRST : SdViews.QuizMode.PRACTICE;
        }
        return item.schedule().isDueOn(today) ? SdViews.QuizMode.REVIEW : SdViews.QuizMode.PRACTICE;
    }

    private static int revisionFor(SdItem item, SdViews.QuizMode mode) {
        return mode == SdViews.QuizMode.REVIEW ? Math.min(item.getReps() + 1, item.getKind().revisions()) : 0;
    }

    private Map<LocalDate, Integer> dueCounts(Long userId, LocalDate today) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Object[] row : items.dueCountsBetween(userId, today.plusDays(1), today.plusDays(SpacedRepetitionPolicy.MAX_GAP))) {
            counts.put((LocalDate) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    // ---------------------------------------------------------------- views

    private SdViews.Item view(SdItem item) {
        return itemViews(List.of(item), plan(item.getUserId()), LocalDate.now(clock)).get(id(item.getKind(), item.getItemKey()));
    }

    private Map<String, SdViews.Item> itemViews(List<SdItem> list, Plan plan, LocalDate today) {
        if (list.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = list.stream().map(SdItem::getId).toList();
        Map<Long, List<SdAttempt>> byItem = attempts.findByItemIdInOrderByItemIdAscRevisionAscTryNoAsc(ids).stream()
                .collect(Collectors.groupingBy(SdAttempt::getItemId));
        List<Long> attemptIds = byItem.values().stream().flatMap(List::stream).map(SdAttempt::getId).toList();
        Map<Long, List<SdAnswer>> byAttempt = attemptIds.isEmpty() ? Map.of()
                : answers.findByAttemptIdInOrderByAttemptIdAscVersionNoAsc(attemptIds).stream()
                .collect(Collectors.groupingBy(SdAnswer::getAttemptId));
        return list.stream().collect(Collectors.toMap(i -> id(i.getKind(), i.getItemKey()),
                i -> SdViews.item(i, byItem.getOrDefault(i.getId(), List.of()), byAttempt, catalog,
                        policies.get(i.getKind()), plan, today),
                (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static String id(SdKind kind, String key) {
        return kind + ":" + key;
    }

    // ---------------------------------------------------------------- lookups and checks

    private SdCatalog.Topic requireTopic(String key) {
        return catalog.topic(key).orElseThrow(() -> new NotFoundException("No system design topic \"" + key + "\""));
    }

    private SdCatalog.Problem requireProblem(String key) {
        return catalog.problem(key).orElseThrow(() -> new NotFoundException("No system design problem \"" + key + "\""));
    }

    private SdItem find(Long userId, long itemId) {
        return items.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new NotFoundException("No system design item with id " + itemId));
    }

    private SdAttempt ownedAttempt(Long userId, long attemptId) {
        return attempts.findOwned(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("No attempt with id " + attemptId));
    }

    private void requireEditable(SdAttempt attempt) {
        try {
            attempt.requireEditable(LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    private String newItemsClosed() {
        return "New topics and problems stop on day " + props.lastNewProblemDay() + " so every revision fits before day "
                + props.planDays() + ". The rest of the plan is for revisions (quizzes can still be practised).";
    }

    private static String checkNotes(String notes) {
        String n = notes == null || notes.isBlank() ? null : notes.strip();
        if (n != null && n.length() > 2000) {
            throw new BadRequestException("Notes can be at most 2000 characters");
        }
        return n;
    }

    private static String checkDrawing(String url) {
        String d = url == null || url.isBlank() ? null : url.strip();
        if (d != null && (d.length() > 500 || !d.matches("^https://\\S+$"))) {
            throw new BadRequestException("The drawing link must start with https://");
        }
        return d;
    }

    /** Null when nothing is written. */
    private static DesignSections checkSections(DesignSections sections) {
        if (sections == null) {
            return null;
        }
        DesignSections s = sections.normalised();
        if (s.isEmpty()) {
            return null;
        }
        if (s.parts().anyMatch(p -> p != null && p.length() > DesignSections.MAX_SECTION)) {
            throw new BadRequestException("Each part of the design can be at most " + DesignSections.MAX_SECTION + " characters");
        }
        return s;
    }
}
