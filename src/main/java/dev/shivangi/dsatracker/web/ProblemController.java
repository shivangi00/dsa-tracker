package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.security.TooManyRequestsException;
import dev.shivangi.dsatracker.service.AnalysisService;
import dev.shivangi.dsatracker.service.BadRequestException;
import dev.shivangi.dsatracker.service.DashboardService;
import dev.shivangi.dsatracker.service.ProblemService;
import dev.shivangi.dsatracker.service.ProblemViews;
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

/**
 * The tracker itself. Every endpoint needs a signed-in user ({@code SecurityConfig}), and
 * {@code @AuthenticationPrincipal} hands us who that is, so each call only sees its owner's data.
 */
@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService problems;
    private final ProblemViews views;
    private final DashboardService dashboard;
    private final AnalysisService analysis;
    private final ApproachRecommender approaches;

    public ProblemController(ProblemService problems, ProblemViews views, DashboardService dashboard,
                             AnalysisService analysis, ApproachRecommender approaches) {
        this.problems = problems;
        this.views = views;
        this.dashboard = dashboard;
        this.analysis = analysis;
        this.approaches = approaches;
    }

    /**
     * Marking a problem done. Name, link and difficulty come from NeetCode. {@code rating}: how it
     * went for you (Forgot / Hard / Medium / Easy = AGAIN / HARD / GOOD / EASY), which decides the
     * first revision; missing means GOOD. Code, if any, is saved as version 1 and analysed.
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
     * A revision: {@code rating} is Again / Hard / Good / Easy (the older {@code remembered},
     * true = Good and false = Again, still works). Notes and code are optional; code is saved as
     * version 1 of the revision and analysed.
     */
    public record ReviewRequest(Rating rating, Boolean remembered, @Size(max = 2000) String learnings,
                                @Size(max = ProblemService.MAX_CODE_LENGTH) String code, CodeLanguage codeLanguage) {
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

    /** An attempt's notes and drawing link, sent whole. */
    public record NotesRequest(
            @Size(max = 2000) String learnings,
            @Size(max = 500)
            @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https:// link")
            String excalidrawUrl) {
    }

    /** "Save as new version". {@code analyse}: also analyse it now (the page sends this after an Analyse). */
    public record VersionRequest(@Size(max = ProblemService.MAX_CODE_LENGTH) String code, CodeLanguage codeLanguage,
                                 boolean analyse) {
    }

    /**
     * Code to analyse before it's saved. {@code catalogId}, optional, is the NeetCode problem it
     * solves: the code must then match it, and the answer can suggest a better approach.
     */
    public record PreviewRequest(@Size(max = ProblemService.MAX_CODE_LENGTH) String code, CodeLanguage codeLanguage,
                                 Integer catalogId) {
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboard.build(me.id());
    }

    @PostMapping("/catalog/{catalogId}/done")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemView markDone(@AuthenticationPrincipal AuthUser me, @PathVariable int catalogId,
                                @Valid @RequestBody MarkDoneRequest req) {
        ProblemService.Saved saved = problems.markDone(me.id(), catalogId, req.learnings(), req.excalidrawUrl(),
                req.code(), req.codeLanguage(), req.rating());
        return view(me, analyseIfAny(me, saved));
    }

    @PostMapping("/problems/{id}/reviews")
    public ProblemView review(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                              @Valid @RequestBody ReviewRequest req) {
        ProblemService.Saved saved = problems.review(me.id(), id, req.resolved(), req.learnings(),
                req.code(), req.codeLanguage());
        return view(me, analyseIfAny(me, saved));
    }

    /** Edit an attempt's notes: only on the day of that attempt, otherwise 409. */
    @PatchMapping("/attempts/{id}")
    public ProblemView editNotes(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                 @Valid @RequestBody NotesRequest req) {
        return view(me, problems.editNotes(me.id(), id, req.learnings(), req.excalidrawUrl()));
    }

    @PostMapping("/attempts/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemView addVersion(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                  @Valid @RequestBody VersionRequest req) {
        ProblemService.Saved saved = problems.addVersion(me.id(), id, req.code(), req.codeLanguage());
        return view(me, req.analyse() ? analyseIfAny(me, saved) : saved.problem());
    }

    /** Analyse a saved version (on its attempt's day). */
    @PostMapping("/versions/{id}/analysis")
    public ProblemView analyseVersion(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return view(me, analysis.analyse(me.id(), id));
    }

    @DeleteMapping("/versions/{id}")
    public ProblemView deleteVersion(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return view(me, problems.deleteVersion(me.id(), id));
    }

    /** Analyse code that isn't saved yet: nothing is stored. */
    @PostMapping("/analysis/preview")
    public AnalysisView previewAnalysis(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PreviewRequest req) {
        return AnalysisView.of(analysis.preview(me.id(), req.code(), req.codeLanguage(), req.catalogId()),
                req.catalogId(), approaches);
    }

    /** Undo a "done" (and its whole history). */
    @DeleteMapping("/problems/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        problems.delete(me.id(), id);
    }

    /** Analyses the version just saved, if any. Over the daily limit, it stays saved to analyse later today. */
    private Problem analyseIfAny(AuthUser me, ProblemService.Saved saved) {
        if (saved.versionId() == null) {
            return saved.problem();
        }
        try {
            return analysis.analyse(me.id(), saved.versionId());
        } catch (TooManyRequestsException e) {
            return saved.problem();
        }
    }

    private ProblemView view(AuthUser me, Problem p) {
        return views.of(p, problems.plan(me.id()));
    }
}
