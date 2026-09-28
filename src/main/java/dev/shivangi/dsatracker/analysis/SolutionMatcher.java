package dev.shivangi.dsatracker.analysis;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks that code is a solution to the problem it's saved under, the way LeetCode names things:
 * a function named after the problem ({@code twoSum}) or, for design problems, a class
 * ({@code LRUCache}). Only definitions count, so a call to {@code merge(...)} in some other
 * solution doesn't pass as Merge Intervals.
 */
public final class SolutionMatcher {

    private SolutionMatcher() {
    }

    /** True if the code defines at least one of {@code names} (comments and strings ignored). */
    public static boolean definesAny(String code, CodeLanguage language, List<String> names) {
        String clean = CodeStructure.clean(code, language);
        return names.stream().anyMatch(n -> defines(clean, n));
    }

    static boolean defines(String clean, String name) {
        String n = Pattern.quote(name);
        String any = "(?m)"
                + "\\bclass\\s+" + n + "\\b"                                     // class LRUCache
                + "|\\bdef\\s+" + n + "\\s*\\("                                  // def twoSum(
                + "|\\bfunction\\s+" + n + "\\s*\\("                             // function twoSum(
                + "|\\b" + n + "\\s*=\\s*(async\\s+)?(function\\b|\\([^()]*\\)\\s*=>|\\w+\\s*=>)"   // var twoSum = function / (...) =>
                + "|\\b" + n + "\\s*\\([^;{}]*\\)\\s*(const\\s*)?(throws\\s+[\\w.,\\s]+)?\\{";   // int[] twoSum(...) {
        return Pattern.compile(any).matcher(clean).find();
    }
}
