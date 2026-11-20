package dev.dsatracker.dsa;

import dev.dsatracker.dsa.analysis.ApproachRecommender;
import dev.dsatracker.dsa.analysis.ComplexityAnalysis;
import dev.dsatracker.dsa.analysis.ComplexityExpression;
import dev.dsatracker.shared.BadRequestException;

/**
 * Your own answer for a version's complexity, as you type it: "n log n", "O(n)", "o(m*n)".
 * It's tidied into "O(…)" form and must be something the tracker can read, so it can be compared
 * with the best known approaches.
 */
public final class YourComplexity {

    public static final int MAX_LENGTH = 60;

    private YourComplexity() {
    }

    /** "n log n" → "O(n log n)"; blank → null. Throws a readable 400 if it isn't a complexity. */
    public static String tidy(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip().replaceAll("\\s+", " ");
        if (s.length() > 1 && (s.charAt(0) == 'o' || s.charAt(0) == 'O') && s.charAt(1) == '(') {
            s = "O" + s.substring(1);
        } else if (s.charAt(0) == 'O' && s.length() > 2 && s.charAt(1) == ' ' && s.charAt(2) == '(') {
            s = "O" + s.substring(2);
        } else {
            s = "O(" + s + ")";
        }
        if (s.length() > MAX_LENGTH) {
            throw new BadRequestException("Your " + what + " complexity can be at most " + MAX_LENGTH + " characters");
        }
        if (ComplexityExpression.evaluate(s).isEmpty() || !onlyComplexityWords(s)) {
            throw new BadRequestException("Couldn't read \u201c" + raw.strip() + "\u201d as a " + what
                    + " complexity. Write it like O(n log n), O(n²), O(m·n) or O(log(min(m, n))).");
        }
        return s;
    }

    /**
     * Letters must be variables (n, m, k, V, E, mn) or log / min / max / sqrt, so a word like "fast"
     * isn't read as f·a·s·t.
     */
    private static boolean onlyComplexityWords(String s) {
        var words = java.util.regex.Pattern.compile("[A-Za-z]+").matcher(s.substring(1));
        while (words.find()) {
            String w = words.group();
            String rest = w.replaceAll("log|lg|ln|min|max|sqrt", "");
            if (rest.length() > 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * When your answer and the built-in estimate clearly disagree, a sentence saying what the
     * estimate found; null when they agree (or either is missing).
     */
    public static String estimateDiffers(ComplexityAnalysis yours, ComplexityAnalysis estimate) {
        if (yours == null || estimate == null) {
            return null;
        }
        boolean time = differ(yours.time(), estimate.time());
        boolean space = differ(yours.space(), estimate.space());
        if (!time && !space) {
            return null;
        }
        String what = time && space ? estimate.time() + " time and " + estimate.space() + " space"
                : time ? estimate.time() + " time" : estimate.space() + " space";
        return "The built-in estimate says " + what + ". Worth a second look at your answer; if you're sure,"
                + " keep yours: the estimate reads the code's shape and can be wrong.";
    }

    private static boolean differ(String a, String b) {
        var x = ComplexityExpression.evaluate(a);
        var y = ComplexityExpression.evaluate(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        return ApproachRecommender.improved(x.get(), y.get()) || ApproachRecommender.improved(y.get(), x.get());
    }
}
