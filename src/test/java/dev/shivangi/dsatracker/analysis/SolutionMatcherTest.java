package dev.shivangi.dsatracker.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolutionMatcherTest {

    private static boolean defines(String code, CodeLanguage lang, String... names) {
        return SolutionMatcher.definesAny(code, lang, List.of(names));
    }

    @Test
    void findsLeetCodeFunctionsInEveryLanguage() {
        assertTrue(defines("class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        return null;\n    }\n}",
                CodeLanguage.JAVA, "twoSum"));
        assertTrue(defines("class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        pass\n",
                CodeLanguage.PYTHON, "twoSum"));
        assertTrue(defines("var twoSum = function(nums, target) {\n};", CodeLanguage.JAVASCRIPT, "twoSum"));
        assertTrue(defines("const twoSum = (nums, target) => {\n};", CodeLanguage.JAVASCRIPT, "twoSum"));
        assertTrue(defines("function twoSum(nums, target) {\n}", CodeLanguage.JAVASCRIPT, "twoSum"));
        assertTrue(defines("class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        return {};\n    }\n};",
                CodeLanguage.CPP, "twoSum"));
    }

    @Test
    void findsDesignClasses() {
        assertTrue(defines("class LRUCache {\n    public LRUCache(int capacity) {\n    }\n}", CodeLanguage.JAVA, "LRUCache"));
        assertTrue(defines("class LRUCache:\n    def __init__(self, capacity):\n        pass\n", CodeLanguage.PYTHON, "LRUCache"));
        assertTrue(defines("var LRUCache = function(capacity) {\n};", CodeLanguage.JAVASCRIPT, "LRUCache"));
    }

    @Test
    void codeForAnotherProblemDoesntPass() {
        String containsDuplicate = "class Solution {\n    public boolean containsDuplicate(int[] nums) {\n        return false;\n    }\n}";
        assertFalse(defines(containsDuplicate, CodeLanguage.JAVA, "twoSum"));
    }

    @Test
    void callsCommentsAndStringsDontCount() {
        String code = "class Solution:\n    def sortList(self, a):\n        # merge(a, b) would work too\n"
                + "        s = \"def merge(x):\"\n        return merge(a, a)\n";
        assertFalse(defines(code, CodeLanguage.PYTHON, "merge"));
        assertFalse(defines("int x = merge(a, b);", CodeLanguage.JAVA, "merge"));
    }

    @Test
    void alternativeNamesAreAccepted() {
        assertTrue(defines("def hasDuplicate(nums):\n    pass\n", CodeLanguage.PYTHON, "containsDuplicate", "hasDuplicate"));
    }
}
