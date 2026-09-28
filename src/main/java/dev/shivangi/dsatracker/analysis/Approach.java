package dev.shivangi.dsatracker.analysis;

/**
 * A known way to solve a NeetCode problem, from {@code best-approaches.json}.
 *
 * @param name     short name, e.g. "Two pointers"
 * @param time     e.g. "O(n)"
 * @param space    e.g. "O(1)"
 * @param idea     one or two sentences on how it works
 * @param advanced true for approaches rarely expected in interviews (Manacher's algorithm, say):
 *                 only mentioned as "going further" once your solution matches the usual best
 */
public record Approach(String name, String time, String space, String idea, boolean advanced) {
}
