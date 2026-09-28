package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.domain.CatalogProblem;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.RevisionAttempt;
import dev.shivangi.dsatracker.domain.RevisionAttemptRepository;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Marking problems done, reviewing them, undoing. Every method acts for one user only. */
@Service
public class ProblemService {

    private final CatalogRepository catalogs;
    private final ProblemRepository problems;
    private final RevisionAttemptRepository attempts;
    private final SpacedRepetitionPolicy policy;
    private final Clock clock;

    public ProblemService(CatalogRepository catalogs, ProblemRepository problems,
                          RevisionAttemptRepository attempts, SpacedRepetitionPolicy policy, Clock clock) {
        this.catalogs = catalogs;
        this.problems = problems;
        this.attempts = attempts;
        this.policy = policy;
        this.clock = clock;
    }

    /** Marks a NeetCode 150 problem as done today, without code. */
    @Transactional
    public Problem markDone(Long userId, int catalogId, String learnings, String excalidrawUrl) {
        return markDone(userId, catalogId, learnings, excalidrawUrl, null, null, Rating.GOOD);
    }

    /**
     * Marks a NeetCode 150 problem as done today, optionally with your code. {@code rating} is how it
     * went for you (null counts as GOOD, "Medium"); it sets when the first review is due.
     */
    @Transactional
    public Problem markDone(Long userId, int catalogId, String learnings, String excalidrawUrl,
                            String code, CodeLanguage language, Rating rating) {
        Rating first = rating == null ? Rating.GOOD : rating;
        LocalDate today = LocalDate.now(clock);

        CatalogProblem catalog = catalogs.findById(catalogId)
                .orElseThrow(() -> new NotFoundException("No NeetCode problem with id " + catalogId));
        if (problems.existsByUserIdAndCatalogId(userId, catalogId)) {
            throw new ConflictException("\"" + catalog.getName() + "\" is already marked done");
        }

        String notes = learnings == null ? "" : learnings.trim();
        if (notes.isEmpty()) {
            throw new BadRequestException("Write down what you learned");
        }
        String drawing = blankToNull(excalidrawUrl);
        if (drawing != null && !drawing.startsWith("https://")) {
            throw new BadRequestException("The Excalidraw link must start with https://");
        }

        String solution = checkCode(code, language);

        // Always today: past days can't be back-filled.
        Problem problem = new Problem(userId, catalog, notes, drawing, today, first, policy.onFirstSolve(today, first));
        if (solution != null) {
            problem.editNotes(notes, drawing, solution, language, today);
        }
        return problems.save(problem);
    }

    /** Records a review: Again, Hard, Good or Easy. */
    @Transactional
    public Problem review(Long userId, long problemId, Rating rating) {
        LocalDate today = LocalDate.now(clock);
        Problem problem = problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));

        ScheduleState before = problem.schedule();
        ScheduleState after;
        try {
            after = policy.onReview(before, today, rating);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }

        problem.apply(after);
        attempts.save(new RevisionAttempt(problem.getId(), today, rating,
                before.intervalDays(), after.intervalDays()));
        return problem;
    }

    public static final int MAX_CODE_LENGTH = 10_000;

    /**
     * Saves edited notes and code. Allowed only on the day the problem was solved; after that the
     * notes are frozen (409).
     */
    @Transactional
    public Problem updateNotes(Long userId, long problemId, String learnings, String excalidrawUrl,
                               String code, CodeLanguage language) {
        Problem problem = find(userId, problemId);
        String notes = learnings == null ? "" : learnings.trim();
        if (notes.isEmpty()) {
            throw new BadRequestException("Write down what you learned");
        }
        String drawing = blankToNull(excalidrawUrl);
        if (drawing != null && !drawing.startsWith("https://")) {
            throw new BadRequestException("The Excalidraw link must start with https://");
        }
        String solution = checkCode(code, language);
        try {
            problem.editNotes(notes, drawing, solution, solution == null ? null : language, LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return problem;
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

    /** The code to analyse, as saved. Read in its own short transaction (see AnalysisService). */
    public record CodeToAnalyse(String code, CodeLanguage language) {
    }

    @Transactional(readOnly = true)
    public CodeToAnalyse codeToAnalyse(Long userId, long problemId) {
        Problem problem = find(userId, problemId);
        if (!problem.isEditableOn(LocalDate.now(clock))) {
            throw new ConflictException("Notes are frozen: code can only be analysed on the day you solved the problem");
        }
        if (problem.getCode() == null) {
            throw new BadRequestException("Add your code first, then analyse it");
        }
        return new CodeToAnalyse(problem.getCode(), problem.getCodeLanguage());
    }

    /**
     * Stores an analysis, but only if the code is still the code that was analysed: if it was
     * edited meanwhile (another tab), the result would describe the wrong code.
     */
    @Transactional
    public Problem saveAnalysis(Long userId, long problemId, CodeToAnalyse analysed, ComplexityAnalysis result) {
        Problem problem = find(userId, problemId);
        if (!Objects.equals(problem.getCode(), analysed.code()) || problem.getCodeLanguage() != analysed.language()) {
            throw new ConflictException("The code changed while it was being analysed. Analyse it again.");
        }
        try {
            problem.recordAnalysis(result, clock.instant(), LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return problem;
    }

    private Problem find(Long userId, long problemId) {
        return problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));
    }

    /** Undo: removes the entry and its review history, so the problem is "to do" again. */
    @Transactional
    public void delete(Long userId, long problemId) {
        Problem problem = problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));
        problems.delete(problem);   // its review history goes too (ON DELETE CASCADE)
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
