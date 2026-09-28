package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.security.TooManyRequestsException;
import dev.shivangi.dsatracker.service.AnalysisService;
import dev.shivangi.dsatracker.service.BadRequestException;
import dev.shivangi.dsatracker.service.DashboardService;
import dev.shivangi.dsatracker.service.ProblemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The tracker itself. Every endpoint needs a signed-in user ({@code SecurityConfig}), and
 * {@code @AuthenticationPrincipal} hands us who that is, so each call only sees its owner's data.
 */
@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService problems;
    private final DashboardService dashboard;
    private final AnalysisService analysis;
    private final ApproachRecommender approaches;
    private final SpacedRepetitionPolicy policy;
    private final Clock clock;

    public ProblemController(ProblemService problems, DashboardService dashboard, AnalysisService analysis,
                             ApproachRecommender approaches, SpacedRepetitionPolicy policy, Clock clock) {
        this.analysis = analysis;
        this.approaches = approaches;
        this.policy = policy;
        this.clock = clock;
        this.problems = problems;
        this.dashboard = dashboard;
    }

    /**
     * What you type when marking a problem done. Name, link and difficulty come from NeetCode.
     * {@code rating}: how it went for you (Forgot / Hard / Medium / Easy = AGAIN / HARD / GOOD / EASY);
     * it decides the first review. Missing means GOOD.
     */
    public record MarkDoneRequest(
            @NotBlank @Size(max = 2000) String learnings,
            @Size(max = 500)
            @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https:// link")
            String excalidrawUrl,
            @Size(max = ProblemService.MAX_CODE_LENGTH) String code,
            CodeLanguage codeLanguage,
            Rating rating) {
    }

    /**
     * Code to analyse before it's saved (the Mark as done window). {@code catalogId}, optional, is the
     * NeetCode problem it solves, so the answer can suggest a better approach.
     */
    public record PreviewRequest(@Size(max = ProblemService.MAX_CODE_LENGTH) String code, CodeLanguage codeLanguage,
                                 Integer catalogId) {
    }

    /** Edited notes and code. Sent whole: the page always has every field. */
    public record NotesRequest(
            @NotBlank @Size(max = 2000) String learnings,
            @Size(max = 500)
            @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https:// link")
            String excalidrawUrl,
            @Size(max = ProblemService.MAX_CODE_LENGTH) String code,
            CodeLanguage codeLanguage) {
    }

    /**
     * {@code rating}: Again / Hard / Good / Easy. The older {@code remembered} (true = Good,
     * false = Again) is still accepted so existing clients keep working.
     */
    public record ReviewRequest(Rating rating, Boolean remembered) {
        Rating resolved() {
            if (rating != null) {
                return rating;
            }
            if (remembered != null) {
                return remembered ? Rating.GOOD : Rating.AGAIN;
            }
            throw new BadRequestException("Choose Again, Hard, Good or Easy");
        }
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboard.build(me.id());
    }

    @PostMapping("/catalog/{catalogId}/done")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemView markDone(@AuthenticationPrincipal AuthUser me, @PathVariable int catalogId,
                                @Valid @RequestBody MarkDoneRequest req) {
        Problem saved = problems.markDone(me.id(), catalogId, req.learnings(), req.excalidrawUrl(),
                req.code(), req.codeLanguage(), req.rating());
        if (saved.getCode() != null) {
            try {
                saved = analysis.analyse(me.id(), saved.getId());   // saved with its analysis
            } catch (TooManyRequestsException e) {
                // Over the daily analysis limit: the problem and code are saved; analyse later today.
            }
        }
        return view(saved);
    }

    @PostMapping("/problems/{id}/reviews")
    public ProblemView review(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                              @Valid @RequestBody ReviewRequest req) {
        var updated = problems.review(me.id(), id, req.resolved());
        return view(updated);
    }

    /** Edit notes and code: only on the day the problem was solved, otherwise 409. */
    @PatchMapping("/problems/{id}/notes")
    public ProblemView editNotes(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                 @Valid @RequestBody NotesRequest req) {
        return view(problems.updateNotes(me.id(), id, req.learnings(), req.excalidrawUrl(),
                req.code(), req.codeLanguage()));
    }

    /** Analyse code that isn't saved yet: nothing is stored. */
    @PostMapping("/analysis/preview")
    public AnalysisView previewAnalysis(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PreviewRequest req) {
        return AnalysisView.of(analysis.preview(me.id(), req.code(), req.codeLanguage()), req.catalogId(), approaches);
    }

    /** Analyse the saved code's time and space complexity. */
    @PostMapping("/problems/{id}/analysis")
    public ProblemView analyse(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return view(analysis.analyse(me.id(), id));
    }

    private ProblemView view(Problem p) {
        return ProblemView.of(p, 0, LocalDate.now(clock), approaches, policy);
    }

    /** Undo a "done" (and its reviews). */
    @DeleteMapping("/problems/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        problems.delete(me.id(), id);
    }
}
