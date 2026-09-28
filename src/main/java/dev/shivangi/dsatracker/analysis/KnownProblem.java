package dev.shivangi.dsatracker.analysis;

import java.util.List;

/**
 * One NeetCode problem's entry in {@code best-approaches.json}.
 *
 * @param approaches the best known approaches (at least one that isn't advanced)
 * @param fixedSize  null, or why the input size is bounded ("the board is always 9×9"): then every
 *                   loop is effectively constant and comparing complexities would only mislead
 * @param tips       interview talking points for the problem (edge cases, trade-offs), from
 *                   {@code interview-tips.json}
 */
public record KnownProblem(List<Approach> approaches, String fixedSize, List<String> tips) {

    public KnownProblem {
        approaches = List.copyOf(approaches);
        tips = List.copyOf(tips);
    }

    public KnownProblem(List<Approach> approaches, String fixedSize) {
        this(approaches, fixedSize, List.of());
    }

    public static KnownProblem of(Approach... approaches) {
        return new KnownProblem(List.of(approaches), null);
    }
}
