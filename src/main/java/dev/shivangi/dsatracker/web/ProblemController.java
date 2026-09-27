package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.security.AuthUser;
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
    private final DashboardService dashboard;

    public ProblemController(ProblemService problems, DashboardService dashboard) {
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
        return ProblemView.of(saved, 0);
    }

    @PostMapping("/problems/{id}/reviews")
    public ProblemView review(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                              @Valid @RequestBody ReviewRequest req) {
        var updated = problems.review(me.id(), id, req.remembered());
        return ProblemView.of(updated, 0);
    }

    /** Undo a "done" (and its reviews). */
    @DeleteMapping("/problems/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        problems.delete(me.id(), id);
    }
}
