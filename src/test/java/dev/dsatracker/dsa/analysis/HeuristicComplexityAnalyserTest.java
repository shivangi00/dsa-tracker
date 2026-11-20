package dev.dsatracker.dsa.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.dsatracker.dsa.analysis.CodeLanguage.CPP;
import static dev.dsatracker.dsa.analysis.CodeLanguage.JAVA;
import static dev.dsatracker.dsa.analysis.CodeLanguage.JAVASCRIPT;
import static dev.dsatracker.dsa.analysis.CodeLanguage.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real NeetCode-style solutions with their known complexities. */
class HeuristicComplexityAnalyserTest {

    private final HeuristicComplexityAnalyser analyser = new HeuristicComplexityAnalyser();

    private ComplexityAnalysis expect(String time, String space, String code, CodeLanguage lang) {
        ComplexityAnalysis a = analyser.analyse(code, lang);
        assertEquals(time + " / " + space, a.time() + " / " + a.space(), String.join("\n", a.reasons()));
        return a;
    }

    // ---------------------------------------------------------------- arrays & hashing

    @Test
    void twoSumWithAHashMap() {
        expect("O(n)", "O(n)", """
                class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        Map<Integer, Integer> seen = new HashMap<>();
                        for (int i = 0; i < nums.length; i++) {
                            int need = target - nums[i];
                            if (seen.containsKey(need)) return new int[]{seen.get(need), i};
                            seen.put(nums[i], i);
                        }
                        return new int[0];
                    }
                }""", JAVA);
    }

    @Test
    void bruteForceNestedLoops() {
        expect("O(n²)", "O(1)", """
                public boolean containsDuplicate(int[] nums) {
                    for (int i = 0; i < nums.length; i++)
                        for (int j = i + 1; j < nums.length; j++)
                            if (nums[i] == nums[j]) return true;
                    return false;
                }""", JAVA);
    }

    @Test
    void anagramWithAFixed26LetterCount() {
        expect("O(n)", "O(1)", """
                public boolean isAnagram(String s, String t) {
                    if (s.length() != t.length()) return false;
                    int[] count = new int[26];
                    for (int i = 0; i < s.length(); i++) {
                        count[s.charAt(i) - 'a']++;
                        count[t.charAt(i) - 'a']--;
                    }
                    for (int c : count) if (c != 0) return false;
                    return true;
                }""", JAVA);
    }

    @Test
    void sortThenScan() {
        expect("O(n log n)", "O(1)", """
                class Solution:
                    def containsDuplicate(self, nums):
                        nums.sort()
                        for i in range(1, len(nums)):
                            if nums[i] == nums[i - 1]:
                                return True
                        return False
                """, PYTHON);
    }

    @Test
    void productExceptSelfDoesNotCountTheOutput() {
        expect("O(n)", "O(1)", """
                def productExceptSelf(nums):
                    res = [1] * len(nums)
                    prefix = 1
                    for i in range(len(nums)):
                        res[i] = prefix
                        prefix *= nums[i]
                    postfix = 1
                    for i in range(len(nums) - 1, -1, -1):
                        res[i] *= postfix
                        postfix *= nums[i]
                    return res
                """, PYTHON);
    }

    // ---------------------------------------------------------------- two pointers, windows, stacks

    @Test
    void binarySearch() {
        expect("O(log n)", "O(1)", """
                public int search(int[] nums, int target) {
                    int lo = 0, hi = nums.length - 1;
                    while (lo <= hi) {
                        int mid = lo + (hi - lo) / 2;
                        if (nums[mid] == target) return mid;
                        else if (nums[mid] < target) lo = mid + 1;
                        else hi = mid - 1;
                    }
                    return -1;
                }""", JAVA);
    }

    @Test
    void threeSumTwoPointersInsideALoop() {
        expect("O(n²)", "O(1)", """
                def threeSum(nums):
                    res = []
                    nums.sort()
                    for i, a in enumerate(nums):
                        if i > 0 and a == nums[i - 1]:
                            continue
                        l, r = i + 1, len(nums) - 1
                        while l < r:
                            s = a + nums[l] + nums[r]
                            if s > 0:
                                r -= 1
                            elif s < 0:
                                l += 1
                            else:
                                res.append([a, nums[l], nums[r]])
                                l += 1
                                while l < r and nums[l] == nums[l - 1]:
                                    l += 1
                    return res
                """, PYTHON);
    }

