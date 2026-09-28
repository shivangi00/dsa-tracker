package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.config.AppProperties;
import dev.shivangi.dsatracker.domain.Attempt;
import dev.shivangi.dsatracker.domain.AttemptRepository;
import dev.shivangi.dsatracker.domain.CatalogProblem;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.RevisionAttempt;
import dev.shivangi.dsatracker.domain.RevisionAttemptRepository;
import dev.shivangi.dsatracker.domain.SolutionVersion;
import dev.shivangi.dsatracker.domain.SolutionVersionRepository;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Solving, revising and the history of attempts. Every sitting is an {@link Attempt} (the solve
 * day, then revisions 1–3) with optional notes and saved versions of your code; each attempt is
 * editable on its own day and frozen after, so nothing is ever overwritten across days.
 */
@Service
public class ProblemService {

    public static final int MAX_CODE_LENGTH = 10_000;

    private final CatalogRepository catalogs;
    private final ProblemRepository problems;
    private final RevisionAttemptRepository reviewLog;
    private final AttemptRepository attempts;
    private final SolutionVersionRepository versions;
    private final UserRepository users;
    private final SpacedRepetitionPolicy policy;
    private final ApproachRecommender approaches;
    private final AppProperties props;
    private final Clock clock;

    public ProblemService(CatalogRepository catalogs, ProblemRepository problems, RevisionAttemptRepository reviewLog,
                          AttemptRepository attempts, SolutionVersionRepository versions, UserRepository users,
                          SpacedRepetitionPolicy policy, ApproachRecommender approaches, AppProperties props,
                          Clock clock) {
        this.catalogs = catalogs;
        this.problems = problems;
        this.reviewLog = reviewLog;
        this.attempts = attempts;
        this.versions = versions;
        this.users = users;
        this.policy = policy;
        this.approaches = approaches;
        this.props = props;
        this.clock = clock;
    }

    /** What a write produced: the problem, and the new code version if one was saved (to analyse next). */
    public record Saved(Problem problem, Long versionId) {
    }

    public Plan plan(Long userId) {
        LocalDate start = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user")).getStartDate();
        return Plan.of(start, props.planDays(), props.lastNewProblemDay());
    }

    /**
     * Marks a NeetCode 150 problem as done today, with your notes and optionally your code (saved as
     * version 1). {@code rating} is how it went for you (null counts as GOOD, "Medium"); it sets the
     * first revision, capped so all three fit before the plan ends.
     */
    @Transactional
    public Saved markDone(Long userId, int catalogId, String learnings, String excalidrawUrl,
                          String code, CodeLanguage language, Rating rating) {
        LocalDate today = LocalDate.now(clock);
        Plan plan = plan(userId);
        if (!plan.newProblemsOpen(today)) {
            throw new ConflictException("New problems stop on day " + props.lastNewProblemDay() + " (" + plan.lastNewDay()
                    + ") so every problem's revisions fit before day " + props.planDays() + ". The rest of the plan is for revisions.");
        }
        CatalogProblem catalog = catalogs.findById(catalogId)
                .orElseThrow(() -> new NotFoundException("No NeetCode problem with id " + catalogId));
        if (problems.existsByUserIdAndCatalogId(userId, catalogId)) {
            throw new ConflictException("\"" + catalog.getName() + "\" is already marked done");
        }
        String notes = blankToNull(learnings);
        if (notes == null) {
            throw new BadRequestException("Write down what you learned");
        }
        String drawing = checkDrawing(excalidrawUrl);
        String solution = checkCode(code, language);
        requireSolutionTo(catalogId, solution, language);

        Rating first = rating == null ? Rating.GOOD : rating;
        ScheduleState schedule = policy.spread(policy.onFirstSolve(today, first, plan.end()), today, dueCounts(userId, today));
        Problem problem = problems.save(new Problem(userId, catalog, today, first, schedule));
        Attempt attempt = attempts.save(new Attempt(problem.getId(), Attempt.SOLVE, 1, today, first, notes, drawing));
        Long versionId = solution == null ? null
                : versions.save(new SolutionVersion(attempt.getId(), 1, solution, language)).getId();
        return new Saved(problem, versionId);
    }

