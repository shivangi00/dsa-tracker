package dev.shivangi.dsatracker.analysis;

/**
 * A complexity class: n^power · log^logs n · k^kpow · log^klogs k, optionally times 2ⁿ or n!,
 * or h (a tree's height). n is the input size; k is the size of each item (a word's length, say),
 * so Group Anagrams reads O(n·k) instead of a misleading O(n²).
 *
 * <p>{@code height} is O(h): between O(log n) (balanced tree) and O(n) (a chain).
 */
record Cx(int power, int logs, int kpow, int klogs, Growth growth, boolean height) implements Comparable<Cx> {

    enum Growth { POLY, EXPONENTIAL, FACTORIAL }

    static final Cx ONE = new Cx(0, 0, 0, 0, Growth.POLY, false);
    static final Cx LOG = new Cx(0, 1, 0, 0, Growth.POLY, false);
    static final Cx N = new Cx(1, 0, 0, 0, Growth.POLY, false);
    static final Cx N_LOG_N = new Cx(1, 1, 0, 0, Growth.POLY, false);
    static final Cx N2 = new Cx(2, 0, 0, 0, Growth.POLY, false);
    static final Cx K = new Cx(0, 0, 1, 0, Growth.POLY, false);
    static final Cx K_LOG_K = new Cx(0, 0, 1, 1, Growth.POLY, false);
    static final Cx H = new Cx(0, 0, 0, 0, Growth.POLY, true);
    static final Cx TWO_TO_N = new Cx(0, 0, 0, 0, Growth.EXPONENTIAL, false);
    static final Cx N_FACTORIAL = new Cx(0, 0, 0, 0, Growth.FACTORIAL, false);

    static Cx power(int p) {
        return new Cx(p, 0, 0, 0, Growth.POLY, false);
    }

    Cx times(Cx o) {
        if (equals(ONE)) {
            return o;
        }
        if (o.equals(ONE)) {
            return this;
        }
        Growth g = growth.ordinal() >= o.growth.ordinal() ? growth : o.growth;
        // h only describes recursion depth; multiplied by anything, count it as n (the worst case)
        int p = power + o.power + (height ? 1 : 0) + (o.height ? 1 : 0);
        return new Cx(p, logs + o.logs, kpow + o.kpow, klogs + o.klogs, g, false);
    }

    static Cx max(Cx a, Cx b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    boolean usesK() {
        return kpow > 0 || klogs > 0;
    }

    /** Orders by growth, then total polynomial degree (k counts like n), then logs. */
    @Override
    public int compareTo(Cx o) {
        if (growth != o.growth) {
            return Integer.compare(growth.ordinal(), o.growth.ordinal());
        }
        double mine = power + kpow + (height ? 0.5 : 0);
        double theirs = o.power + o.kpow + (o.height ? 0.5 : 0);
        if (mine != theirs) {
            return Double.compare(mine, theirs);
        }
        if (logs + klogs != o.logs + o.klogs) {
            return Integer.compare(logs + klogs, o.logs + o.klogs);
        }
        return Integer.compare(power, o.power);   // n² beats n·k when sizes are unknown
    }

    /** "O(n log n)", "O(n²)", "O(n·k log k)", "O(2ⁿ)", … In grid mode n² reads "m·n" (an m × n grid). */
    String format(boolean grid) {
        StringBuilder poly = new StringBuilder();
        if (height) {
            poly.append('h');
        }
        boolean gridSquare = grid && power > 0 && power % 2 == 0;
        if (power > 0) {
            if (gridSquare) {
                poly.append(power == 2 ? "m·n" : "(m·n)" + sup(power / 2));
            } else {
                poly.append('n').append(power > 1 ? sup(power) : "");
            }
        }
        if (kpow > 0) {
            poly.append(poly.isEmpty() ? "" : "·").append('k').append(kpow > 1 ? sup(kpow) : "");
        }
        if (logs > 0) {
            poly.append(poly.isEmpty() ? "" : " ").append(logs == 1 ? "log " : "log" + sup(logs) + " ")
                    .append(gridSquare ? "(m·n)" : "n");
        }
        if (klogs > 0) {
            poly.append(poly.isEmpty() ? "" : " ").append(klogs == 1 ? "log k" : "log" + sup(klogs) + " k");
        }
        String core = switch (growth) {
            case POLY -> poly.isEmpty() ? "1" : poly.toString();
            case EXPONENTIAL -> (poly.isEmpty() ? "" : poly + " · ") + "2ⁿ";
            case FACTORIAL -> (poly.isEmpty() ? "" : poly + " · ") + "n!";
        };
        return "O(" + core + ")";
    }

    private static String sup(int p) {
        String digits = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder b = new StringBuilder();
        for (char c : Integer.toString(p).toCharArray()) {
            b.append(digits.charAt(c - '0'));
        }
        return b.toString();
    }
}