    @Test
    void slidingWindowIsAmortisedLinear() {
        expect("O(n)", "O(n)", """
                def lengthOfLongestSubstring(s):
                    seen = set()
                    l = 0
                    best = 0
                    for r in range(len(s)):
                        while s[r] in seen:
                            seen.remove(s[l])
                            l += 1
                        seen.add(s[r])
                        best = max(best, r - l + 1)
                    return best
                """, PYTHON);
    }

    @Test
    void monotonicStackIsAmortisedLinear() {
        expect("O(n)", "O(n)", """
                public int[] dailyTemperatures(int[] t) {
                    int[] ans = new int[t.length];
                    Deque<Integer> stack = new ArrayDeque<>();
                    for (int i = 0; i < t.length; i++) {
                        while (!stack.isEmpty() && t[stack.peek()] < t[i]) {
                            int j = stack.pop();
                            ans[j] = i - j;
                        }
                        stack.push(i);
                    }
                    return ans;
                }""", JAVA);
    }

    // ---------------------------------------------------------------- heaps

    @Test
    void heapPushInsideALoop() {
        expect("O(n log n)", "O(n)", """
                import heapq
                def findKthLargest(nums, k):
                    heap = []
                    for n in nums:
                        heapq.heappush(heap, n)
                        if len(heap) > k:
                            heapq.heappop(heap)
                    return heap[0]
                """, PYTHON);
    }

    // ---------------------------------------------------------------- recursion

    @Test
    void naiveFibonacciIsExponential() {
        ComplexityAnalysis a = analyser.analyse("""
                def fib(n):
                    if n <= 1: return n
                    return fib(n - 1) + fib(n - 2)
                """, PYTHON);
        assertEquals("O(2ⁿ) / O(n)", a.time() + " / " + a.space());
        assertEquals("low", a.confidence());
    }

    @Test
    void memoisedFibonacciIsLinear() {
        expect("O(n)", "O(n)", """
                from functools import lru_cache
                @lru_cache(None)
                def fib(n):
                    if n <= 1:
                        return n
                    return fib(n - 1) + fib(n - 2)
                """, PYTHON);
    }

    @Test
    void memoisedTwoIndexDpIsQuadratic() {
        expect("O(n²)", "O(n)", """
                def longestCommonSubsequence(a, b):
                    memo = {}
                    def dfs(i, j):
                        if i == len(a) or j == len(b):
                            return 0
                        if (i, j) in memo:
                            return memo[(i, j)]
                        if a[i] == b[j]:
                            memo[(i, j)] = 1 + dfs(i + 1, j + 1)
                        else:
                            memo[(i, j)] = max(dfs(i + 1, j), dfs(i, j + 1))
                        return memo[(i, j)]
                    return dfs(0, 0)
                """, PYTHON);
    }

    @Test
    void treeDepthVisitsEachNodeOnce() {
        expect("O(n)", "O(h)", """
                public int maxDepth(TreeNode root) {
                    if (root == null) return 0;
                    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
                }""", JAVA);
    }

    @Test
    void mergeSortIsNLogN() {
        expect("O(n log n)", "O(n)", """
                void mergeSort(int[] a, int l, int r) {
                    if (l >= r) return;
                    int m = (l + r) / 2;
                    mergeSort(a, l, m);
                    mergeSort(a, m + 1, r);
                    merge(a, l, m, r);
                }
                void merge(int[] a, int l, int m, int r) {
                    int[] tmp = new int[r - l + 1];
                    int i = l, j = m + 1, k = 0;
                    while (i <= m && j <= r) tmp[k++] = a[i] <= a[j] ? a[i++] : a[j++];
                    while (i <= m) tmp[k++] = a[i++];
                    while (j <= r) tmp[k++] = a[j++];
                    for (k = 0; k < tmp.length; k++) a[l + k] = tmp[k];
                }""", JAVA);
    }

