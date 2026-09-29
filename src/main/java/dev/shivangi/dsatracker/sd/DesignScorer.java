package dev.shivangi.dsatracker.sd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The free, built-in score for a written design, out of 10. It checks <b>coverage</b>, not quality:
 * <ul>
 *   <li><b>Structure, 2 points:</b> 0.4 for each of the five parts that's written (30+ characters).</li>
 *   <li><b>Rubric, 8 points:</b> the share of the problem's key points your answer mentions, spotted by
 *       their keywords ("base62", "presigned URL", "geohash"...).</li>
 * </ul>
 * Mentioning a word isn't the same as explaining it well, so the page says so and offers
 * "Analyse with Claude" for a real review.
 */
public final class DesignScorer {

    public static final double STRUCTURE_POINTS = 2.0;
    public static final double RUBRIC_POINTS = 8.0;

    /**
     * @param covered indexes of the rubric points covered
     * @param hits    those points, as text
     * @param missed  the points not covered, as text: what to add next time
     */
    public record Score(double total, double structure, double rubric, List<Integer> covered,
                        List<String> hits, List<String> missed) {
    }

    /** Null if the problem has no rubric yet. */
    public Score score(SdCatalog.Problem problem, DesignSections answer) {
        if (!problem.scored()) {
            return null;
        }
        String text = normalise(answer.allText());
        List<Integer> covered = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        List<SdCatalog.RubricPoint> rubric = problem.rubric();
        for (int i = 0; i < rubric.size(); i++) {
            SdCatalog.RubricPoint point = rubric.get(i);
            if (mentionsAny(text, point.keywords())) {
                covered.add(i);
                hits.add(point.point());
            } else {
                missed.add(point.point());
            }
        }
        double structure = round1(STRUCTURE_POINTS * answer.filled() / 5.0);
        double rubricScore = round1(RUBRIC_POINTS * covered.size() / rubric.size());
        return new Score(round1(structure + rubricScore), structure, rubricScore, List.copyOf(covered),
                List.copyOf(hits), List.copyOf(missed));
    }

    /**
     * True if any keyword starts a word in the text: "cache" matches "cache", "caches" and "cached",
     * and a final "e" may become "ing" ("caching", "expiring").
     */
    static boolean mentionsAny(String normalisedText, List<String> keywords) {
        for (String k : keywords) {
            String kw = normalise(k).strip();
            if (kw.isEmpty()) {
                continue;
            }
            if (normalisedText.contains(" " + kw)) {
                return true;
            }
            if (kw.length() >= 4 && kw.endsWith("e") && normalisedText.contains(" " + kw.substring(0, kw.length() - 1) + "ing")) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, every run of punctuation or spaces as one space, padded, so "Pre-signed URL" = " pre signed url ". */
    static String normalise(String s) {
        return " " + s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9:]+", " ").strip() + " ";
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }
}
