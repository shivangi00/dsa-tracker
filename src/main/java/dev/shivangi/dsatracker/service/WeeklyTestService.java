package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.domain.CatalogProblem;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Pattern;
import dev.shivangi.dsatracker.domain.PatternRepository;
import dev.shivangi.dsatracker.domain.PracticeProblem;
import dev.shivangi.dsatracker.domain.PracticeProblemRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.TestOutcome;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.domain.WeeklyTest;
import dev.shivangi.dsatracker.domain.WeeklyTestItem;
import dev.shivangi.dsatracker.domain.WeeklyTestItemRepository;
import dev.shivangi.dsatracker.domain.WeeklyTestRepository;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.web.TestViews;
import dev.shivangi.dsatracker.weekly.TestBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Weekly tests. Week n of a plan runs from startDate + 7(n−1) for 7 days. A week's test unlocks
 * on its last day and never expires, so there's no deadline pressure.
 */
@Service
public class WeeklyTestService {

    private final UserRepository users;
    private final ProblemRepository problems;
    private final CatalogRepository catalogs;
    private final PatternRepository patterns;
    private final PracticeProblemRepository practice;
    private final WeeklyTestRepository tests;
    private final WeeklyTestItemRepository items;
    private final TestBuilder builder;
    private final SpacedRepetitionPolicy policy;
    private final Clock clock;

    public WeeklyTestService(UserRepository users, ProblemRepository problems, CatalogRepository catalogs,
                             PatternRepository patterns, PracticeProblemRepository practice,
                             WeeklyTestRepository tests, WeeklyTestItemRepository items,
                             TestBuilder builder, SpacedRepetitionPolicy policy, Clock clock) {
        this.users = users;
        this.problems = problems;
        this.catalogs = catalogs;
        this.patterns = patterns;
        this.practice = practice;
        this.tests = tests;
        this.items = items;
        this.builder = builder;
        this.policy = policy;
        this.clock = clock;
    }

    /** The dashboard list: every week that has something in it, newest first. */
    @Transactional(readOnly = true)
    public List<TestViews.Summary> summaries(Long userId, LocalDate start) {
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(start)) {
            return List.of();
        }
        int currentWeek = (int) (ChronoUnit.DAYS.between(start, today) / 7) + 1;

        Map<Integer, WeeklyTest> byWeek = tests.findByUserId(userId).stream()
                .collect(Collectors.toMap(WeeklyTest::getWeekNumber, Function.identity()));
        Map<Long, List<WeeklyTestItem>> itemsByTest = byWeek.isEmpty() ? Map.of()
                : items.findByTestIdIn(byWeek.values().stream().map(WeeklyTest::getId).toList()).stream()
                .collect(Collectors.groupingBy(WeeklyTestItem::getTestId));

