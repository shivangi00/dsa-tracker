package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.domain.TestOutcome;
import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.service.WeeklyTestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Weekly tests for the signed-in user. */
@RestController
@RequestMapping("/api/tests")
public class WeeklyTestController {

    private final WeeklyTestService tests;

    public WeeklyTestController(WeeklyTestService tests) {
        this.tests = tests;
    }

    public record PatternAnswer(@NotNull Integer patternId) {
    }

    /** {@code feedback}: the AI review you pasted back (optional). */
    public record OutcomeAnswer(@NotNull TestOutcome outcome, @Size(max = WeeklyTestService.MAX_FEEDBACK) String feedback) {
    }

    /** The pattern you marked (a NeetCode category), why, and your code. */
    public record ReflectionAnswer(@NotNull String category,
                                   @Size(max = WeeklyTestService.MAX_RECOGNITION) String recognition,
                                   @Size(max = WeeklyTestService.MAX_CODE) String code,
                                   CodeLanguage codeLanguage) {
    }

    @PostMapping("/items/{itemId}/answer")
    public TestViews.Item answer(@AuthenticationPrincipal AuthUser me, @PathVariable long itemId,
                                 @Valid @RequestBody ReflectionAnswer req) {
        return tests.answer(me.id(), itemId, req.category(), req.recognition(), req.code(), req.codeLanguage());
    }

    /** Opens the test for a plan week, creating it the first time. */
    @PostMapping("/week/{weekNumber}")
    public TestViews.Test open(@AuthenticationPrincipal AuthUser me, @PathVariable int weekNumber) {
        return tests.open(me.id(), weekNumber);
    }

    @GetMapping("/{testId}")
    public TestViews.Test get(@AuthenticationPrincipal AuthUser me, @PathVariable long testId) {
        return tests.get(me.id(), testId);
    }

    @PostMapping("/items/{itemId}/pattern")
    public TestViews.Item pattern(@AuthenticationPrincipal AuthUser me, @PathVariable long itemId,
                                  @Valid @RequestBody PatternAnswer req) {
        return tests.choosePattern(me.id(), itemId, req.patternId());
    }

    @PostMapping("/items/{itemId}/outcome")
    public TestViews.Item outcome(@AuthenticationPrincipal AuthUser me, @PathVariable long itemId,
                                  @Valid @RequestBody OutcomeAnswer req) {
        return tests.recordOutcome(me.id(), itemId, req.outcome(), req.feedback());
    }
}
