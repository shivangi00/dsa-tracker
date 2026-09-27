package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.domain.TestOutcome;
import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.service.WeeklyTestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    public record OutcomeAnswer(@NotNull TestOutcome outcome) {
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
        return tests.recordOutcome(me.id(), itemId, req.outcome());
    }
}
