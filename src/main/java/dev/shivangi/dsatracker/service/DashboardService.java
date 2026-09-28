package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.config.AppProperties;
import dev.shivangi.dsatracker.consistency.ActivityRepository;
import dev.shivangi.dsatracker.consistency.ActivityRepository.DayActivity;
import dev.shivangi.dsatracker.consistency.ActivityRepository.ReviewStats;
import dev.shivangi.dsatracker.consistency.ConsistencyCalculator;
import dev.shivangi.dsatracker.consistency.ConsistencySummary;
import dev.shivangi.dsatracker.domain.AppUser;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.web.DashboardView;
import dev.shivangi.dsatracker.web.ProblemView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only: gathers everything one user's dashboard shows, in one call. */
@Service
public class DashboardService {

    static final int RECALL_WINDOW_DAYS = 30;
    static final int WORKLOAD_DAYS = 14;

    private final UserRepository users;
    private final CatalogRepository catalogs;
    private final ProblemRepository problems;
    private final ActivityRepository activity;
    private final ConsistencyCalculator consistency;
    private final WeeklyTestService weeklyTests;
    private final SpacedRepetitionPolicy policy;
    private final AppProperties props;
    private final ProblemViews views;
    private final ProblemService problemService;
    private final Clock clock;

    public DashboardService(UserRepository users, CatalogRepository catalogs, ProblemRepository problems,
                            ActivityRepository activity, ConsistencyCalculator consistency,
                            WeeklyTestService weeklyTests, SpacedRepetitionPolicy policy,
                            AppProperties props, ProblemViews views, ProblemService problemService, Clock clock) {
        this.users = users;
        this.catalogs = catalogs;
        this.problems = problems;
        this.activity = activity;
        this.consistency = consistency;
        this.weeklyTests = weeklyTests;
        this.policy = policy;
        this.props = props;
        this.views = views;
        this.problemService = problemService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView build(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user"));
        LocalDate today = LocalDate.now(clock);
        LocalDate start = user.getStartDate();
        Plan plan = problemService.plan(userId);
        LocalDate planEnd = plan.end();

        ConsistencySummary habit = consistency.summarise(
                activity.activeDays(userId, today), today, start, props.weeklyTargetDays());
        List<DayActivity> days = activity.countsBetween(userId, start, planEnd);
        ReviewStats recent = activity.reviewStats(userId, today.minusDays(RECALL_WINDOW_DAYS - 1L));

        List<Problem> mine = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId);
        List<ProblemView> all = views.of(mine, plan);
        List<ProblemView> due = all.stream().filter(p -> p.nextDueOn() != null && !p.nextDueOn().isAfter(today))
                .sorted(Comparator.comparing(ProblemView::nextDueOn).thenComparing(ProblemView::id))
                .toList();

        // Memory stages by current review gap: under a week, one to three weeks, three weeks or more.
        int longTerm = (int) mine.stream().filter(p -> p.getIntervalDays() >= SpacedRepetitionPolicy.MATURE_DAYS).count();
        int strengthening = (int) mine.stream().filter(p -> p.getIntervalDays() >= SpacedRepetitionPolicy.STRENGTHENING_DAYS
                && p.getIntervalDays() < SpacedRepetitionPolicy.MATURE_DAYS).count();
        int learning = mine.size() - longTerm - strengthening;

        // Join the 150 NeetCode problems with this user's progress on each (at most one entry each).
        Map<Integer, ProblemView> byCatalogId = all.stream()
                .filter(p -> p.catalogId() != null)
                .collect(Collectors.toMap(ProblemView::catalogId, Function.identity()));
        List<DashboardView.CatalogItem> catalog = catalogs.findAllByOrderByIdAsc().stream()
                .map(c -> new DashboardView.CatalogItem(c.getId(), c.getCategory(), c.getName(),
                        c.getDifficulty(), c.getUrl(), byCatalogId.get(c.getId())))
                .toList();
        List<ProblemView> earlier = all.stream().filter(p -> p.catalogId() == null).toList();

        long overdue = due.stream().filter(p -> p.overdueDays() > 0).count();
        long fullyRevised = byCatalogId.values().stream().filter(ProblemView::fullyRevised).count();
        long improved = all.stream().filter(p -> p.attempts().stream()
                .anyMatch(a -> a.versions().stream().anyMatch(v -> v.improved()))).count();
        int dayOfPlan = plan.dayOf(today);

        return new DashboardView(
                user.getUsername(),
                today,
                start,
                props.planDays(),
                dayOfPlan,
                habit,
                new DashboardView.Memory(learning, strengthening, longTerm),
                new DashboardView.Recall(RECALL_WINDOW_DAYS, recent.total(), recent.remembered()),
                new DashboardView.Counts(byCatalogId.size(), due.size(), overdue, props.targetProblems(), fullyRevised, improved),
                days.stream().map(d -> new DashboardView.Day(d.day(), d.activities())).toList(),
                due,
                weeklyTests.summaries(userId, start),
                catalog,
                earlier,
                policy.firstGaps(today, planEnd),
                new DashboardView.Plan(planEnd, plan.lastNewDay(), props.lastNewProblemDay(), plan.newProblemsOpen(today),
                        SpacedRepetitionPolicy.REVISIONS, SpacedRepetitionPolicy.MAX_REVIEWS_PER_DAY),
                pace(byCatalogId.size(), plan, today),
                workload(all, today));
    }

    /** Problems per day needed to finish by the last new-problem day, your pace so far, and where it lands. */
    DashboardView.Pace pace(int done, Plan plan, LocalDate today) {
        int left = Math.max(0, props.targetProblems() - done);
        int daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(today, plan.lastNewDay()) + 1);
        Double needed = daysLeft > 0 ? round1((double) left / daysLeft) : null;
        int elapsed = Math.max(1, Math.min(plan.dayOf(today), props.lastNewProblemDay()));
        double yours = round1((double) done / elapsed);
        Integer projected = done == 0 || left == 0 ? null
                : plan.dayOf(today) + (int) Math.ceil(left / ((double) done / elapsed));
        return new DashboardView.Pace(left, daysLeft, needed, yours, projected);
    }

    /** Reviews due on each of the next 14 days; today's includes anything overdue. */
    static List<DashboardView.Workload> workload(List<ProblemView> all, LocalDate today) {
        int[] counts = new int[WORKLOAD_DAYS];
        for (ProblemView p : all) {
            if (p.nextDueOn() == null) {
                continue;
            }
            long in = ChronoUnit.DAYS.between(today, p.nextDueOn());
            if (in < WORKLOAD_DAYS) {
                counts[(int) Math.max(0, in)]++;
            }
        }
        List<DashboardView.Workload> out = new ArrayList<>();
        for (int i = 0; i < WORKLOAD_DAYS; i++) {
            out.add(new DashboardView.Workload(today.plusDays(i), counts[i]));
        }
        return out;
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }

}
