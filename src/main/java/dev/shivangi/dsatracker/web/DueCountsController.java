package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.sd.SdItemRepository;
import dev.shivangi.dsatracker.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** How many revisions are due today in each tracker: the badges on the DSA and System design tabs. */
@RestController
public class DueCountsController {

    public record DueCounts(long dsa, long systemDesign) {
    }

    private final ProblemRepository problems;
    private final SdItemRepository sdItems;
    private final Clock clock;

    public DueCountsController(ProblemRepository problems, SdItemRepository sdItems, Clock clock) {
        this.problems = problems;
        this.sdItems = sdItems;
        this.clock = clock;
    }

    @GetMapping("/api/due-counts")
    public DueCounts dueCounts(@AuthenticationPrincipal AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        return new DueCounts(problems.countByUserIdAndNextDueOnLessThanEqual(me.id(), today),
                sdItems.countByUserIdAndNextDueOnLessThanEqual(me.id(), today));
    }
}
