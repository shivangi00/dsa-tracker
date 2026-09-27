package dev.shivangi.dsatracker.analysis;

import org.junit.jupiter.api.Test;

import static dev.shivangi.dsatracker.analysis.CodeLanguage.CPP;
import static dev.shivangi.dsatracker.analysis.CodeLanguage.JAVA;
import static dev.shivangi.dsatracker.analysis.CodeLanguage.JAVASCRIPT;
import static dev.shivangi.dsatracker.analysis.CodeLanguage.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real NeetCode-style solutions with their known complexities. */
class HeuristicComplexityAnalyserTest {

    private final HeuristicComplexityAnalyser analyser = new HeuristicComplexityAnalyser();

    private void expect(String time, String space, String code, CodeLanguage lang) {
        ComplexityAnalysis a = analyser.analyse(code, lang);
        assertEquals(time + " / " + space, a.time() + " / " + a.space(), String.join("\n", a.reasons()));
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
}
