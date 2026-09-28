package dev.shivangi.dsatracker.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares an analysis with the best known approaches for a NeetCode problem and recommends a
 * better one when there is one: faster first; otherwise equally fast with less memory.
 *
 * <p>"Better" means at least 3× smaller at typical input sizes (see {@link ComplexityExpression}),
 * so O(n log n) vs O(n) counts, but O(V + E) vs O(n) or O(2n) vs O(n) don't. Two exponential
 * complexities (2ⁿ vs 3^L, say) aren't compared: estimates of backtracking are too rough for that.
 */
public class ApproachRecommender {

    /** How much smaller counts as better: ln 3. */
    static final double MARGIN = Math.log(3);

    private static final KnownProblem UNKNOWN = new KnownProblem(List.of(), null);

    private final Map<Integer, KnownProblem> byCatalogId;

    public ApproachRecommender(Map<Integer, KnownProblem> byCatalogId) {
        this.byCatalogId = Map.copyOf(byCatalogId);
    }

    /** Interview talking points for a NeetCode problem; empty for anything else. */
    public List<String> tips(Integer catalogId) {
        return problem(catalogId).tips();
    }

    private KnownProblem problem(Integer catalogId) {
        return catalogId == null ? UNKNOWN : byCatalogId.getOrDefault(catalogId, UNKNOWN);
    }

    /** Empty for problems outside NeetCode 150, or when the analysis can't be read. */
    public Optional<Recommendation> recommend(Integer catalogId, ComplexityAnalysis analysis) {
        if (analysis == null) {
            return Optional.empty();
        }
        KnownProblem problem = problem(catalogId);
        List<Rated> known = problem.approaches().stream().map(Rated::of).flatMap(Optional::stream).toList();
        Optional<ComplexityExpression.Size> time = ComplexityExpression.evaluate(analysis.time());
        if (known.isEmpty() || time.isEmpty()) {
            return Optional.empty();
        }
        if (problem.fixedSize() != null) {
            Approach usual = known.stream().filter(r -> !r.approach.advanced()).findFirst().orElse(known.get(0)).approach;
            String msg = "Here " + problem.fixedSize() + ", so every loop has a fixed bound and complexities "
                    + "can't really be compared. The usual approach is below for reference.";
            return Optional.of(new Recommendation(Recommendation.FIXED, msg, usual, null));
        }
        Optional<ComplexityExpression.Size> space = ComplexityExpression.evaluate(analysis.space());
        List<Rated> usual = known.stream().filter(r -> !r.approach.advanced()).toList();
        Comparator<Rated> fastest = Comparator.comparingDouble((Rated r) -> r.time.log())
                .thenComparingDouble(r -> r.space.log());

        // 1. Something faster
        Optional<Rated> faster = usual.stream().filter(r -> better(r.time, time.get())).min(fastest);
        if (faster.isPresent()) {
            Rated r = faster.get();
            String msg = r.approach.time() + " time is possible; your solution is " + analysis.time() + ".";
            if (space.isPresent() && better(space.get(), r.space)) {
                msg += " It trades memory for speed: " + r.approach.space() + " space instead of your "
                        + analysis.space() + ".";
            }
            return Optional.of(new Recommendation(Recommendation.FASTER, msg, r.approach, further(known, r.time)));
        }

        // 2. Just as fast, less memory
        if (space.isPresent()) {
            Optional<Rated> leaner = usual.stream()
                    .filter(r -> !better(time.get(), r.time) && better(r.space, space.get()))
                    .min(Comparator.comparingDouble((Rated r) -> r.space.log()));
            if (leaner.isPresent()) {
                Rated r = leaner.get();
                String msg = r.approach.space() + " space is possible at the same speed; your solution uses "
                        + analysis.space() + ".";
                return Optional.of(new Recommendation(Recommendation.LEANER, msg, r.approach, further(known, time.get())));
            }
        }

        // 3. Nothing better among the usual approaches
        Rated best = usual.isEmpty() ? known.stream().min(fastest).get() : usual.stream().min(fastest).get();
        if (better(time.get(), best.time)) {
            String msg = "The analysis (" + analysis.time() + ") is faster than the best known approach ("
                    + best.approach.time() + "), so the analysis may have missed something. Worth a second look.";
            return Optional.of(new Recommendation(Recommendation.CHECK, msg, best.approach, null));
        }
        String msg = "Your " + analysis.time() + " time is the best known for this problem.";
        return Optional.of(new Recommendation(Recommendation.OPTIMAL, msg, best.approach, further(known, time.get())));
    }

    /** An advanced approach faster than {@code than}, if the list has one. */
    private static Approach further(List<Rated> known, ComplexityExpression.Size than) {
        return known.stream()
                .filter(r -> r.approach.advanced() && better(r.time, than))
                .min(Comparator.comparingDouble((Rated r) -> r.time.log()))
                .map(r -> r.approach)
                .orElse(null);
    }

    /** True when {@code a} is clearly smaller than {@code b}. */
    static boolean better(ComplexityExpression.Size a, ComplexityExpression.Size b) {
        if (a.exponential() && b.exponential()) {
            return false;
        }
        return b.log() - a.log() > MARGIN;
    }

    private record Rated(Approach approach, ComplexityExpression.Size time, ComplexityExpression.Size space) {
        static Optional<Rated> of(Approach a) {
            var t = ComplexityExpression.evaluate(a.time());
            var s = ComplexityExpression.evaluate(a.space());
            return t.isPresent() && s.isPresent() ? Optional.of(new Rated(a, t.get(), s.get())) : Optional.empty();
        }
    }
}
