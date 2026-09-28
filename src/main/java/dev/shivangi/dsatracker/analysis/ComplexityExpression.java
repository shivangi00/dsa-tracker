package dev.shivangi.dsatracker.analysis;

import java.util.Optional;

/**
 * Turns a complexity like "O(n log n)", "O(m·n)", "O(V + E)" or "O(n · 2ⁿ)" into a number, so two
 * complexities written in different styles (the built-in estimate's, Claude's, the approach list's)
 * can be compared.
 *
 * <p>Every variable gets a typical size and the expression is evaluated as a natural log (so 2ⁿ and
 * n! don't overflow). Input sizes (n, m, V, E, t, h, and any unknown name) are 1000; k and L, the
 * length of one item such as a word, are 20. h is a tree's height, counted at its worst case (n).
 * Constant factors are dropped, as in big-O: O(26·n) is O(n).
 *
 * <p>Parses: + (and - , which keeps the left side), · * × and juxtaposition, / , ^ and superscripts,
 * log / lg / ln, √ / sqrt, min(a, b), max(a, b), n! and α(n) (inverse Ackermann, treated as 1).
 */
public final class ComplexityExpression {

    /** ln of the value when every variable takes its typical size; exponential is true for 2ⁿ, n!, … */
    public record Size(double log, boolean exponential) {
    }

    static final double INPUT = 1000;
    static final double ITEM = 20;

    private ComplexityExpression() {
    }