    /**
     * A revision: your rating (Again repeats the revision tomorrow; Hard, Good or Easy completes it),
     * optional notes on what you noticed, and optionally your code, saved as version 1.
     */
    @Transactional
    public Saved review(Long userId, long problemId, Rating rating, String learnings, String code, CodeLanguage language) {
        LocalDate today = LocalDate.now(clock);
        Problem problem = find(userId, problemId);
        String notes = blankToNull(learnings);
        if (notes != null && notes.length() > 2000) {
            throw new BadRequestException("Notes can be at most 2000 characters");
        }
        String solution = checkCode(code, language);
        requireSolutionTo(problem.getCatalogId(), solution, language);

        ScheduleState before = problem.schedule();
        ScheduleState after;
        try {
            after = policy.onReview(before, today, rating, plan(userId).end());
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        after = policy.spread(after, today, dueCounts(userId, today));
        problem.apply(after);

        int revision = Math.min(before.reps() + 1, SpacedRepetitionPolicy.REVISIONS);
        int tryNo = attempts.countByProblemIdAndRevision(problemId, revision) + 1;
        Attempt attempt = attempts.save(new Attempt(problemId, revision, tryNo, today, rating, notes, null));
        reviewLog.save(new RevisionAttempt(problemId, today, rating, before.intervalDays(), after.intervalDays()));
        Long versionId = solution == null ? null
                : versions.save(new SolutionVersion(attempt.getId(), 1, solution, language)).getId();
        return new Saved(problem, versionId);
    }

    /** Edits an attempt's notes and drawing link, on its day only. Notes are required for the solve day. */
    @Transactional
    public Problem editNotes(Long userId, long attemptId, String learnings, String excalidrawUrl) {
        Attempt attempt = ownedAttempt(userId, attemptId);
        String notes = blankToNull(learnings);
        if (attempt.isSolve() && notes == null) {
            throw new BadRequestException("Write down what you learned");
        }
        if (notes != null && notes.length() > 2000) {
            throw new BadRequestException("Notes can be at most 2000 characters");
        }
        try {
            attempt.editNotes(notes, checkDrawing(excalidrawUrl), LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return problems.findById(attempt.getProblemId()).orElseThrow();
    }

    /** "Save as new version": keeps your code as another version of this attempt, on its day only. */
    @Transactional
    public Saved addVersion(Long userId, long attemptId, String code, CodeLanguage language) {
        Attempt attempt = ownedAttempt(userId, attemptId);
        requireEditable(attempt);
        String solution = checkCode(code, language);
        if (solution == null) {
            throw new BadRequestException("Add your code first");
        }
        Problem problem = problems.findById(attempt.getProblemId()).orElseThrow();
        requireSolutionTo(problem.getCatalogId(), solution, language);
        if (versions.countByAttemptId(attemptId) >= SolutionVersion.MAX_PER_ATTEMPT) {
            throw new ConflictException("You can keep at most " + SolutionVersion.MAX_PER_ATTEMPT
                    + " versions per attempt. Delete one to save another.");
        }
        int next = versions.maxVersionNo(attemptId) + 1;
        SolutionVersion saved = versions.save(new SolutionVersion(attemptId, next, solution, language));
        return new Saved(problem, saved.getId());
    }

    /** Deletes a saved version, on its attempt's day only. */
    @Transactional
    public Problem deleteVersion(Long userId, long versionId) {
        SolutionVersion version = ownedVersion(userId, versionId);
        Attempt attempt = attempts.findById(version.getAttemptId()).orElseThrow();
        requireEditable(attempt);
        versions.delete(version);
        return problems.findById(attempt.getProblemId()).orElseThrow();
    }

    /** Code saved under a NeetCode problem must look like a solution to it (LeetCode's function or class name). */
    public void requireSolutionTo(Integer catalogId, String code, CodeLanguage language) {
        if (code != null) {
            approaches.wrongProblem(catalogId, code, language).ifPresent(msg -> {
                throw new BadRequestException(msg);
            });
        }
    }

    /** Blank code means none; otherwise it must fit and have a language. Returns the code to store. */
    static String checkCode(String code, CodeLanguage language) {
        String solution = code == null || code.isBlank() ? null : code.stripTrailing();
        if (solution != null && solution.length() > MAX_CODE_LENGTH) {
            throw new BadRequestException("Code can be at most " + MAX_CODE_LENGTH + " characters");
        }
        if (solution != null && language == null) {
            throw new BadRequestException("Choose the language of your code");
        }
        return solution;
    }

    /** A saved version to analyse. Read in its own short transaction (see AnalysisService). */
    public record CodeToAnalyse(long versionId, String code, CodeLanguage language) {
    }

    @Transactional(readOnly = true)
    public CodeToAnalyse codeToAnalyse(Long userId, long versionId) {
        SolutionVersion version = ownedVersion(userId, versionId);
        requireEditable(attempts.findById(version.getAttemptId()).orElseThrow());
        return new CodeToAnalyse(versionId, version.getCode(), version.getCodeLanguage());
    }

    /** Stores the analysis of a saved version (versions never change, so it still describes the code). */
    @Transactional
    public Problem saveAnalysis(Long userId, long versionId, ComplexityAnalysis result) {
        SolutionVersion version = ownedVersion(userId, versionId);
        Attempt attempt = attempts.findById(version.getAttemptId()).orElseThrow();
        requireEditable(attempt);
        version.recordAnalysis(result, clock.instant());
        return problems.findById(attempt.getProblemId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Problem get(Long userId, long problemId) {
        return find(userId, problemId);
    }

    /** Undo: removes the entry and all its attempts, so the problem is "to do" again. */
    @Transactional
    public void delete(Long userId, long problemId) {
        problems.delete(find(userId, problemId));   // attempts, versions and reviews go too (ON DELETE CASCADE)
    }

    /** Reviews already due on each of the next few weeks' days, for spreading the workload. */
    private Map<LocalDate, Integer> dueCounts(Long userId, LocalDate today) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Object[] row : problems.dueCountsBetween(userId, today.plusDays(1), today.plusDays(SpacedRepetitionPolicy.MAX_GAP))) {
            counts.put((LocalDate) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private void requireEditable(Attempt attempt) {
        try {
            attempt.requireEditable(LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    private Problem find(Long userId, long problemId) {
        return problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));
    }

    private Attempt ownedAttempt(Long userId, long attemptId) {
        return attempts.findOwned(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("No attempt with id " + attemptId));
    }

    private SolutionVersion ownedVersion(Long userId, long versionId) {
        return versions.findOwned(versionId, userId)
                .orElseThrow(() -> new NotFoundException("No saved version with id " + versionId));
    }

    private static String checkDrawing(String url) {
        String drawing = blankToNull(url);
        if (drawing != null && !drawing.matches("^https://\\S+$")) {
            throw new BadRequestException("The Excalidraw link must start with https://");
        }
        return drawing;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