    @Test
    void permutationsBacktracking() {
        expect("O(n · n!)", "O(n)", """
                public List<List<Integer>> permute(int[] nums) {
                    List<List<Integer>> res = new ArrayList<>();
                    backtrack(nums, new ArrayList<>(), new boolean[nums.length], res);
                    return res;
                }
                private void backtrack(int[] nums, List<Integer> path, boolean[] used, List<List<Integer>> res) {
                    if (path.size() == nums.length) { res.add(new ArrayList<>(path)); return; }
                    for (int i = 0; i < nums.length; i++) {
                        if (used[i]) continue;
                        used[i] = true;
                        path.add(nums[i]);
                        backtrack(nums, path, used, res);
                        path.remove(path.size() - 1);
                        used[i] = false;
                    }
                }""", JAVA);
    }

    @Test
    void subsetsIncludeOrSkip() {
        expect("O(n · 2ⁿ)", "O(n)", """
                class Solution:
                    def subsets(self, nums):
                        res = []
                        path = []
                        def dfs(i):
                            if i == len(nums):
                                res.append(path[:])
                                return
                            path.append(nums[i])
                            dfs(i + 1)
                            path.pop()
                            dfs(i + 1)
                        dfs(0)
                        return res
                """, PYTHON);
    }

    // ---------------------------------------------------------------- grids & graphs

    @Test
    void numberOfIslandsDfsIsLinearInTheGrid() {
        expect("O(m·n)", "O(m·n)", """
                class Solution:
                    def numIslands(self, grid):
                        rows, cols = len(grid), len(grid[0])
                        def dfs(r, c):
                            if r < 0 or c < 0 or r >= rows or c >= cols or grid[r][c] != "1":
                                return
                            grid[r][c] = "0"
                            dfs(r + 1, c)
                            dfs(r - 1, c)
                            dfs(r, c + 1)
                            dfs(r, c - 1)
                        count = 0
                        for r in range(rows):
                            for c in range(cols):
                                if grid[r][c] == "1":
                                    dfs(r, c)
                                    count += 1
                        return count
                """, PYTHON);
    }

    @Test
    void bfsOverAGridFromEveryCell() {
        expect("O(m·n)", "O(m·n)", """
                public int numIslands(char[][] grid) {
                    int m = grid.length, n = grid[0].length, count = 0;
                    boolean[][] visited = new boolean[m][n];
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int r = 0; r < m; r++) {
                        for (int c = 0; c < n; c++) {
                            if (grid[r][c] != '1' || visited[r][c]) continue;
                            count++;
                            Queue<int[]> q = new ArrayDeque<>();
                            q.add(new int[]{r, c});
                            visited[r][c] = true;
                            while (!q.isEmpty()) {
                                int[] cur = q.poll();
                                for (int[] d : dirs) {
                                    int nr = cur[0] + d[0], nc = cur[1] + d[1];
                                    if (nr >= 0 && nc >= 0 && nr < m && nc < n && grid[nr][nc] == '1' && !visited[nr][nc]) {
                                        visited[nr][nc] = true;
                                        q.add(new int[]{nr, nc});
                                    }
                                }
                            }
                        }
                    }
                    return count;
                }""", JAVA);
    }

    @Test
    void graphDfsCountsEdgesOnce() {
        expect("O(n)", "O(n)", """
                def countComponents(n, edges):
                    graph = {i: [] for i in range(n)}
                    for a, b in edges:
                        graph[a].append(b)
                        graph[b].append(a)
                    visited = set()
                    def dfs(node):
                        visited.add(node)
                        for nei in graph[node]:
                            if nei not in visited:
                                dfs(nei)
                    count = 0
                    for i in range(n):
                        if i not in visited:
                            dfs(i)
                            count += 1
                    return count
                """, PYTHON);
    }

    // ---------------------------------------------------------------- DP

    @Test
    void twoDimensionalDpTable() {
        expect("O(n²)", "O(n²)", """
                public int longestCommonSubsequence(String a, String b) {
                    int[][] dp = new int[a.length() + 1][b.length() + 1];
                    for (int i = a.length() - 1; i >= 0; i--)
                        for (int j = b.length() - 1; j >= 0; j--)
                            dp[i][j] = a.charAt(i) == b.charAt(j) ? 1 + dp[i + 1][j + 1] : Math.max(dp[i + 1][j], dp[i][j + 1]);
                    return dp[0][0];
                }""", JAVA);
    }