    /** Empty when the text isn't a complexity this can read. */
    public static Optional<Size> evaluate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            Parser p = new Parser(normalise(inner(text)));
            Val v = p.sum();
            p.skipSpaces();
            if (!p.done()) {
                return Optional.empty();
            }
            return Optional.of(new Size(v.hasVar ? v.log : 0, v.exponential));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The part inside "O( … )", ignoring any words around it ("O(n) average"). No "O(" means not a complexity. */
    static String inner(String text) {
        String s = text.strip();
        for (int i = 0; i + 1 < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == 'O' || c == 'Θ' || c == 'Ω') && s.charAt(i + 1) == '(' && (i == 0 || !Character.isLetter(s.charAt(i - 1)))) {
                int depth = 0;
                for (int j = i + 1; j < s.length(); j++) {
                    if (s.charAt(j) == '(') {
                        depth++;
                    } else if (s.charAt(j) == ')' && --depth == 0) {
                        return s.substring(i + 2, j);
                    }
                }
                throw new IllegalArgumentException("unbalanced");
            }
        }
        throw new IllegalArgumentException("no O(...)");
    }

    private static final String SUPERS = "⁰¹²³⁴⁵⁶⁷⁸⁹ⁿᵏᵐᴸᵗ⁺⁻";
    private static final String PLAIN = "0123456789nkmLt+-";

    /** Superscript runs become ^(…): "2ⁿ" → "2^(n)", "n²" → "n^(2)". */
    static String normalise(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int at = SUPERS.indexOf(s.charAt(i));
            if (at < 0) {
                out.append(s.charAt(i++));
                continue;
            }
            out.append("^(");
            while (i < s.length() && (at = SUPERS.indexOf(s.charAt(i))) >= 0) {
                out.append(PLAIN.charAt(at));
                i++;
            }
            out.append(')');
        }
        return out.toString();
    }

    /**
     * A value: a constant (hasVar false, log = ln of the constant) or something that grows
     * (log = ln of its size at typical inputs).
     */
    private record Val(double log, boolean hasVar, boolean exponential) {
        static Val constant(double c) {
            return new Val(Math.log(Math.max(c, 1e-9)), false, false);
        }

        static Val var(double size) {
            return new Val(Math.log(size), true, false);
        }

        /** The actual (not log) value, capped so exponents stay finite. */
        double value() {
            return Math.exp(Math.min(log, 700));
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean done() {
            return pos >= s.length();
        }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            skipSpaces();
            return done() ? '\0' : s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("expected " + c);
            }
            pos++;
        }

        /** a + b + … : the biggest term wins (log-sum-exp keeps V + E slightly above V). */
        Val sum() {
            Val v = product();
            while (true) {
                char c = peek();
                if (c == '+') {
                    pos++;
                    v = add(v, product());
                } else if (c == '-' || c == '−') {
                    pos++;
                    product();                       // n - k + 1: the subtracted part doesn't grow it
                } else {
                    return v;
                }
            }
        }

        private static Val add(Val a, Val b) {
            if (a.hasVar != b.hasVar) {
                return a.hasVar ? a : b;             // n + 1 is n
            }
            double hi = Math.max(a.log, b.log);
            double lo = Math.min(a.log, b.log);
            return new Val(hi + Math.log1p(Math.exp(lo - hi)), a.hasVar, a.exponential || b.exponential);
        }

        Val product() {
            Val v = power();
            while (true) {
                char c = peek();
                if (c == '·' || c == '*' || c == '×' || c == '⋅' || c == '∙') {
                    pos++;
                    v = multiply(v, power());
                } else if (c == '/') {
                    pos++;
                    Val d = power();
                    v = d.hasVar ? new Val(v.log - d.log, v.hasVar, v.exponential) : v;
                } else if (startsFactor(c)) {
                    v = multiply(v, power());        // juxtaposition: "n log n", "2ⁿ n"
                } else {
                    return v;
                }
            }
        }

        private static boolean startsFactor(char c) {
            return Character.isLetterOrDigit(c) || c == '(' || c == '√';
        }

        private static Val multiply(Val a, Val b) {
            if (!a.hasVar && !b.hasVar) {
                return new Val(a.log + b.log, false, false);
            }
            if (!a.hasVar) {
                return b;                            // constant factors don't count
            }
            if (!b.hasVar) {
                return a;
            }
            return new Val(a.log + b.log, true, a.exponential || b.exponential);
        }

        Val power() {
            Val base = postfix(primary());
            if (peek() == '^') {
                pos++;
                Val exp = power();                   // right-associative
                return raise(base, exp);
            }
            return base;
        }

        private Val postfix(Val v) {
            if (peek() == '!') {
                pos++;
                double x = v.value();
                return new Val(x * Math.log(x) - x, v.hasVar, v.hasVar);   // Stirling
            }
            return v;
        }

        private static Val raise(Val base, Val exp) {
            if (!exp.hasVar) {
                return new Val(base.log * exp.value(), base.hasVar, base.exponential);
            }
            if (base.log <= 0) {
                return Val.constant(1);              // 1ⁿ
            }
            return new Val(exp.value() * base.log, true, true);   // 2ⁿ, 3^L, n^n
        }

        Val primary() {
            char c = peek();
            if (c == '(') {
                pos++;
                Val v = sum();
                expect(')');
                return v;
            }
            if (c == '√') {
                pos++;
                Val v = power();
                return new Val(v.log / 2, v.hasVar, v.exponential);
            }
            if (Character.isDigit(c)) {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                    pos++;
                }
                return Val.constant(Double.parseDouble(s.substring(start, pos)));
            }
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                    pos++;
                }
                return word(s.substring(start, pos));
            }
            throw new IllegalArgumentException("unexpected " + c);
        }

        private Val word(String w) {
            switch (w.toLowerCase()) {
                case "log", "lg", "ln" -> {
                    Val exp = null;
                    if (peek() == '^') {             // log² n
                        pos++;
                        exp = primary();
                    }
                    Val v = logOf(power());
                    return exp == null ? v : raise(v, exp);
                }
                case "sqrt" -> {
                    Val v = power();
                    return new Val(v.log / 2, v.hasVar, v.exponential);
                }
                case "min", "max" -> {
                    boolean min = w.equalsIgnoreCase("min");
                    expect('(');
                    Val v = sum();
                    while (peek() == ',') {
                        pos++;
                        Val o = sum();
                        v = (min == (o.log < v.log)) ? o : v;
                    }
                    expect(')');
                    return v;
                }
                case "α", "alpha" -> {
                    if (peek() == '(') {             // α(n), inverse Ackermann: at most 4 in practice
                        pos++;
                        sum();
                        expect(')');
                    }
                    return Val.constant(1);
                }
                default -> {
                    int at = w.indexOf("log");
                    if (at >= 0 && w.length() > at + 3 && isVariables(w.substring(0, at)) && isVariables(w.substring(at + 3))) {
                        return multiply(variables(w.substring(0, at)), logOf(variables(w.substring(at + 3))));   // "nlogn"
                    }
                    if (w.length() > 1 && isVariables(w)) {
                        return variables(w);                          // "mn" is m·n
                    }
                    return variable(w);
                }
            }
        }

        private static final String VARIABLES = "nmkhVELt";

        private static boolean isVariables(String w) {
            return w.chars().allMatch(ch -> VARIABLES.indexOf(ch) >= 0);
        }

        private static Val variables(String w) {
            Val v = Val.constant(1);
            for (char ch : w.toCharArray()) {
                v = multiply(v, variable(String.valueOf(ch)));
            }
            return v;
        }

        private static Val variable(String w) {
            return Val.var(w.equals("k") || w.equals("L") ? ITEM : INPUT);
        }

        private static Val logOf(Val arg) {
            return new Val(Math.log(Math.max(arg.log / Math.log(2), 1)), arg.hasVar, false);
        }
    }
}