        // One query for all your problems, then count per week in memory.
        Map<Integer, Long> doneByWeek = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId).stream()
                .filter(p -> !p.getSolvedOn().isBefore(start))
                .collect(Collectors.groupingBy(p -> (int) (ChronoUnit.DAYS.between(start, p.getSolvedOn()) / 7) + 1,
                        Collectors.counting()));

        List<TestViews.Summary> out = new ArrayList<>();
        for (int week = currentWeek; week >= 1; week--) {
            LocalDate ws = weekStart(start, week);
            LocalDate we = ws.plusDays(6);
            int done = doneByWeek.getOrDefault(week, 0L).intValue();
            WeeklyTest test = byWeek.get(week);
            if (test == null && done == 0) {
                continue;   // nothing studied, nothing to test
            }
            if (test == null) {
                String status = we.isAfter(today) ? "UPCOMING" : "AVAILABLE";
                out.add(new TestViews.Summary(week, ws, we, status, done, null, 0, 0, 0, 0, 0));
                continue;
            }
            List<WeeklyTestItem> its = itemsByTest.getOrDefault(test.getId(), List.of());
            int answered = (int) its.stream().filter(i -> i.getOutcome() != null).count();
            int right = (int) its.stream().filter(WeeklyTestItem::patternCorrect).count();
            int solved = (int) its.stream().filter(i -> i.getOutcome() == TestOutcome.SOLVED).count();
            int hint = (int) its.stream().filter(i -> i.getOutcome() == TestOutcome.HINT).count();
            out.add(new TestViews.Summary(week, ws, we, test.isCompleted() ? "COMPLETED" : "IN_PROGRESS",
                    done, test.getId(), its.size(), answered, right, solved, hint));
        }
        return out;
    }

    /** Opens (creating on first use) the test for a plan week. */
    @Transactional
    public TestViews.Test open(Long userId, int weekNumber) {
        LocalDate start = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user")).getStartDate();
        LocalDate today = LocalDate.now(clock);
        if (weekNumber < 1) {
            throw new BadRequestException("Weeks start at 1");
        }
        LocalDate ws = weekStart(start, weekNumber);
        LocalDate we = ws.plusDays(6);
        if (we.isAfter(today)) {
            throw new ConflictException("This week's test unlocks on " + we);
        }

        WeeklyTest existing = tests.findByUserIdAndWeekNumber(userId, weekNumber).orElse(null);
        if (existing != null) {
            return view(existing);
        }

        // What you solved that week, and the pattern each one teaches.
        List<Problem> solved = problems.findByUserIdAndSolvedOnBetween(userId, ws, we).stream()
                .filter(p -> p.getCatalogId() != null).toList();
        Map<Integer, CatalogProblem> catalogById = catalogs.findAllById(
                solved.stream().map(Problem::getCatalogId).toList()).stream()
                .collect(Collectors.toMap(CatalogProblem::getId, Function.identity()));
        List<TestBuilder.Solved> candidates = solved.stream()
                .map(p -> new TestBuilder.Solved(p.getCatalogId(), catalogById.get(p.getCatalogId()).getPatternId(), p.getLapses()))
                .toList();

        // Practice problems not used in any earlier test of yours.
        Set<Integer> used = new HashSet<>(items.usedPracticeIds(userId));
        Map<Integer, List<Integer>> unused = new HashMap<>();
        for (PracticeProblem pp : practice.findAll()) {
            if (!used.contains(pp.getId())) {
                unused.computeIfAbsent(pp.getPatternId(), k -> new ArrayList<>()).add(pp.getId());
            }
        }
        List<TestBuilder.PatternInfo> allPatterns = patterns.findAll().stream()
                .map(p -> new TestBuilder.PatternInfo(p.getId(), p.getCategory())).toList();

        List<TestBuilder.ItemPlan> plan = builder.build(candidates, unused, allPatterns);
        if (plan.isEmpty()) {
            throw new ConflictException("There's nothing new to test from this week yet.");
        }

        WeeklyTest test = tests.save(new WeeklyTest(userId, weekNumber, ws, we));
        int position = 1;
        for (TestBuilder.ItemPlan ip : plan) {
            items.save(new WeeklyTestItem(test.getId(), position++, ip.practiceId(), ip.patternId(),
                    ip.anchorCatalogId(), ip.optionPatternIds()));
        }
        return view(test);
    }

    @Transactional(readOnly = true)
    public TestViews.Test get(Long userId, long testId) {
        return view(ownedTest(userId, testId));
    }

    /** Step 1: which pattern fits? Answer once; then the right answer is revealed. */
    @Transactional
    public TestViews.Item choosePattern(Long userId, long itemId, int patternId) {
        WeeklyTestItem item = ownedItem(userId, itemId);
        if (item.patternAnswered()) {
            throw new ConflictException("You've already answered this one");
        }
        if (!item.options().contains(patternId)) {
            throw new BadRequestException("Pick one of the four options");
        }
        item.choosePattern(patternId);
        return itemView(item, lookups());
    }

    /**
     * Step 2: how solving it went. "Couldn't solve" brings the NeetCode problem with the same
     * pattern back for review tomorrow. The last answer completes the test.
     */
    @Transactional
    public TestViews.Item recordOutcome(Long userId, long itemId, TestOutcome outcome) {
        WeeklyTestItem item = ownedItem(userId, itemId);
        if (!item.patternAnswered()) {
            throw new ConflictException("Pick the pattern first");
        }
        if (item.getOutcome() != null) {
            throw new ConflictException("You've already recorded this one");
        }
        LocalDate today = LocalDate.now(clock);
        item.recordOutcome(outcome, clock.instant());

        if (outcome == TestOutcome.NOT_SOLVED) {
            problems.findByUserIdAndCatalogId(userId, item.getAnchorCatalogId())
                    .ifPresent(p -> p.apply(policy.pullForward(p.schedule(), today)));
        }

        WeeklyTest test = ownedTest(userId, item.getTestId());
        boolean allDone = items.findByTestIdOrderByPosition(test.getId()).stream()
                .allMatch(i -> i.getOutcome() != null);
        if (allDone) {
            test.markCompleted(clock.instant());
        }
        return itemView(item, lookups());
    }

    // ---------------------------------------------------------------

    private static LocalDate weekStart(LocalDate start, int week) {
        return start.plusDays((week - 1) * 7L);
    }

    private WeeklyTest ownedTest(Long userId, long testId) {
        return tests.findByIdAndUserId(testId, userId)
                .orElseThrow(() -> new NotFoundException("No test with id " + testId));
    }

    private WeeklyTestItem ownedItem(Long userId, long itemId) {
        WeeklyTestItem item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("No question with id " + itemId));
        ownedTest(userId, item.getTestId());   // 404 if the test isn't yours
        return item;
    }

    private record Lookups(Map<Integer, Pattern> patterns, Map<Integer, PracticeProblem> practice,
                           Map<Integer, CatalogProblem> catalog) {
    }

    private Lookups lookups() {
        return new Lookups(
                patterns.findAll().stream().collect(Collectors.toMap(Pattern::getId, Function.identity())),
                practice.findAll().stream().collect(Collectors.toMap(PracticeProblem::getId, Function.identity())),
                catalogs.findAll().stream().collect(Collectors.toMap(CatalogProblem::getId, Function.identity())));
    }

    private TestViews.Test view(WeeklyTest test) {
        Lookups l = lookups();
        List<TestViews.Item> list = items.findByTestIdOrderByPosition(test.getId()).stream()
                .map(i -> itemView(i, l)).toList();
        return new TestViews.Test(test.getId(), test.getWeekNumber(), test.getWeekStart(), test.getWeekEnd(),
                test.isCompleted(), list);
    }

    private static TestViews.Item itemView(WeeklyTestItem i, Lookups l) {
        PracticeProblem pp = l.practice().get(i.getPracticeProblemId());
        List<TestViews.Option> options = i.options().stream()
                .map(id -> new TestViews.Option(id, l.patterns().get(id).getName())).toList();
        boolean revealed = i.patternAnswered();
        Pattern correct = l.patterns().get(i.getPatternId());
        return new TestViews.Item(
                i.getId(), i.getPosition(),
                new TestViews.PracticeRef(pp.getName(), pp.getLeetcodeNumber(), pp.getDifficulty(), pp.getUrl()),
                options,
                i.getChosenPatternId(),
                revealed ? correct.getId() : null,
                revealed ? correct.getName() : null,
                revealed ? correct.getIdea() : null,
                revealed ? l.catalog().get(i.getAnchorCatalogId()).getName() : null,
                i.getOutcome());
    }
}