    @Test
    void climbingStairsDpArray() {
        expect("O(n)", "O(n)", """
                def climbStairs(n):
                    dp = [0] * (n + 1)
                    dp[0], dp[1] = 1, 1
                    for i in range(2, n + 1):
                        dp[i] = dp[i - 1] + dp[i - 2]
                    return dp[n]
                """, PYTHON);
    }

    // ---------------------------------------------------------------- two sizes: n items of size k

    @Test
    void groupAnagramsByLetterCountIsNTimesK() {
        expect("O(n·k)", "O(n)", """
                def groupAnagrams(strs):
                    groups = defaultdict(list)
                    for s in strs:
                        count = [0] * 26
                        for c in s:
                            count[ord(c) - ord('a')] += 1
                        groups[tuple(count)].append(s)
                    return list(groups.values())
                """, PYTHON);
    }

    @Test
    void groupAnagramsBySortingEachWord() {
        expect("O(n·k log k)", "O(n)", """
                public List<List<String>> groupAnagrams(String[] strs) {
                    Map<String, List<String>> groups = new HashMap<>();
                    for (String s : strs) {
                        char[] chars = s.toCharArray();
                        Arrays.sort(chars);
                        groups.computeIfAbsent(new String(chars), x -> new ArrayList<>()).add(s);
                    }
                    return new ArrayList<>(groups.values());
                }""", JAVA);
    }

    @Test
    void bucketsAreAmortisedAcrossTheOuterLoop() {
        expect("O(n)", "O(n)", """
                public int[] topKFrequent(int[] nums, int k) {
                    Map<Integer, Integer> count = new HashMap<>();
                    for (int n : nums) count.merge(n, 1, Integer::sum);
                    List<Integer>[] buckets = new List[nums.length + 1];
                    for (int key : count.keySet()) {
                        int f = count.get(key);
                        if (buckets[f] == null) buckets[f] = new ArrayList<>();
                        buckets[f].add(key);
                    }
                    int[] res = new int[k];
                    int idx = 0;
                    for (int i = buckets.length - 1; i >= 0 && idx < k; i--)
                        if (buckets[i] != null) for (int v : buckets[i]) { if (idx < k) res[idx++] = v; }
                    return res;
                }""", JAVA);
    }

    @Test
    void dijkstraWithAHeap() {
        expect("O(n log n)", "O(n)", """
                def networkDelayTime(times, n, k):
                    graph = defaultdict(list)
                    for u, v, w in times:
                        graph[u].append((v, w))
                    heap = [(0, k)]
                    visited = set()
                    t = 0
                    while heap:
                        w1, n1 = heapq.heappop(heap)
                        if n1 in visited:
                            continue
                        visited.add(n1)
                        t = w1
                        for n2, w2 in graph[n1]:
                            if n2 not in visited:
                                heapq.heappush(heap, (w1 + w2, n2))
                    return t if len(visited) == n else -1
                """, PYTHON);
    }

