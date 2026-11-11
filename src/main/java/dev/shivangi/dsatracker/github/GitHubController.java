package dev.shivangi.dsatracker.github;

import dev.shivangi.dsatracker.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Link a GitHub repository and push your solutions to it. Signed-in only, like all of /api. */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService github;

    public GitHubController(GitHubService github) {
        this.github = github;
    }

    /** @param branch optional: the repository's default branch when empty */
    public record ConnectRequest(@Size(max = 300) String repository, @Size(max = 300) String token,
                                 @Size(max = 255) String branch, boolean autoPush) {
    }

    public record AutoPushRequest(boolean autoPush) {
    }

    @GetMapping
    public GitHubService.Status status(@AuthenticationPrincipal AuthUser me) {
        return github.status(me.id());
    }

    @PutMapping
    public GitHubService.Status connect(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ConnectRequest req) {
        return github.connect(me.id(), req.repository(), req.token(), req.branch(), req.autoPush());
    }

    @PatchMapping
    public GitHubService.Status autoPush(@AuthenticationPrincipal AuthUser me, @RequestBody AutoPushRequest req) {
        return github.setAutoPush(me.id(), req.autoPush());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthUser me) {
        github.disconnect(me.id());
    }

    @PostMapping("/problems/{problemId}/push")
    public GitHubService.PushView push(@AuthenticationPrincipal AuthUser me, @PathVariable long problemId) {
        return github.pushProblem(me.id(), problemId);
    }

    @PostMapping("/push-all")
    public GitHubService.PushView pushAll(@AuthenticationPrincipal AuthUser me) {
        return github.pushAll(me.id());
    }

    /** GitHub's errors, already explained in plain words: settings problems are 400, GitHub being down is 502. */
    @ExceptionHandler(GitHubClient.GitHubException.class)
    public ProblemDetail gitHubError(GitHubClient.GitHubException e) {
        HttpStatus status = switch (e.status()) {
            case 401, 403, 404 -> HttpStatus.BAD_REQUEST;
            case 409, 422 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
