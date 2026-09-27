package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.domain.CatalogProblem;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.RevisionAttempt;
import dev.shivangi.dsatracker.domain.RevisionAttemptRepository;
import dev.shivangi.dsatracker.repetition.ScheduleState;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

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

    /** Marks a NeetCode 150 problem as done today. The first review is due tomorrow. */
    @Transactional
    public Problem markDone(Long userId, int catalogId, String learnings, String excalidrawUrl) {
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

        // Always today: past days can't be back-filled.
        return problems.save(new Problem(userId, catalog, notes, drawing, today, policy.onFirstSolve(today)));
    }

    /** Records a review: remembered (true) or forgot (false). */
    @Transactional
    public Problem review(Long userId, long problemId, boolean remembered) {
        LocalDate today = LocalDate.now(clock);
        Problem problem = problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));

        ScheduleState before = problem.schedule();
        ScheduleState after;
        try {
            after = policy.onReview(before, today, remembered);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }

        problem.apply(after);
        attempts.save(new RevisionAttempt(problem.getId(), today, remembered,
                before.intervalDays(), after.intervalDays()));
        return problem;
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