    @Test
    void nestedLoopsOverDifferentInputsSaySo() {
        ComplexityAnalysis a = analyser.analyse("""
                public int coinChange(int[] coins, int amount) {
                    int[] dp = new int[amount + 1];
                    Arrays.fill(dp, amount + 1);
                    dp[0] = 0;
                    for (int a = 1; a <= amount; a++)
                        for (int c : coins)
                            if (c <= a) dp[a] = Math.min(dp[a], 1 + dp[a - c]);
                    return dp[amount] > amount ? -1 : dp[amount];
                }""", JAVA);
        assertEquals("O(n²)", a.time());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("amount and coins") && r.contains("O(m·n)")));
        assertEquals("medium", a.confidence());
    }

    // ---------------------------------------------------------------- other languages & edge cases

    @Test
    void javascriptArrowFunctionsAndNestedLoops() {
        expect("O(n²)", "O(1)", """
                const hasPairWithSum = (nums, target) => {
                  for (let i = 0; i < nums.length; i++) {
                    for (let j = i + 1; j < nums.length; j++) {
                      if (nums[i] + nums[j] === target) return true;
                    }
                  }
                  return false;
                };""", JAVASCRIPT);
    }

    @Test
    void cppHashSetSingleLoop() {
        expect("O(n)", "O(n)", """
                class Solution {
                public:
                    bool containsDuplicate(vector<int>& nums) {
                        unordered_set<int> seen;
                        for (int x : nums) {
                            if (seen.count(x)) return true;
                            seen.insert(x);
                        }
                        return false;
                    }
                };""", CPP);
    }

    @Test
    void loopsInsideStringsAndCommentsAreIgnored() {
        expect("O(1)", "O(1)", """
                public String hello() {
                    // for (int i = 0; i < n; i++) { for (;;) {} }
                    /* while (true) { } */
                    return "for (int i = 0; i < n; i++) {";
                }""", JAVA);
    }

    @Test
    void digitLoopIsLogarithmic() {
        expect("O(log n)", "O(1)", """
                def sumOfDigits(n):
                    total = 0
                    while n > 0:
                        total += n % 10
                        n //= 10
                    return total
                """, PYTHON);
    }

    @Test
    void everyResultExplainsItselfAndSaysItIsAnEstimate() {
        ComplexityAnalysis a = analyser.analyse("for x in nums:\n    print(x)\n", PYTHON);
        assertEquals("O(n)", a.time());
        assertEquals(ComplexityAnalysis.ESTIMATE, a.source());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("Line 1")));
        assertTrue(a.reasons().get(a.reasons().size() - 1).contains("estimate"));
    }

    @Test
    void garbageNeverThrows() {
        ComplexityAnalysis a = analyser.analyse("}}}{{{ ((( for while )))", JAVA);
        assertFalse(a.time().isEmpty());
    }

    @Test
    void sortedMakesACopySoItCountsAsSpace() {
        ComplexityAnalysis a = analyser.analyse("def f(s, t):\n    return sorted(s) == sorted(t)\n", CodeLanguage.PYTHON);
        assertEquals("O(n log n) / O(n)", a.time() + " / " + a.space());
        assertTrue(a.reasons().stream().anyMatch(r -> r.startsWith("Space: Line 2: sorted() builds a new sorted copy")));
    }

    @Test
    void stepsAreSplitIntoTimeThenSpaceInLineOrderWithTotals() {
        String code = "def f(nums):\n    seen = set()\n    for i in range(len(nums)):\n        for j in range(len(nums)):\n"
                + "            seen.add(nums[i] + nums[j])\n    return len(seen)\n";
        List<String> r = analyser.analyse(code, CodeLanguage.PYTHON).reasons();
        int outer = indexOf(r, "Time: Line 3"), inner = indexOf(r, "Time: Line 4"), timeTotal = indexOf(r, "Time: total → O(n²)");
        int space = indexOf(r, "Space: Line 2"), spaceTotal = indexOf(r, "Space: total → O(n)");
        assertTrue(outer >= 0 && outer < inner && inner < timeTotal && timeTotal < space && space < spaceTotal, String.join("\n", r));
    }

    @Test
    void codeWithNothingThatGrowsSaysSoForBoth() {
        List<String> r = analyser.analyse("def f(a, b):\n    return a + b\n", CodeLanguage.PYTHON).reasons();
        assertTrue(r.get(0).startsWith("Time: no loops"), String.join("\n", r));
        assertTrue(r.get(1).startsWith("Space: only single variables"), String.join("\n", r));
    }

    private static int indexOf(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void sortingAHandfulOfValuesIsConstant() {
        String code = "def threeSum(nums):\n    res = set()\n    for i in range(len(nums)):\n        for j in range(i + 1, len(nums)):\n"
                + "            for k in range(j + 1, len(nums)):\n                if nums[i] + nums[j] + nums[k] == 0:\n"
                + "                    res.add(tuple(sorted((nums[i], nums[j], nums[k]))))\n    return [list(t) for t in res]\n";
        ComplexityAnalysis a = analyser.analyse(code, CodeLanguage.PYTHON);
        assertEquals("O(n³)", a.time(), String.join("\n", a.reasons()));
    }

    @Test
    void theReturnedArrayIsNotExtraSpace() {
        String code = "int[] firstK(int[] nums, int k) {\n    int out2 [] = new int [k];\n    for (int i = 0; i < k; i++) {\n"
                + "        out2[i] = nums[i];\n    }\n    return out2;\n}\n";
        ComplexityAnalysis a = analyser.analyse(code, CodeLanguage.JAVA);
        assertEquals("O(1)", a.space(), String.join("\n", a.reasons()));
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("isn't counted as extra space")));
    }

    // ---------------------------------------------------------------- reading a string piece by piece

    private void expectTime(String time, String code, CodeLanguage lang) {
        ComplexityAnalysis a = analyser.analyse(code, lang);
        assertEquals(time, a.time(), String.join("\n", a.reasons()));
    }

    /** Encode and Decode Strings: indexOf/substring start at the pointer, which then jumps past them. */
    @Test
    void decodingPieceByPieceIsLinear() {
        expectTime("O(n)", """
                class Solution {
                    public String encode(String arr[]) {
                        StringBuilder sb = new StringBuilder();
                        for(int i = 0; i < arr.length; i++) {
                            int len = arr[i].length();
                            sb.append(len);
                            sb.append("#");
                            sb.append(arr[i]);
                        }
                        return sb.toString();
                    }

                    public ArrayList<String> decode(String s) {
                        ArrayList<String> ans = new ArrayList<>();
                        int i = 0;
                        int n = s.length();
                        while(i < n) {
                            int delimiterIndex = s.indexOf('#', i);
                            int len = Integer.parseInt(s.substring(i, delimiterIndex));
                            int stringStartIndex = delimiterIndex + 1;
                            int stringEndIndex = stringStartIndex + len;
                            ans.add(s.substring(stringStartIndex, stringEndIndex));
                            i = stringEndIndex;
                        }
                        return ans;
                    }
                }""", JAVA);
    }

    @Test
    void decodingPieceByPieceInPythonIsLinear() {
        expectTime("O(n)", """
                class Solution:
                    def decode(self, s):
                        res, i = [], 0
                        while i < len(s):
                            j = s.index('#', i)
                            length = int(s[i:j])
                            res.append(s[j + 1:j + 1 + length])
                            i = j + 1 + length
                        return res""", PYTHON);
    }

    /** Re-reading from the start on every pass really is quadratic. */
    @Test
    void rereadingFromTheStartIsStillQuadratic() {
        expectTime("O(n²)", """
                class Solution {
                    public int count(String s) {
                        int i = 0, total = 0;
                        while (i < s.length()) {
                            String prefix = s.substring(0, i);
                            total += prefix.length();
                            i = i + 2;
                        }
                        return total;
                    }
                }""", JAVA);
        expectTime("O(n²)", """
                class Solution {
                    public int count(String s) {
                        int i = 0, total = 0;
                        while (i < s.length()) {
                            total += s.indexOf('#');
                            i++;
                        }
                        return total;
                    }
                }""", JAVA);
    }

    // ---------------------------------------------------------------- binary search over values

    /** Koko Eating Bananas, as written with si/ei: a binary search over speeds 1…max(piles), each step O(n). */
    @Test
    void binarySearchOverValuesIsNLogM() {
        expect("O(n log m)", "O(1)", """
                class Solution:
                    def isPossibleToEat(self, piles: list[int], k: int, h: int) -> bool:
                        time_taken_at_k_speed = 0
                        for pile in piles:
                            time_taken_at_k_speed += math.ceil(pile / k)
                            if(time_taken_at_k_speed > h):
                                return False
                        return True

                    def minEatingSpeed(self, piles: list[int], h: int) -> int:
                        si = 1
                        ei = max(piles)
                        while si < ei:
                            eating_speed = (si + ei) // 2
                            if not self.isPossibleToEat(piles, eating_speed, h):
                                si = eating_speed + 1
                            else:
                                ei = eating_speed
                        return si""", PYTHON);
    }

    @Test
    void binarySearchOverValuesInJava() {
        expect("O(n log m)", "O(1)", """
                class Solution {
                    public int minEatingSpeed(int[] piles, int h) {
                        int lo = 1, hi = 1_000_000_000;
                        while (lo < hi) {
                            int speed = lo + (hi - lo) / 2;
                            long hours = 0;
                            for (int p : piles) hours += (p + speed - 1) / speed;
                            if (hours <= h) hi = speed; else lo = speed + 1;
                        }
                        return lo;
                    }
                }""", JAVA);
    }

    /** Any names work, and a search over positions (bounded by the length) stays O(log n). */
    @Test
    void binarySearchWithUnusualNamesOverPositions() {
        expect("O(log n)", "O(1)", """
                class Solution:
                    def search(self, nums, target):
                        s, e = 0, len(nums) - 1
                        while s <= e:
                            middle = (s + e) // 2
                            if nums[middle] == target:
                                return middle
                            if nums[middle] < target:
                                s = middle + 1
                            else:
                                e = middle - 1
                        return -1""", PYTHON);
    }

    // ---------------------------------------------------------------- merge sort on a linked list

    /**
     * Merge K Sorted Lists by joining every list and merge-sorting the result: the middle is found with
     * midNode() (slow/fast pointers), not passed as "mid", and getTail(arr[i]) walks each list once.
     */
    @Test
    void medianOfTwoSortedArraysWithWhileTrue() {
        expect("O(log(min(m, n)))", "O(1)", """
                class Solution:
                    def findMedianSortedArrays(self, nums1: list[int], nums2: list[int]) -> float:
                        n1 = len(nums1)
                        n2 = len(nums2)

                        if n2 < n1:
                            nums1, nums2 = nums2, nums1

                        l, r = 0, len(nums1) - 1
                        total = n1 + n2
                        half = total // 2

                        while True:
                            i = (l + r) // 2 # nums1
                            j = half - i - 2 # nums2

                            left1 = nums1[i] if i >= 0 else float('-inf')
                            right1 = nums1[i + 1] if (i + 1) < len(nums1) else float('inf')
                            left2 = nums2[j] if j >= 0 else float('-inf')
                            right2 = nums2[j + 1] if (j + 1) < len(nums2) else float('inf')

                            # check if partition is valid
                            if left1 <= right2 and left2 <= right1:
                                #odd
                                if total % 2:
                                    return min(right1, right2)
                                return (max(left1, left2) + min(right1, right2)) / 2
                            elif left1 > right2:
                                r = i - 1
                            else:
                                l = i + 1
                """, PYTHON);
    }

    @Test
    void medianOfTwoSortedArraysInJava() {
        expect("O(log(min(m, n)))", "O(1)", """
                class Solution {
                    public double findMedianSortedArrays(int[] a, int[] b) {
                        if (a.length > b.length) return findMedianSortedArrays(b, a);
                        int total = a.length + b.length, half = (total + 1) / 2;
                        int lo = 0, hi = a.length;
                        while (lo <= hi) {
                            int i = (lo + hi) / 2, j = half - i;
                            int l1 = i == 0 ? Integer.MIN_VALUE : a[i - 1];
                            int r1 = i == a.length ? Integer.MAX_VALUE : a[i];
                            int l2 = j == 0 ? Integer.MIN_VALUE : b[j - 1];
                            int r2 = j == b.length ? Integer.MAX_VALUE : b[j];
                            if (l1 <= r2 && l2 <= r1) {
                                if (total % 2 == 1) return Math.max(l1, l2);
                                return (Math.max(l1, l2) + Math.min(r1, r2)) / 2.0;
                            } else if (l1 > r2) hi = i - 1;
                            else lo = i + 1;
                        }
                        return 0;
                    }
                }
                """, JAVA);
    }

    @Test
    void binarySearchWrittenAsWhileTrue() {
        expect("O(log n)", "O(1)", """
                class Solution:
                    def search(self, nums: list[int], target: int) -> int:
                        lo, hi = 0, len(nums) - 1
                        while True:
                            if lo > hi:
                                return -1
                            mid = (lo + hi) // 2
                            if nums[mid] == target:
                                return mid
                            if nums[mid] < target:
                                lo = mid + 1
                            else:
                                hi = mid - 1
                """, PYTHON);
    }

    @Test
    void mergeKListsInPairsIsNLogK() {
        ComplexityAnalysis a = expect("O(n log k)", "O(log k)", """
                # Definition for singly-linked list.
                # class ListNode:
                #     def __init__(self, val=0, next=None):
                #         self.val = val
                #         self.next = next
                class Solution:
                    def merge2sorted(self, list1: ListNode | None, list2: ListNode | None) -> ListNode | None:
                        dummy = ListNode(0)
                        curr = dummy

                        while list1 and list2:
                            if list1.val < list2.val:
                                curr.next = list1
                                list1 = list1.next
                            else:
                                curr.next = list2
                                list2 = list2.next
                            curr = curr.next

                        curr.next = list1 if list1 else list2

                        return dummy.next

                    def mergeKLists_03(self, arr: list[ListNode | None], si: int, ei: int) -> ListNode | None:
                        if si == ei: return arr[si]

                        mid = (si + ei) // 2
                        return self.merge2sorted(self.mergeKLists_03(arr, si, mid), self.mergeKLists_03(arr, mid + 1, ei))

                    def mergeKLists(self, arr: list[ListNode | None]) -> ListNode | None:
                        if len(arr) == 0:
                            return None
                        return self.mergeKLists_03(arr, 0, len(arr) - 1)
                """, PYTHON);
        assertTrue(a.reasons().stream().anyMatch(l -> l.contains("k is the number of lists")), String.join("\n", a.reasons()));
    }

    @Test
    void mergeKListsInPairsInJava() {
        expect("O(n log k)", "O(log k)", """
                class Solution {
                    private ListNode merge(ListNode a, ListNode b) {
                        ListNode dummy = new ListNode(0), cur = dummy;
                        while (a != null && b != null) {
                            if (a.val < b.val) { cur.next = a; a = a.next; }
                            else { cur.next = b; b = b.next; }
                            cur = cur.next;
                        }
                        cur.next = a != null ? a : b;
                        return dummy.next;
                    }

                    private ListNode divide(ListNode[] lists, int lo, int hi) {
                        if (lo == hi) return lists[lo];
                        int mid = lo + (hi - lo) / 2;
                        return merge(divide(lists, lo, mid), divide(lists, mid + 1, hi));
                    }

                    public ListNode mergeKLists(ListNode[] lists) {
                        if (lists.length == 0) return null;
                        return divide(lists, 0, lists.length - 1);
                    }
                }
                """, JAVA);
    }

    @Test
    void linkedListMergeSortIsNLogN() {
        expect("O(n log n)", "O(log n)", """
                # Definition for singly-linked list.
                # class ListNode:
                #     def __init__(self, val=0, next=None):
                #         self.val = val
                #         self.next = next
                class Solution:
                    def merge2sorted(self, list1: ListNode | None, list2: ListNode | None) -> ListNode | None:
                        dummy = ListNode(0)
                        curr = dummy

                        while list1 and list2:
                            if list1.val < list2.val:
                                curr.next = list1
                                list1 = list1.next
                            else:
                                curr.next = list2
                                list2 = list2.next
                            curr = curr.next

                        curr.next = list1 if list1 else list2

                        return dummy.next

                    def midNode(self, head: ListNode | None) -> ListNode | None:
                        if head is None or head.next is None:
                            return head
                        slow = head
                        fast = head
                        while fast.next and fast.next.next:
                            slow = slow.next
                            fast = fast.next.next

                        return slow

                    def mergesort(self, head: ListNode | None) -> ListNode | None:
                        if head is None or head.next is None: return head
                        mid = self.midNode(head)
                        nHead = mid.next
                        mid.next = None

                        return self.merge2sorted(self.mergesort(head), self.mergesort(nHead))

                    def getTail(self, head: ListNode | None) -> ListNode | None:
                        if head is None or head.next is None: return head

                        curr = head
                        while curr.next:
                            curr = curr.next
                        return curr

                    def mergeKLists_02(self, arr: list[ListNode | None]) -> ListNode | None:
                        if len(arr) is 0: return None

                        dummy = ListNode(0)
                        curr = dummy

                        for i in range(len(arr)):
                            tail = self.getTail(arr[i])
                            if tail is not None:
                                curr.next = arr[i]
                                curr = tail
                        return self.mergesort(dummy.next)

                    def mergeKLists(self, lists: list[ListNode | None]) -> ListNode | None:
                        if len(lists) is 0:
                            return None
                        return self.mergeKLists_02(lists)""", PYTHON);
    }

    /** Two self-calls that don't split anything are still exponential. */
    @Test
    void twoCallsWithoutSplittingStayExponential() {
        expect("O(2ⁿ)", "O(n)", """
                class Solution:
                    def climb(self, n):
                        if n <= 1:
                            return 1
                        return self.climb(n - 1) + self.climb(n - 2)""", PYTHON);
    }
}
