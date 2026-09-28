package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.analysis.ApproachRecommender;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only: gathers everything one user's dashboard shows, in one call. */
@Service
public class DashboardService {

    static final int RECALL_WINDOW_DAYS = 30;

    private final UserRepository users;
    private final CatalogRepository catalogs;
    private final ProblemRepository problems;
    private final ActivityRepository activity;
    private final ConsistencyCalculator consistency;
    private final WeeklyTestService weeklyTests;
    private final SpacedRepetitionPolicy policy;
    private final AppProperties props;
    private final ApproachRecommender approaches;
    private final Clock clock;

    public DashboardService(UserRepository users, CatalogRepository catalogs, ProblemRepository problems,
                            ActivityRepository activity, ConsistencyCalculator consistency,
                            WeeklyTestService weeklyTests, SpacedRepetitionPolicy policy,
                            AppProperties props, ApproachRecommender approaches, Clock clock) {
        this.users = users;
        this.catalogs = catalogs;
        this.problems = problems;
        this.activity = activity;
        this.consistency = consistency;
        this.weeklyTests = weeklyTests;
        this.policy = policy;
        this.props = props;
        this.approaches = approaches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView build(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user"));
        LocalDate today = LocalDate.now(clock);
        LocalDate start = user.getStartDate();
        LocalDate planEnd = start.plusDays(props.planDays() - 1L);

        ConsistencySummary habit = consistency.summarise(
                activity.activeDays(userId, today), today, start, props.weeklyTargetDays());
        List<DayActivity> days = activity.countsBetween(userId, start, planEnd);
        ReviewStats recent = activity.reviewStats(userId, today.minusDays(RECALL_WINDOW_DAYS - 1L));

        List<Problem> mine = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId);
        List<ProblemView> all = mine.stream().map(p -> view(p, today)).toList();
        List<ProblemView> due = problems.findDue(userId, today).stream().map(p -> view(p, today)).toList();

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
        int dayOfPlan = (int) ChronoUnit.DAYS.between(start, today) + 1;

        return new DashboardView(
                user.getUsername(),
                today,
                start,
                props.planDays(),
                dayOfPlan,
                habit,
                new DashboardView.Memory(learning, strengthening, longTerm),
                new DashboardView.Recall(RECALL_WINDOW_DAYS, recent.total(), recent.remembered()),
                new DashboardView.Counts(byCatalogId.size(), due.size(), overdue, props.targetProblems()),
                days.stream().map(d -> new DashboardView.Day(d.day(), d.activities())).toList(),
                due,
                weeklyTests.summaries(userId, start),
                catalog,
                earlier,
                policy.firstGaps());
    }

    private ProblemView view(Problem p, LocalDate today) {
        return ProblemView.of(p, policy.overdueDays(p.schedule(), today), today, approaches, policy);
    }
}
