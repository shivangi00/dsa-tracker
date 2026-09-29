package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.security.AuthUser;
import jakarta.validation.Valid;
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

import java.util.List;

/** The system design tracker's API. Signed-in only, like everything under /api. */
@RestController
@RequestMapping("/api/sd")
public class SdController {

    private final SdService sd;

    public SdController(SdService sd) {
        this.sd = sd;
    }

    public record QuizSubmission(List<Quiz.Answer> answers, @Size(max = 2000) String notes) {
    }

    /** First design of a problem. {@code rating}: Forgot / Hard / Medium / Easy (missing = Medium). */
    public record DesignRequest(Rating rating, @Size(max = 2000) String notes, @Size(max = 500) String excalidrawUrl,
                                DesignSections sections) {
    }

    public record NotesRequest(@Size(max = 2000) String notes, @Size(max = 500) String excalidrawUrl) {
    }

    public record SectionsRequest(DesignSections sections) {
    }

    public record PreviewRequest(String problemKey, DesignSections sections) {
    }

    @GetMapping("/dashboard")
    public SdViews.Dashboard dashboard(@AuthenticationPrincipal AuthUser me) {
        return sd.dashboard(me.id());
    }

    @GetMapping("/items/{id}")
    public SdViews.Item item(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return sd.item(me.id(), id);
    }

    /** The rubric and reference design of a problem (the page shows them behind a toggle). */
    @GetMapping("/problems/{key}")
    public SdViews.ProblemDetail problem(@PathVariable String key) {
        return sd.problem(key);
    }

    @GetMapping("/topics/{key}/quiz")
    public SdViews.QuizView quiz(@AuthenticationPrincipal AuthUser me, @PathVariable String key) {
        return sd.quiz(me.id(), key);
    }

    @PostMapping("/topics/{key}/quiz")
    public SdViews.QuizResult submitQuiz(@AuthenticationPrincipal AuthUser me, @PathVariable String key,
                                         @Valid @RequestBody QuizSubmission req) {
        return sd.submitQuiz(me.id(), key, req.answers(), req.notes());
    }

    @PostMapping("/problems/{key}/done")
    @ResponseStatus(HttpStatus.CREATED)
    public SdViews.Item markDesigned(@AuthenticationPrincipal AuthUser me, @PathVariable String key,
                                     @Valid @RequestBody DesignRequest req) {
        return sd.markDesigned(me.id(), key, req.rating(), req.notes(), req.excalidrawUrl(), req.sections());
    }

    @PostMapping("/items/{id}/reviews")
    public SdViews.Item revise(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                               @Valid @RequestBody DesignRequest req) {
        return sd.reviseProblem(me.id(), id, req.rating(), req.notes(), req.excalidrawUrl(), req.sections());
    }

    @PatchMapping("/attempts/{id}")
    public SdViews.Item editNotes(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                  @Valid @RequestBody NotesRequest req) {
        return sd.editNotes(me.id(), id, req.notes(), req.excalidrawUrl());
    }

    @PostMapping("/attempts/{id}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public SdViews.Item addAnswer(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                                  @RequestBody SectionsRequest req) {
        return sd.addAnswer(me.id(), id, req.sections());
    }

    @DeleteMapping("/answers/{id}")
    public SdViews.Item deleteAnswer(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return sd.deleteAnswer(me.id(), id);
    }

    /** Score a design without saving it. */
    @PostMapping("/score")
    public SdViews.ScoreView preview(@RequestBody PreviewRequest req) {
        return sd.preview(req.problemKey(), req.sections());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        sd.delete(me.id(), id);
    }
}
