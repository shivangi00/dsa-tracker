package dev.shivangi.dsatracker.github;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoLayoutTest {

    private static final RepoLayout.Analysis LINEAR = new RepoLayout.Analysis("O(n)", "O(n)",
            List.of("Time: one pass over nums: O(n)", "Space: a set of up to n numbers: O(n)"), "estimate");

    private static RepoLayout.Problem twoSum() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        return new RepoLayout.Problem(2, "Arrays & Hashing", 1, "Two Sum", "EASY", "https://leetcode.com/problems/two-sum/", false, List.of(
                new RepoLayout.Sitting(0, 1, d, "Medium", "Brute force first.\n\n- complement in a map", null, List.of(
                        new RepoLayout.Version(1, "JAVA", "class Solution {}", null),
                        new RepoLayout.Version(2, "PYTHON", "class Solution:\n    pass\n\n\n", LINEAR))),
                new RepoLayout.Sitting(1, 1, d.plusDays(3), "Again", "Forgot the map.", null, List.of()),
                new RepoLayout.Sitting(1, 2, d.plusDays(4), "Good", null, "https://excalidraw.com/#json=a,b", List.of(
                        new RepoLayout.Version(1, "JAVA", "class Solution { int[] twoSum() { return null; } }", LINEAR)))));
    }

    @Test
    void topicThenQuestionThenSitting() {
        Map<String, String> files = RepoLayout.problemFiles(twoSum());
        assertEquals(List.of(
                "01 Arrays & Hashing/002 Two Sum/README.md",
                "01 Arrays & Hashing/002 Two Sum/Solved/notes.md",
                "01 Arrays & Hashing/002 Two Sum/Solved/v1.java",
                "01 Arrays & Hashing/002 Two Sum/Solved/v2.py",
                "01 Arrays & Hashing/002 Two Sum/Revision 1/notes.md",
                "01 Arrays & Hashing/002 Two Sum/Revision 1 (try 2)/notes.md",
                "01 Arrays & Hashing/002 Two Sum/Revision 1 (try 2)/v1.java"), List.copyOf(files.keySet()));
        assertEquals("class Solution:\n    pass\n", files.get("01 Arrays & Hashing/002 Two Sum/Solved/v2.py"));
    }

    @Test
    void notesHoldTheRatingNotesDrawingAndEachVersionsAnalysis() {
        Map<String, String> files = RepoLayout.problemFiles(twoSum());
        String solved = files.get("01 Arrays & Hashing/002 Two Sum/Solved/notes.md");
        assertTrue(solved.contains("**Rating:** Medium"));
        assertTrue(solved.contains("- complement in a map"));
        assertTrue(solved.contains("## Version 1 · [v1.java](v1.java)\n\n_Not analysed._"));
        assertTrue(solved.contains("**Time:** `O(n)` · **Space:** `O(n)`"));
        assertTrue(solved.contains("- Time: one pass over nums: O(n)"));
        String retry = files.get("01 Arrays & Hashing/002 Two Sum/Revision 1 (try 2)/notes.md");
        assertTrue(retry.contains("_No notes._"));
        assertTrue(retry.contains("[Drawing on Excalidraw](https://excalidraw.com/#json=a,b)"));
    }

    @Test
    void theProblemPageListsEverySittingWithLinks() {
        String readme = RepoLayout.problemFiles(twoSum()).get("01 Arrays & Hashing/002 Two Sum/README.md");
        assertTrue(readme.contains("Easy · Arrays & Hashing · [Problem](https://leetcode.com/problems/two-sum/)"));
        assertTrue(readme.contains("| [Revision 1 (try 2)](Revision%201%20%28try%202%29/notes.md) | 2026-10-05 | Good | `O(n)` | `O(n)` | 1 version |"));
    }

    @Test
    void namesAreSafeAsFoldersEverywhere() {
        assertEquals("a b c", RepoLayout.safe("a/b\\c"));
        assertEquals("What is", RepoLayout.safe("What is?"));
        assertEquals("Untitled", RepoLayout.safe(" ... "));
        assertEquals(80, RepoLayout.safe("x".repeat(200)).length());
        assertEquals("Pow(x, n)", RepoLayout.safe("Pow(x, n)"));   // brackets and commas are fine
    }

    @Test
    void ownProblemsGoUnderOtherWithoutANumber() {
        RepoLayout.Problem mine = new RepoLayout.Problem(null, "Other", 19, "My: tricky one?", null, null, false, List.of());
        assertEquals("19 Other/My tricky one", RepoLayout.problemFolder(mine));
    }

    @Test
    void theFrontPageGroupsByTopicAndLinksEachProblem() {
        RepoLayout.Problem two = twoSum();
        RepoLayout.Problem valid = new RepoLayout.Problem(10, "Two Pointers", 2, "Valid Palindrome", "EASY", null, true, List.of());
        String md = RepoLayout.rootReadme(List.of(valid, two), "nell");
        assertTrue(md.startsWith("# NeetCode 150 · nell's solutions"));
        assertTrue(md.contains("2 problems solved, 1 fully revised."));
        assertTrue(md.indexOf("## Arrays & Hashing") < md.indexOf("## Two Pointers"));
        assertTrue(md.contains("| [Two Sum](01%20Arrays%20%26%20Hashing/002%20Two%20Sum) | Easy | Revision 1 (try 2) | `O(n)` time, `O(n)` space |"));
        assertFalse(md.contains("null"));
    }

    @Test
    void theCommitMessageSaysWhatHappened() {
        assertEquals("Two Sum: revision 1 (try 2) (Good) · O(n) time", RepoLayout.commitMessage(twoSum()));
    }
}
