package dev.shivangi.dsatracker.analysis;

/**
 * How an analysed solution compares with the best known approaches for its problem.
 *
 * @param verdict  {@link #FASTER}: a faster approach exists; {@link #LEANER}: same speed, less memory;
 *                 {@link #OPTIMAL}: already matches the best known; {@link #CHECK}: the analysis claims
 *                 better than the best known, so it's probably worth double-checking; {@link #FIXED}: the
 *                 input has a fixed size, so there's nothing meaningful to compare
 * @param message  one plain sentence saying the above with the numbers in it
 * @param approach the approach to look at (for OPTIMAL, the one your solution matches)
 * @param further  an advanced approach that is faster still, or null
 */
public record Recommendation(String verdict, String message, Approach approach, Approach further) {

    public static final String FASTER = "faster";
    public static final String LEANER = "leaner";
    public static final String OPTIMAL = "optimal";
    public static final String CHECK = "check";
    public static final String FIXED = "fixed";
}
