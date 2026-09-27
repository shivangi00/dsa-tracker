package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.service.AnalysisService;
import dev.shivangi.dsatracker.service.DashboardService;
import dev.shivangi.dsatracker.service.ProblemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private final Clock clock;

    public ProblemController(ProblemService problems, DashboardService dashboard, AnalysisService analysis, Clock clock) {
        this.analysis = analysis;
        this.clock = clock;
        this.problems = problems;
        this.dashboard = dashboard;
    }

    /** What you type when marking a problem done. Name, link and difficulty come from NeetCode. */
    public record MarkDoneRequest(
            @NotBlank @Size(max = 2000) String learnings,
            @Size(max = 500)
            @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https:// link")
            String excalidrawUrl) {
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

    /** {@code remembered}: true if you could solve it again from memory. */
    public record ReviewRequest(@NotNull Boolean remembered) {
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboard.build(me.id());
    }

    @PostMapping("/catalog/{catalogId}/done")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemView markDone(@AuthenticationPrincipal AuthUser me, @PathVariable int catalogId,
                                @Valid @RequestBody MarkDoneRequest req) {
        var saved = problems.markDone(me.id(), catalogId, req.learnings(), req.excalidrawUrl());
        return view(saved);
    }

    @PostMapping("/problems/{id}/reviews")
    public ProblemView review(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                              @Valid @RequestBody ReviewRequest req) {
        var updated = problems.review(me.id(), id, req.remembered());
        return view(updated);
    }

    /** Edit notes and code: only on the day the problem was solved, otherwise 409. */
    @PatchMapping("/problems/{id}/notes")
    public ProblemView editNotes(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                 @Valid @RequestBody NotesRequest req) {
        return view(problems.updateNotes(me.id(), id, req.learnings(), req.excalidrawUrl(),
                req.code(), req.codeLanguage()));
    }

    /** Analyse the saved code's time and space complexity. */
    @PostMapping("/problems/{id}/analysis")
    public ProblemView analyse(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return view(analysis.analyse(me.id(), id));
    }

    private ProblemView view(Problem p) {
        return ProblemView.of(p, 0, LocalDate.now(clock));
    }

    /** Undo a "done" (and its reviews). */
    @DeleteMapping("/problems/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        problems.delete(me.id(), id);
    }
}
