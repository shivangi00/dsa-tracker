package dev.shivangi.dsatracker.weekly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Chooses the questions for one weekly test. Pure Java (no Spring, no database); the only
 * outside input is a {@link Random}, which tests replace with a seeded one.
 *
 * <p>For each pattern you practised that week, it picks one practice problem you haven't seen
 * in an earlier test: a different problem that uses the same technique. That tests whether you
 * can recognise the pattern, not whether you remember one answer. Patterns whose problems you
 * forgot most often come first. Each question gets four pattern options: the right one, then
 * look-alikes from the same topic, then others from what you studied that week.
 */
public final class TestBuilder {

    public static final int MAX_ITEMS = 5;
    public static final int OPTIONS = 4;

    /** A problem you marked done that week. */
    public record Solved(int catalogId, int patternId, int lapses) {
    }

    public record PatternInfo(int id, String category) {
    }

    /** One question: the practice problem, the right pattern, the problem it came from, the options. */
    public record ItemPlan(int practiceId, int patternId, int anchorCatalogId, List<Integer> optionPatternIds) {
    }

    private final Random random;

    public TestBuilder(Random random) {
        this.random = random;
    }

    /**
     * @param solvedThisWeek problems marked done in the week
     * @param unusedPractice practice problem ids per pattern, excluding ones used in earlier tests
     * @param allPatterns    every pattern, for the options
     */
    public List<ItemPlan> build(List<Solved> solvedThisWeek, Map<Integer, List<Integer>> unusedPractice,
                                List<PatternInfo> allPatterns) {
        Map<Integer, PatternInfo> patterns = new LinkedHashMap<>();
        allPatterns.forEach(p -> patterns.put(p.id(), p));

        // One candidate per pattern: the solved problem you forgot most often.
        Map<Integer, Solved> byPattern = new LinkedHashMap<>();
        List<Solved> shuffled = new ArrayList<>(solvedThisWeek);
        Collections.shuffle(shuffled, random);
        for (Solved s : shuffled) {
            byPattern.merge(s.patternId(), s, (a, b) -> b.lapses() > a.lapses() ? b : a);
        }
        List<Solved> order = new ArrayList<>(byPattern.values());
        order.sort(Comparator.comparingInt(Solved::lapses).reversed());   // stable: ties keep the shuffle

        Set<String> weekCategories = new LinkedHashSet<>();
        for (Solved s : order) {
            weekCategories.add(patterns.get(s.patternId()).category());
        }

        List<ItemPlan> items = new ArrayList<>();
        for (Solved s : order) {
            if (items.size() == MAX_ITEMS) {
                break;
            }
            List<Integer> pool = unusedPractice.getOrDefault(s.patternId(), List.of());
            if (pool.isEmpty()) {
                continue;   // nothing new left for this pattern
            }
            int practiceId = pool.get(random.nextInt(pool.size()));
            items.add(new ItemPlan(practiceId, s.patternId(), s.catalogId(),
                    options(s.patternId(), patterns, weekCategories)));
        }
        return items;
    }

    private List<Integer> options(int correct, Map<Integer, PatternInfo> patterns, Set<String> weekCategories) {
        String category = patterns.get(correct).category();
        List<Integer> sameTopic = new ArrayList<>();
        List<Integer> sameWeek = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        for (PatternInfo p : patterns.values()) {
            if (p.id() == correct) continue;
            if (p.category().equals(category)) sameTopic.add(p.id());
            else if (weekCategories.contains(p.category())) sameWeek.add(p.id());
            else rest.add(p.id());
        }
        Collections.shuffle(sameTopic, random);
        Collections.shuffle(sameWeek, random);
        Collections.shuffle(rest, random);

        List<Integer> chosen = new ArrayList<>();
        chosen.add(correct);
        for (List<Integer> source : List.of(sameTopic, sameWeek, rest)) {
            for (int id : source) {
                if (chosen.size() == OPTIONS) break;
                chosen.add(id);
            }
        }
        Collections.shuffle(chosen, random);
        return chosen;
    }
}
