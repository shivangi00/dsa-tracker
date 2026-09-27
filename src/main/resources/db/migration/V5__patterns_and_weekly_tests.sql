-- V5: patterns, practice problems and weekly tests.
--
-- Every NeetCode 150 problem belongs to one pattern (the technique it teaches). Each pattern
-- has "practice" problems: different LeetCode problems that use the same technique, taken from
-- NeetCode's wider list (.problemSiteData.json, entries not in the 150), with LeetCode numbers.
-- The weekly test shows you practice problems for the patterns you studied that week.

CREATE TABLE patterns (
    id        SMALLINT     PRIMARY KEY,
    category  VARCHAR(40)  NOT NULL,
    name      VARCHAR(80)  NOT NULL UNIQUE,
    idea      VARCHAR(300) NOT NULL          -- one-line explanation shown after you answer
);

INSERT INTO patterns (id, category, name, idea) VALUES
    (1, 'Arrays & Hashing', 'Hash map lookup & counting', 'Store what you''ve seen in a hash map or set so each lookup is O(1).'),
    (2, 'Arrays & Hashing', 'Canonical keys for grouping', 'Turn each item into a normalised key (sorted string, counts, row/col/box id) and group or dedupe by it.'),
    (3, 'Arrays & Hashing', 'Counting / bucket sort', 'Index an array by value or frequency to sort or rank in O(n) instead of O(n log n).'),
    (4, 'Arrays & Hashing', 'Prefix & suffix accumulation', 'Precompute running sums or products from the left and right to answer range questions without nested loops.'),
    (5, 'Arrays & Hashing', 'Hash set membership & in-place marking', 'Put values in a set (or mark indices in place) and only start work where a sequence begins.'),
    (6, 'Arrays & Hashing', 'Encoding & data-structure design', 'Design the representation first: length prefixes, maps of maps, or a map plus an array.'),
    (7, 'Two Pointers', 'Pointers from both ends', 'Start at both ends and move the pointer that can only improve the answer.'),
    (8, 'Two Pointers', 'Sort, fix one, two-pointer the rest', 'Sort first, loop over one element, and solve the remaining pair with two pointers; skip duplicates.'),
    (9, 'Two Pointers', 'Left and right boundaries', 'The answer at each index depends on the best value to its left and right; sweep both ways.'),
    (10, 'Two Pointers', 'Read / write pointers in place', 'One pointer reads, one writes the next kept element, so the array is rewritten in place.'),
    (11, 'Sliding Window', 'Grow and shrink (longest valid window)', 'Expand the right edge; when the window breaks the rule, shrink from the left.'),
    (12, 'Sliding Window', 'Fixed-size window with counts', 'Slide a window of exact size k, adding one element and removing one each step.'),
    (13, 'Sliding Window', 'Shrink to the shortest valid window', 'Expand until the window is valid, then shrink as far as possible, recording the minimum.'),
    (14, 'Sliding Window', 'One pass tracking the best so far', 'Keep the best value seen so far (like the lowest price) and compare each new element with it.'),
    (15, 'Sliding Window', 'Monotonic deque', 'Keep a deque of candidates in decreasing order so the window''s max is always at the front.'),
    (16, 'Stack', 'Stack for matching & parsing', 'Push openers or operands, pop when the matching closer or operator arrives.'),
    (17, 'Stack', 'Design with an auxiliary stack', 'Keep a second structure alongside the main one so extra queries (min, frequency) stay O(1).'),
    (18, 'Stack', 'Monotonic stack', 'Keep the stack increasing or decreasing; popping tells you each element''s next greater or smaller item.'),
    (19, 'Binary Search', 'Binary search on sorted data', 'Halve a sorted search space each step, keeping track of the boundary you want.'),
    (20, 'Binary Search', 'Binary search on the answer', 'Guess the answer, check if it''s feasible in O(n), and binary search the smallest feasible guess.'),
    (21, 'Binary Search', 'Rotated or peak arrays', 'One half is always sorted (or sloping); decide which half can contain the answer.'),
    (22, 'Binary Search', 'Partition two sorted arrays', 'Binary search a split point so the left halves of both arrays hold the smaller half of all values.'),
    (23, 'Linked List', 'Reverse pointers', 'Walk the list re-pointing each node''s next to the previous node; use a dummy node for sub-lists.'),
    (24, 'Linked List', 'Fast & slow pointers', 'Move one pointer twice as fast (or n steps ahead) to find middles, cycles or the nth from the end.'),
    (25, 'Linked List', 'Dummy head & merging', 'Build the result behind a dummy node, taking the smaller head (or the next digit) each step.'),
    (26, 'Linked List', 'Hash map + linked list design', 'Combine a map for O(1) lookup with a (doubly) linked list for O(1) ordering.'),
    (27, 'Trees', 'DFS that returns a value upward', 'Solve each subtree recursively and combine the children''s answers at the parent.'),
    (28, 'Trees', 'Compare two trees', 'Recurse on both trees together, comparing nodes and their mirrored or matching children.'),
    (29, 'Trees', 'BFS level by level', 'Process the tree one level at a time with a queue, using the level''s size.'),
    (30, 'Trees', 'BST ordering (inorder and bounds)', 'Use left < node < right: inorder visits values in sorted order, and bounds narrow as you go down.'),
    (31, 'Trees', 'DFS passing state down', 'Carry information from the root down (max so far, running sum, path) as a parameter.'),
    (32, 'Trees', 'Build and serialise trees', 'Rebuild a tree from traversal orders or a string, using recursion with index ranges.'),
    (33, 'Heap / Priority Queue', 'Top-k with a size-k heap', 'Keep a heap of size k; the root is the kth best, and anything worse is thrown away.'),
    (34, 'Heap / Priority Queue', 'Greedy: always take the biggest', 'Repeatedly pop the largest (or most frequent) item, use it, and push back what''s left.'),
    (35, 'Heap / Priority Queue', 'Heap-driven simulation over time', 'Order events by time in a heap and process them as the clock moves forward.'),
    (36, 'Heap / Priority Queue', 'Two heaps', 'A max-heap for the lower half and a min-heap for the upper half keep the middle available.'),
    (37, 'Backtracking', 'Choose or skip (subsets and combinations)', 'At each step either include the next choice or skip it, undoing the choice after exploring.'),
    (38, 'Backtracking', 'Permutations with a used set', 'Build an ordering one position at a time, marking which items are already used.'),
    (39, 'Backtracking', 'Partition a string', 'Try every cut point for the next piece; recurse only if the piece is valid.'),
    (40, 'Backtracking', 'Board search with constraints', 'Place or visit cells one at a time, checking constraints, and undo on the way back.'),
    (41, 'Tries', 'Prefix tree', 'Store words character by character in a tree so prefix queries cost only the prefix length.'),
    (42, 'Graphs', 'Grid DFS / flood fill', 'Treat cells as nodes and DFS from each unvisited cell, marking what you reach.'),
    (43, 'Graphs', 'BFS for shortest paths (unweighted)', 'BFS explores in rings of equal distance; start from many sources at once if needed.'),
    (44, 'Graphs', 'Topological sort', 'Repeatedly take nodes with no remaining prerequisites (in-degree 0); a leftover means a cycle.'),
    (45, 'Graphs', 'Union-find and connected components', 'Merge nodes into groups as edges arrive; a union of two nodes already joined means a cycle.'),
    (46, 'Graphs', 'Graph traversal with a visited map', 'Walk an adjacency list with DFS/BFS, using a map from node to result (or colour) to avoid repeats.'),
    (47, 'Advanced Graphs', 'Dijkstra (weighted shortest path)', 'Always expand the cheapest known node next using a min-heap of (cost, node).'),
    (48, 'Advanced Graphs', 'Minimum spanning tree', 'Connect everything as cheaply as possible: add the cheapest edge that doesn''t form a cycle.'),
    (49, 'Advanced Graphs', 'Shortest path with a limit on steps', 'Relax edges level by level (Bellman-Ford or BFS) so you never use more than k steps.'),
    (50, 'Advanced Graphs', 'Eulerian path', 'Use every edge exactly once with a DFS that adds nodes to the path as it backtracks.'),
    (51, '1-D Dynamic Programming', 'Fibonacci-style DP', 'The answer at i depends only on the answers at i-1 and i-2 (or a few steps back).'),
    (52, '1-D Dynamic Programming', 'Unbounded knapsack', 'For each amount, try every item (reusable) and keep the best: dp[x] = best(dp[x - item] + 1).'),
    (53, '1-D Dynamic Programming', '0/1 knapsack (subset sum)', 'Each item is used at most once: iterate items outside and amounts backwards inside.'),
    (54, '1-D Dynamic Programming', 'Longest increasing subsequence', 'dp[i] = longest chain ending at i, built from earlier smaller items (or patience sorting in O(n log n)).'),
    (55, '1-D Dynamic Programming', 'Palindromes: expand around centre', 'Every palindrome has a centre; expand from each of the 2n-1 centres while the ends match.'),
    (56, '1-D Dynamic Programming', 'Kadane: best subarray ending here', 'Track the best (and for products, the worst) subarray ending at each index.'),
    (57, '2-D Dynamic Programming', 'Grid path DP', 'Each cell''s answer comes from its neighbours above and to the left (or by memoised DFS).'),
    (58, '2-D Dynamic Programming', 'Two-string DP (LCS family)', 'dp[i][j] compares prefixes of two strings: match the characters, or drop one from either side.'),
    (59, '2-D Dynamic Programming', 'Counting knapsack', 'Count the ways to reach a target: dp over items and amounts, adding rather than taking the best.'),
    (60, '2-D Dynamic Programming', 'State-machine DP', 'Track a small set of states per day (holding, sold, resting) and the transitions between them.'),
    (61, '2-D Dynamic Programming', 'Interval DP', 'dp[l][r] solves a range by choosing the last (or first) split point inside it.'),
    (62, 'Greedy', 'Farthest reach', 'Track the farthest index you can reach; a new jump is needed only when you pass the current limit.'),
    (63, 'Greedy', 'Greedy with running totals', 'Make the locally best choice while keeping a running balance; reset when it goes negative.'),
    (64, 'Greedy', 'Cut at the last occurrence', 'Record where each character last appears and close a part when you reach the furthest one.'),
    (65, 'Intervals', 'Sort by start and merge', 'Sort by start; an interval overlaps the previous one if it starts before the previous ends.'),
    (66, 'Intervals', 'Sort by end and keep the most', 'To keep the most non-overlapping intervals, always keep the one that ends first.'),
    (67, 'Intervals', 'Sweep line or heap of end times', 'Process start and end events in time order, tracking how many intervals are open.'),
    (68, 'Math & Geometry', 'Matrix traversal & in-place transforms', 'Move through a matrix by layers or boundaries, or transpose and reverse, without extra space.'),
    (69, 'Math & Geometry', 'Digit manipulation', 'Peel digits off with % 10 and / 10, carrying as you go, and watch for overflow.'),
    (70, 'Math & Geometry', 'Math shortcuts', 'Replace brute force with a formula or a halving trick (fast power, GCD, counting).'),
    (71, 'Math & Geometry', 'Hashing points', 'Count points in a hash map keyed by coordinates or slopes to find shapes and lines.'),
    (72, 'Bit Manipulation', 'XOR tricks', 'x ^ x = 0 and x ^ 0 = x, so XOR-ing everything cancels out the pairs.'),
    (73, 'Bit Manipulation', 'Bit counting & shifting', 'Inspect or build numbers bit by bit with &, |, >> and <<, including carries without +.');

ALTER TABLE catalog_problems ADD COLUMN pattern_id SMALLINT REFERENCES patterns (id);
UPDATE catalog_problems SET pattern_id = 1 WHERE name IN ('Contains Duplicate', 'Valid Anagram', 'Two Sum');
UPDATE catalog_problems SET pattern_id = 2 WHERE name IN ('Group Anagrams', 'Valid Sudoku');
UPDATE catalog_problems SET pattern_id = 3 WHERE name IN ('Top K Frequent Elements');
UPDATE catalog_problems SET pattern_id = 4 WHERE name IN ('Product of Array Except Self');
UPDATE catalog_problems SET pattern_id = 5 WHERE name IN ('Longest Consecutive Sequence');
UPDATE catalog_problems SET pattern_id = 6 WHERE name IN ('Encode and Decode Strings');
UPDATE catalog_problems SET pattern_id = 7 WHERE name IN ('Valid Palindrome', 'Two Sum II Input Array Is Sorted', 'Container With Most Water');
UPDATE catalog_problems SET pattern_id = 8 WHERE name IN ('3Sum');
UPDATE catalog_problems SET pattern_id = 9 WHERE name IN ('Trapping Rain Water');
UPDATE catalog_problems SET pattern_id = 11 WHERE name IN ('Longest Substring Without Repeating Characters', 'Longest Repeating Character Replacement');
UPDATE catalog_problems SET pattern_id = 12 WHERE name IN ('Permutation In String');
UPDATE catalog_problems SET pattern_id = 13 WHERE name IN ('Minimum Window Substring');
UPDATE catalog_problems SET pattern_id = 14 WHERE name IN ('Best Time to Buy And Sell Stock');
UPDATE catalog_problems SET pattern_id = 15 WHERE name IN ('Sliding Window Maximum');
UPDATE catalog_problems SET pattern_id = 16 WHERE name IN ('Valid Parentheses', 'Evaluate Reverse Polish Notation');
UPDATE catalog_problems SET pattern_id = 17 WHERE name IN ('Min Stack');
UPDATE catalog_problems SET pattern_id = 18 WHERE name IN ('Daily Temperatures', 'Car Fleet', 'Largest Rectangle In Histogram');
UPDATE catalog_problems SET pattern_id = 19 WHERE name IN ('Binary Search', 'Search a 2D Matrix', 'Time Based Key Value Store');
UPDATE catalog_problems SET pattern_id = 20 WHERE name IN ('Koko Eating Bananas');
UPDATE catalog_problems SET pattern_id = 21 WHERE name IN ('Find Minimum In Rotated Sorted Array', 'Search In Rotated Sorted Array');
UPDATE catalog_problems SET pattern_id = 22 WHERE name IN ('Median of Two Sorted Arrays');
UPDATE catalog_problems SET pattern_id = 23 WHERE name IN ('Reverse Linked List', 'Reverse Nodes In K Group', 'Reorder List');
UPDATE catalog_problems SET pattern_id = 24 WHERE name IN ('Linked List Cycle', 'Find The Duplicate Number', 'Remove Nth Node From End of List');
UPDATE catalog_problems SET pattern_id = 25 WHERE name IN ('Merge Two Sorted Lists', 'Add Two Numbers', 'Merge K Sorted Lists');
UPDATE catalog_problems SET pattern_id = 26 WHERE name IN ('LRU Cache', 'Copy List With Random Pointer');
UPDATE catalog_problems SET pattern_id = 27 WHERE name IN ('Invert Binary Tree', 'Maximum Depth of Binary Tree', 'Diameter of Binary Tree', 'Balanced Binary Tree', 'Binary Tree Maximum Path Sum');
UPDATE catalog_problems SET pattern_id = 28 WHERE name IN ('Same Tree', 'Subtree of Another Tree');
UPDATE catalog_problems SET pattern_id = 29 WHERE name IN ('Binary Tree Level Order Traversal', 'Binary Tree Right Side View');
UPDATE catalog_problems SET pattern_id = 30 WHERE name IN ('Lowest Common Ancestor of a Binary Search Tree', 'Validate Binary Search Tree', 'Kth Smallest Element In a Bst');
UPDATE catalog_problems SET pattern_id = 31 WHERE name IN ('Count Good Nodes In Binary Tree');
UPDATE catalog_problems SET pattern_id = 32 WHERE name IN ('Construct Binary Tree From Preorder And Inorder Traversal', 'Serialize And Deserialize Binary Tree');
UPDATE catalog_problems SET pattern_id = 33 WHERE name IN ('Kth Largest Element In a Stream', 'K Closest Points to Origin', 'Kth Largest Element In An Array');
UPDATE catalog_problems SET pattern_id = 34 WHERE name IN ('Last Stone Weight', 'Task Scheduler');
UPDATE catalog_problems SET pattern_id = 35 WHERE name IN ('Design Twitter');
UPDATE catalog_problems SET pattern_id = 36 WHERE name IN ('Find Median From Data Stream');
UPDATE catalog_problems SET pattern_id = 37 WHERE name IN ('Subsets', 'Combination Sum', 'Combination Sum II', 'Subsets II', 'Generate Parentheses', 'Letter Combinations of a Phone Number');
UPDATE catalog_problems SET pattern_id = 38 WHERE name IN ('Permutations');
UPDATE catalog_problems SET pattern_id = 39 WHERE name IN ('Palindrome Partitioning');
UPDATE catalog_problems SET pattern_id = 40 WHERE name IN ('Word Search', 'N Queens');
UPDATE catalog_problems SET pattern_id = 41 WHERE name IN ('Implement Trie Prefix Tree', 'Design Add And Search Words Data Structure', 'Word Search II');
UPDATE catalog_problems SET pattern_id = 42 WHERE name IN ('Number of Islands', 'Max Area of Island', 'Surrounded Regions', 'Pacific Atlantic Water Flow');
UPDATE catalog_problems SET pattern_id = 43 WHERE name IN ('Rotting Oranges', 'Walls And Gates', 'Word Ladder');
UPDATE catalog_problems SET pattern_id = 44 WHERE name IN ('Course Schedule', 'Course Schedule II', 'Alien Dictionary');
UPDATE catalog_problems SET pattern_id = 45 WHERE name IN ('Number of Connected Components In An Undirected Graph', 'Graph Valid Tree', 'Redundant Connection');
UPDATE catalog_problems SET pattern_id = 46 WHERE name IN ('Clone Graph');
UPDATE catalog_problems SET pattern_id = 47 WHERE name IN ('Network Delay Time', 'Swim In Rising Water');
UPDATE catalog_problems SET pattern_id = 48 WHERE name IN ('Min Cost to Connect All Points');
UPDATE catalog_problems SET pattern_id = 49 WHERE name IN ('Cheapest Flights Within K Stops');
UPDATE catalog_problems SET pattern_id = 50 WHERE name IN ('Reconstruct Itinerary');
UPDATE catalog_problems SET pattern_id = 51 WHERE name IN ('Climbing Stairs', 'Min Cost Climbing Stairs', 'House Robber', 'House Robber II', 'Decode Ways');
UPDATE catalog_problems SET pattern_id = 52 WHERE name IN ('Coin Change', 'Word Break');
UPDATE catalog_problems SET pattern_id = 53 WHERE name IN ('Partition Equal Subset Sum');
UPDATE catalog_problems SET pattern_id = 54 WHERE name IN ('Longest Increasing Subsequence');
UPDATE catalog_problems SET pattern_id = 55 WHERE name IN ('Longest Palindromic Substring', 'Palindromic Substrings');
UPDATE catalog_problems SET pattern_id = 56 WHERE name IN ('Maximum Product Subarray', 'Maximum Subarray');
UPDATE catalog_problems SET pattern_id = 57 WHERE name IN ('Unique Paths', 'Longest Increasing Path In a Matrix');
UPDATE catalog_problems SET pattern_id = 58 WHERE name IN ('Longest Common Subsequence', 'Interleaving String', 'Distinct Subsequences', 'Edit Distance', 'Regular Expression Matching');
UPDATE catalog_problems SET pattern_id = 59 WHERE name IN ('Coin Change II', 'Target Sum');
UPDATE catalog_problems SET pattern_id = 60 WHERE name IN ('Best Time to Buy And Sell Stock With Cooldown');
UPDATE catalog_problems SET pattern_id = 61 WHERE name IN ('Burst Balloons');
UPDATE catalog_problems SET pattern_id = 62 WHERE name IN ('Jump Game', 'Jump Game II');
UPDATE catalog_problems SET pattern_id = 63 WHERE name IN ('Gas Station', 'Valid Parenthesis String', 'Merge Triplets to Form Target Triplet', 'Hand of Straights');
UPDATE catalog_problems SET pattern_id = 64 WHERE name IN ('Partition Labels');
UPDATE catalog_problems SET pattern_id = 65 WHERE name IN ('Insert Interval', 'Merge Intervals', 'Meeting Rooms');
UPDATE catalog_problems SET pattern_id = 66 WHERE name IN ('Non Overlapping Intervals');
UPDATE catalog_problems SET pattern_id = 67 WHERE name IN ('Meeting Rooms II', 'Minimum Interval to Include Each Query');
UPDATE catalog_problems SET pattern_id = 68 WHERE name IN ('Rotate Image', 'Spiral Matrix', 'Set Matrix Zeroes');
UPDATE catalog_problems SET pattern_id = 69 WHERE name IN ('Happy Number', 'Plus One', 'Multiply Strings', 'Reverse Integer');
UPDATE catalog_problems SET pattern_id = 70 WHERE name IN ('Pow(x, n)');
UPDATE catalog_problems SET pattern_id = 71 WHERE name IN ('Detect Squares');
UPDATE catalog_problems SET pattern_id = 72 WHERE name IN ('Single Number', 'Missing Number');
UPDATE catalog_problems SET pattern_id = 73 WHERE name IN ('Number of 1 Bits', 'Counting Bits', 'Reverse Bits', 'Sum of Two Integers');
ALTER TABLE catalog_problems ALTER COLUMN pattern_id SET NOT NULL;

CREATE TABLE practice_problems (
    id              SMALLINT     PRIMARY KEY,
    pattern_id      SMALLINT     NOT NULL REFERENCES patterns (id),
    name            VARCHAR(200) NOT NULL UNIQUE,
    leetcode_number INT,
    difficulty      VARCHAR(6)   NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    url             VARCHAR(500) NOT NULL
);
CREATE INDEX practice_problems_pattern_idx ON practice_problems (pattern_id);

INSERT INTO practice_problems (id, pattern_id, name, leetcode_number, difficulty, url) VALUES
    (1, 1, 'Isomorphic Strings', 205, 'EASY', 'https://leetcode.com/problems/isomorphic-strings/'),
    (2, 1, 'Word Pattern', 290, 'EASY', 'https://leetcode.com/problems/word-pattern/'),
    (3, 1, 'Maximum Number of Balloons', 1189, 'EASY', 'https://leetcode.com/problems/maximum-number-of-balloons/'),
    (4, 1, 'Majority Element', 169, 'EASY', 'https://leetcode.com/problems/majority-element/'),
    (5, 1, 'Find the Difference of Two Arrays', 2215, 'EASY', 'https://leetcode.com/problems/find-the-difference-of-two-arrays/'),
    (6, 1, 'Number of Pairs of Interchangeable Rectangles', 2001, 'MEDIUM', 'https://leetcode.com/problems/number-of-pairs-of-interchangeable-rectangles/'),
    (7, 2, 'Unique Email Addresses', 929, 'EASY', 'https://leetcode.com/problems/unique-email-addresses/'),
    (8, 2, 'Repeated DNA Sequences', 187, 'MEDIUM', 'https://leetcode.com/problems/repeated-dna-sequences/'),
    (9, 2, 'Brick Wall', 554, 'MEDIUM', 'https://leetcode.com/problems/brick-wall/'),
    (10, 2, 'Naming a Company', 2306, 'HARD', 'https://leetcode.com/problems/naming-a-company/'),
    (11, 3, 'Sort Colors', 75, 'MEDIUM', 'https://leetcode.com/problems/sort-colors/'),
    (12, 3, 'Sort an Array', 912, 'MEDIUM', 'https://leetcode.com/problems/sort-an-array/'),
    (13, 3, 'Largest Number', 179, 'MEDIUM', 'https://leetcode.com/problems/largest-number/'),
    (14, 4, 'Find Pivot Index', 724, 'EASY', 'https://leetcode.com/problems/find-pivot-index/'),
    (15, 4, 'Range Sum Query - Immutable', 303, 'EASY', 'https://leetcode.com/problems/range-sum-query-immutable/'),
    (16, 4, 'Range Sum Query 2D Immutable', 304, 'MEDIUM', 'https://leetcode.com/problems/range-sum-query-2d-immutable/'),
    (17, 4, 'Subarray Sum Equals K', 560, 'MEDIUM', 'https://leetcode.com/problems/subarray-sum-equals-k/'),
    (18, 4, 'Continuous Subarray Sum', 523, 'MEDIUM', 'https://leetcode.com/problems/continuous-subarray-sum/'),
    (19, 4, 'Grid Game', 2017, 'MEDIUM', 'https://leetcode.com/problems/grid-game/'),
    (20, 4, 'Minimum Penalty for a Shop', 2483, 'MEDIUM', 'https://leetcode.com/problems/minimum-penalty-for-a-shop/'),
    (21, 4, 'Replace Elements With Greatest Element On Right Side', 1299, 'EASY', 'https://leetcode.com/problems/replace-elements-with-greatest-element-on-right-side/'),
    (22, 5, 'Find All Numbers Disappeared in An Array', 448, 'EASY', 'https://leetcode.com/problems/find-all-numbers-disappeared-in-an-array/'),
    (23, 5, 'First Missing Positive', 41, 'HARD', 'https://leetcode.com/problems/first-missing-positive/'),
    (24, 5, 'Check if a String Contains all Binary Codes of Size K', 1461, 'MEDIUM', 'https://leetcode.com/problems/check-if-a-string-contains-all-binary-codes-of-size-k/'),
    (25, 6, 'Encode and Decode TinyURL', 535, 'MEDIUM', 'https://leetcode.com/problems/encode-and-decode-tinyurl/'),
    (26, 6, 'Design HashMap', 706, 'EASY', 'https://leetcode.com/problems/design-hashmap/'),
    (27, 6, 'Design HashSet', 705, 'EASY', 'https://leetcode.com/problems/design-hashset/'),
    (28, 6, 'Insert Delete Get Random O(1)', 380, 'MEDIUM', 'https://leetcode.com/problems/insert-delete-getrandom-o1/'),
    (29, 6, 'Design Underground System', 1396, 'MEDIUM', 'https://leetcode.com/problems/design-underground-system/'),
    (30, 7, 'Valid Palindrome II', 680, 'EASY', 'https://leetcode.com/problems/valid-palindrome-ii/'),
    (31, 7, 'Reverse String', 344, 'EASY', 'https://leetcode.com/problems/reverse-string/'),
    (32, 7, 'Boats to Save People', 881, 'MEDIUM', 'https://leetcode.com/problems/boats-to-save-people/'),
    (33, 7, 'Squares of a Sorted Array', 977, 'EASY', 'https://leetcode.com/problems/squares-of-a-sorted-array/'),
    (34, 7, 'Merge Sorted Array', 88, 'EASY', 'https://leetcode.com/problems/merge-sorted-array/'),
    (35, 8, '4Sum', 18, 'MEDIUM', 'https://leetcode.com/problems/4sum/'),
    (36, 8, 'Number of Subsequences That Satisfy The Given Sum Condition', 1498, 'MEDIUM', 'https://leetcode.com/problems/number-of-subsequences-that-satisfy-the-given-sum-condition/'),
    (37, 8, 'Minimum Difference Between Highest And Lowest of K Scores', 1984, 'EASY', 'https://leetcode.com/problems/minimum-difference-between-highest-and-lowest-of-k-scores/'),
    (38, 9, 'Candy', 135, 'HARD', 'https://leetcode.com/problems/candy/'),
    (39, 10, 'Move Zeroes', 283, 'EASY', 'https://leetcode.com/problems/move-zeroes/'),
    (40, 10, 'Remove Duplicates From Sorted Array', 26, 'EASY', 'https://leetcode.com/problems/remove-duplicates-from-sorted-array/'),
    (41, 10, 'Remove Duplicates From Sorted Array II', 80, 'MEDIUM', 'https://leetcode.com/problems/remove-duplicates-from-sorted-array-ii/'),
    (42, 10, 'Remove Element', 27, 'EASY', 'https://leetcode.com/problems/remove-element/'),
    (43, 11, 'Fruits into Basket', 904, 'MEDIUM', 'https://leetcode.com/problems/fruit-into-baskets/'),
    (44, 11, 'Frequency of The Most Frequent Element', 1838, 'MEDIUM', 'https://leetcode.com/problems/frequency-of-the-most-frequent-element/'),
    (45, 11, 'Contains Duplicate II', 219, 'EASY', 'https://leetcode.com/problems/contains-duplicate-ii/'),
    (46, 12, 'Find All Anagrams in a String', 438, 'MEDIUM', 'https://leetcode.com/problems/find-all-anagrams-in-a-string/'),
    (47, 12, 'Maximum Number of Vowels in a Substring of Given Length', 1456, 'MEDIUM', 'https://leetcode.com/problems/maximum-number-of-vowels-in-a-substring-of-given-length/'),
    (48, 12, 'Number of Sub Arrays of Size K and Avg Greater than or Equal to Threshold', 1343, 'MEDIUM', 'https://leetcode.com/problems/number-of-sub-arrays-of-size-k-and-average-greater-than-or-equal-to-threshold/'),
    (49, 12, 'Minimum Number of Flips to Make The Binary String Alternating', 1888, 'MEDIUM', 'https://leetcode.com/problems/minimum-number-of-flips-to-make-the-binary-string-alternating/'),
    (50, 12, 'Maximum Points You Can Obtain From Cards', 1423, 'MEDIUM', 'https://leetcode.com/problems/maximum-points-you-can-obtain-from-cards/'),
    (51, 13, 'Minimum Size Subarray Sum', 209, 'MEDIUM', 'https://leetcode.com/problems/minimum-size-subarray-sum/'),
    (52, 13, 'Minimum Operations to Reduce X to Zero', 1658, 'MEDIUM', 'https://leetcode.com/problems/minimum-operations-to-reduce-x-to-zero/'),
    (53, 14, 'Best Time to Buy And Sell Stock II', 122, 'MEDIUM', 'https://leetcode.com/problems/best-time-to-buy-and-sell-stock-ii/'),
    (54, 15, 'Jump Game VII', 1871, 'MEDIUM', 'https://leetcode.com/problems/jump-game-vii/'),
    (55, 16, 'Baseball Game', 682, 'EASY', 'https://leetcode.com/problems/baseball-game/'),
    (56, 16, 'Removing Stars From a String', 2390, 'MEDIUM', 'https://leetcode.com/problems/removing-stars-from-a-string/'),
    (57, 16, 'Simplify Path', 71, 'MEDIUM', 'https://leetcode.com/problems/simplify-path/'),
    (58, 16, 'Decode String', 394, 'MEDIUM', 'https://leetcode.com/problems/decode-string/'),
    (59, 16, 'Validate Stack Sequences', 946, 'MEDIUM', 'https://leetcode.com/problems/validate-stack-sequences/'),
    (60, 16, 'Remove All Adjacent Duplicates In String II', 1209, 'MEDIUM', 'https://leetcode.com/problems/remove-all-adjacent-duplicates-in-string-ii/'),
    (61, 16, 'Asteroid Collision', 735, 'MEDIUM', 'https://leetcode.com/problems/asteroid-collision/'),
    (62, 17, 'Implement Stack Using Queues', 225, 'EASY', 'https://leetcode.com/problems/implement-stack-using-queues/'),
    (63, 17, 'Maximum Frequency Stack', 895, 'HARD', 'https://leetcode.com/problems/maximum-frequency-stack/'),
    (64, 18, 'Online Stock Span', 901, 'MEDIUM', 'https://leetcode.com/problems/online-stock-span/'),
    (65, 18, 'Next Greater Element I', 496, 'EASY', 'https://leetcode.com/problems/next-greater-element-i/'),
    (66, 18, 'Remove K Digits', 402, 'MEDIUM', 'https://leetcode.com/problems/remove-k-digits/'),
    (67, 18, '132 Pattern', 456, 'MEDIUM', 'https://leetcode.com/problems/132-pattern/'),
    (68, 18, 'Maximum Subarray Min Product', 1856, 'MEDIUM', 'https://leetcode.com/problems/maximum-subarray-min-product/'),
    (69, 19, 'Search Insert Position', 35, 'EASY', 'https://leetcode.com/problems/search-insert-position/'),
    (70, 19, 'Guess Number Higher Or Lower', 374, 'EASY', 'https://leetcode.com/problems/guess-number-higher-or-lower/'),
    (71, 19, 'Find First And Last Position of Element In Sorted Array', 34, 'MEDIUM', 'https://leetcode.com/problems/find-first-and-last-position-of-element-in-sorted-array/'),
    (72, 19, 'Successful Pairs of Spells and Potions', 2300, 'MEDIUM', 'https://leetcode.com/problems/successful-pairs-of-spells-and-potions/'),
    (73, 20, 'Capacity to Ship Packages', 1011, 'MEDIUM', 'https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/'),
    (74, 20, 'Split Array Largest Sum', 410, 'HARD', 'https://leetcode.com/problems/split-array-largest-sum/'),
    (75, 20, 'Minimize the Maximum Difference of Pairs', 2616, 'MEDIUM', 'https://leetcode.com/problems/minimize-the-maximum-difference-of-pairs/'),
    (76, 20, 'Arranging Coins', 441, 'EASY', 'https://leetcode.com/problems/arranging-coins/'),
    (77, 20, 'Sqrt(x)', 69, 'EASY', 'https://leetcode.com/problems/sqrtx/'),
    (78, 20, 'Valid Perfect Square', 367, 'EASY', 'https://leetcode.com/problems/valid-perfect-square/'),
    (79, 20, 'Maximum Number of Removable Characters', 1898, 'MEDIUM', 'https://leetcode.com/problems/maximum-number-of-removable-characters/'),
    (80, 20, 'Minimize Maximum of Array', 2439, 'MEDIUM', 'https://leetcode.com/problems/minimize-maximum-of-array/'),
    (81, 21, 'Search In Rotated Sorted Array II', 81, 'MEDIUM', 'https://leetcode.com/problems/search-in-rotated-sorted-array-ii/'),
    (82, 21, 'Find Peak Element', 162, 'MEDIUM', 'https://leetcode.com/problems/find-peak-element/'),
    (83, 21, 'Single Element in a Sorted Array', 540, 'MEDIUM', 'https://leetcode.com/problems/single-element-in-a-sorted-array/'),
    (84, 23, 'Reverse Linked List II', 92, 'MEDIUM', 'https://leetcode.com/problems/reverse-linked-list-ii/'),
    (85, 23, 'Swap Nodes In Pairs', 24, 'MEDIUM', 'https://leetcode.com/problems/swap-nodes-in-pairs/'),
    (86, 23, 'Palindrome Linked List', 234, 'EASY', 'https://leetcode.com/problems/palindrome-linked-list/'),
    (87, 23, 'Maximum Twin Sum Of A Linked List', 2130, 'MEDIUM', 'https://leetcode.com/problems/maximum-twin-sum-of-a-linked-list/'),
    (88, 24, 'Middle of the Linked List', 876, 'EASY', 'https://leetcode.com/problems/middle-of-the-linked-list/'),
    (89, 24, 'Swapping Nodes in a Linked List', 1721, 'MEDIUM', 'https://leetcode.com/problems/swapping-nodes-in-a-linked-list/'),
    (90, 24, 'Intersection of Two Linked Lists', 160, 'EASY', 'https://leetcode.com/problems/intersection-of-two-linked-lists/'),
    (91, 24, 'Rotate List', 61, 'MEDIUM', 'https://leetcode.com/problems/rotate-list/'),
    (92, 25, 'Remove Linked List Elements', 203, 'EASY', 'https://leetcode.com/problems/remove-linked-list-elements/'),
    (93, 25, 'Remove Duplicates From Sorted List', 83, 'EASY', 'https://leetcode.com/problems/remove-duplicates-from-sorted-list/'),
    (94, 25, 'Partition List', 86, 'MEDIUM', 'https://leetcode.com/problems/partition-list/'),
    (95, 25, 'Sort List', 148, 'MEDIUM', 'https://leetcode.com/problems/sort-list/'),
    (96, 25, 'Insertion Sort List', 147, 'MEDIUM', 'https://leetcode.com/problems/insertion-sort-list/'),
    (97, 25, 'Split Linked List in Parts', 725, 'MEDIUM', 'https://leetcode.com/problems/split-linked-list-in-parts/'),
    (98, 25, 'Add to Array-Form of Integer', 989, 'EASY', 'https://leetcode.com/problems/add-to-array-form-of-integer/'),
    (99, 26, 'LFU Cache', 460, 'HARD', 'https://leetcode.com/problems/lfu-cache/'),
    (100, 26, 'Design Linked List', 707, 'MEDIUM', 'https://leetcode.com/problems/design-linked-list/'),
    (101, 26, 'Design Circular Queue', 622, 'MEDIUM', 'https://leetcode.com/problems/design-circular-queue/'),
    (102, 26, 'Design Browser History', 1472, 'MEDIUM', 'https://leetcode.com/problems/design-browser-history/'),
    (103, 27, 'Merge Two Binary Trees', 617, 'EASY', 'https://leetcode.com/problems/merge-two-binary-trees/'),
    (104, 27, 'House Robber III', 337, 'MEDIUM', 'https://leetcode.com/problems/house-robber-iii/'),
    (105, 27, 'Minimum Time to Collect All Apples in a Tree', 1443, 'MEDIUM', 'https://leetcode.com/problems/minimum-time-to-collect-all-apples-in-a-tree/'),
    (106, 27, 'Time Needed to Inform All Employees', 1376, 'MEDIUM', 'https://leetcode.com/problems/time-needed-to-inform-all-employees/'),
    (107, 28, 'Symmetric Tree', 101, 'EASY', 'https://leetcode.com/problems/symmetric-tree/'),
    (108, 28, 'Flip Equivalent Binary Trees', 951, 'MEDIUM', 'https://leetcode.com/problems/flip-equivalent-binary-trees/'),
    (109, 28, 'Find Duplicate Subtrees', 652, 'MEDIUM', 'https://leetcode.com/problems/find-duplicate-subtrees/'),
    (110, 29, 'Binary Tree Zigzag Level Order Traversal', 103, 'MEDIUM', 'https://leetcode.com/problems/binary-tree-zigzag-level-order-traversal/'),
    (111, 29, 'Maximum Width of Binary Tree', 662, 'MEDIUM', 'https://leetcode.com/problems/maximum-width-of-binary-tree/'),
    (112, 29, 'Find Bottom Left Tree Value', 513, 'MEDIUM', 'https://leetcode.com/problems/find-bottom-left-tree-value/'),
    (113, 29, 'Check Completeness of a Binary Tree', 958, 'MEDIUM', 'https://leetcode.com/problems/check-completeness-of-a-binary-tree/'),
    (114, 29, 'Populating Next Right Pointers In Each Node', 116, 'MEDIUM', 'https://leetcode.com/problems/populating-next-right-pointers-in-each-node/'),
    (115, 30, 'Insert into a Binary Search Tree', 701, 'MEDIUM', 'https://leetcode.com/problems/insert-into-a-binary-search-tree/'),
    (116, 30, 'Delete Node in a BST', 450, 'MEDIUM', 'https://leetcode.com/problems/delete-node-in-a-bst/'),
    (117, 30, 'Minimum Distance between BST Nodes', 783, 'EASY', 'https://leetcode.com/problems/minimum-distance-between-bst-nodes/'),
    (118, 30, 'Trim a Binary Search Tree', 669, 'MEDIUM', 'https://leetcode.com/problems/trim-a-binary-search-tree/'),
    (119, 30, 'Binary Search Tree Iterator', 173, 'MEDIUM', 'https://leetcode.com/problems/binary-search-tree-iterator/'),
    (120, 30, 'Convert Bst to Greater Tree', 538, 'MEDIUM', 'https://leetcode.com/problems/convert-bst-to-greater-tree/'),
    (121, 30, 'Convert Sorted Array to Binary Search Tree', 108, 'EASY', 'https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/'),
    (122, 31, 'Path Sum', 112, 'EASY', 'https://leetcode.com/problems/path-sum/'),
    (123, 31, 'Sum Root to Leaf Numbers', 129, 'MEDIUM', 'https://leetcode.com/problems/sum-root-to-leaf-numbers/'),
    (124, 32, 'Construct Binary Tree from Inorder and Postorder Traversal', 106, 'MEDIUM', 'https://leetcode.com/problems/construct-binary-tree-from-inorder-and-postorder-traversal/'),
    (125, 32, 'Construct String From Binary Tree', 606, 'EASY', 'https://leetcode.com/problems/construct-string-from-binary-tree/'),
    (126, 32, 'Construct Quad Tree', 427, 'MEDIUM', 'https://leetcode.com/problems/construct-quad-tree/'),
    (127, 32, 'All Possible Full Binary Trees', 894, 'MEDIUM', 'https://leetcode.com/problems/all-possible-full-binary-trees/'),
    (128, 32, 'Unique Binary Search Trees II', 95, 'MEDIUM', 'https://leetcode.com/problems/unique-binary-search-trees-ii/'),
    (129, 33, 'Find The Kth Largest Integer In The Array', 1985, 'MEDIUM', 'https://leetcode.com/problems/find-the-kth-largest-integer-in-the-array/'),
    (130, 33, 'Maximum Subsequence Score', 2542, 'MEDIUM', 'https://leetcode.com/problems/maximum-subsequence-score/'),
    (131, 33, 'Maximum Performance of a Team', 1383, 'HARD', 'https://leetcode.com/problems/maximum-performance-of-a-team/'),
    (132, 34, 'Reorganize String', 767, 'MEDIUM', 'https://leetcode.com/problems/reorganize-string/'),
    (133, 34, 'Longest Happy String', 1405, 'MEDIUM', 'https://leetcode.com/problems/longest-happy-string/'),
    (134, 34, 'Minimize Deviation in Array', 1675, 'HARD', 'https://leetcode.com/problems/minimize-deviation-in-array/'),
    (135, 34, 'Seat Reservation Manager', 1845, 'MEDIUM', 'https://leetcode.com/problems/seat-reservation-manager/'),
    (136, 35, 'Single Threaded Cpu', 1834, 'MEDIUM', 'https://leetcode.com/problems/single-threaded-cpu/'),
    (137, 35, 'Process Tasks Using Servers', 1882, 'MEDIUM', 'https://leetcode.com/problems/process-tasks-using-servers/'),
    (138, 36, 'IPO', 502, 'HARD', 'https://leetcode.com/problems/ipo/'),
    (139, 37, 'Combinations', 77, 'MEDIUM', 'https://leetcode.com/problems/combinations/'),
    (140, 37, 'Matchsticks to Square', 473, 'MEDIUM', 'https://leetcode.com/problems/matchsticks-to-square/'),
    (141, 37, 'Partition to K Equal Sum Subsets', 698, 'MEDIUM', 'https://leetcode.com/problems/partition-to-k-equal-sum-subsets/'),
    (142, 37, 'Maximum Length of a Concatenated String With Unique Characters', 1239, 'MEDIUM', 'https://leetcode.com/problems/maximum-length-of-a-concatenated-string-with-unique-characters/'),
    (143, 37, 'Find Unique Binary String', 1980, 'MEDIUM', 'https://leetcode.com/problems/find-unique-binary-string/'),
    (144, 38, 'Permutations II', 47, 'MEDIUM', 'https://leetcode.com/problems/permutations-ii/'),
    (145, 39, 'Restore IP Addresses', 93, 'MEDIUM', 'https://leetcode.com/problems/restore-ip-addresses/'),
    (146, 39, 'Splitting a String Into Descending Consecutive Values', 1849, 'MEDIUM', 'https://leetcode.com/problems/splitting-a-string-into-descending-consecutive-values/'),
    (147, 40, 'N Queens II', 52, 'HARD', 'https://leetcode.com/problems/n-queens-ii/'),
    (148, 41, 'Extra Characters in a String', 2707, 'MEDIUM', 'https://leetcode.com/problems/extra-characters-in-a-string/'),
    (149, 41, 'Search Suggestions System', 1268, 'MEDIUM', 'https://leetcode.com/problems/search-suggestions-system/'),
    (150, 41, 'Concatenated Words', 472, 'HARD', 'https://leetcode.com/problems/concatenated-words/'),
    (151, 42, 'Island Perimeter', 463, 'EASY', 'https://leetcode.com/problems/island-perimeter/'),
    (152, 42, 'Count Sub Islands', 1905, 'MEDIUM', 'https://leetcode.com/problems/count-sub-islands/'),
    (153, 42, 'Number of Closed Islands', 1254, 'MEDIUM', 'https://leetcode.com/problems/number-of-closed-islands/'),
    (154, 42, 'Number of Enclaves', 1020, 'MEDIUM', 'https://leetcode.com/problems/number-of-enclaves/'),
    (155, 43, 'As Far from Land as Possible', 1162, 'MEDIUM', 'https://leetcode.com/problems/as-far-from-land-as-possible/'),
    (156, 43, 'Shortest Path in Binary Matrix', 1091, 'MEDIUM', 'https://leetcode.com/problems/shortest-path-in-binary-matrix/'),
    (157, 43, 'Open The Lock', 752, 'MEDIUM', 'https://leetcode.com/problems/open-the-lock/'),
    (158, 43, 'Snakes And Ladders', 909, 'MEDIUM', 'https://leetcode.com/problems/snakes-and-ladders/'),
    (159, 43, 'Shortest Bridge', 934, 'MEDIUM', 'https://leetcode.com/problems/shortest-bridge/'),
    (160, 43, 'Shortest Path with Alternating Colors', 1129, 'MEDIUM', 'https://leetcode.com/problems/shortest-path-with-alternating-colors/'),
    (161, 44, 'Course Schedule IV', 1462, 'MEDIUM', 'https://leetcode.com/problems/course-schedule-iv/'),
    (162, 44, 'Find Eventual Safe States', 802, 'MEDIUM', 'https://leetcode.com/problems/find-eventual-safe-states/'),
    (163, 44, 'Largest Color Value in a Directed Graph', 1857, 'HARD', 'https://leetcode.com/problems/largest-color-value-in-a-directed-graph/'),
    (164, 44, 'Minimum Number of Vertices to Reach all Nodes', 1557, 'MEDIUM', 'https://leetcode.com/problems/minimum-number-of-vertices-to-reach-all-nodes/'),
    (165, 44, 'Verifying An Alien Dictionary', 953, 'EASY', 'https://leetcode.com/problems/verifying-an-alien-dictionary/'),
    (166, 45, 'Accounts Merge', 721, 'MEDIUM', 'https://leetcode.com/problems/accounts-merge/'),
    (167, 45, 'Minimum Score of a Path Between Two Cities', 2492, 'MEDIUM', 'https://leetcode.com/problems/minimum-score-of-a-path-between-two-cities/'),
    (168, 45, 'Remove Max Number of Edges to Keep Graph Fully Traversable', 1579, 'HARD', 'https://leetcode.com/problems/remove-max-number-of-edges-to-keep-graph-fully-traversable/'),
    (169, 46, 'Evaluate Division', 399, 'MEDIUM', 'https://leetcode.com/problems/evaluate-division/'),
    (170, 46, 'Reorder Routes to Make All Paths Lead to The City Zero', 1466, 'MEDIUM', 'https://leetcode.com/problems/reorder-routes-to-make-all-paths-lead-to-the-city-zero/'),
    (171, 46, 'Minimum Fuel Cost to Report to the Capital', 2477, 'MEDIUM', 'https://leetcode.com/problems/minimum-fuel-cost-to-report-to-the-capital/'),
    (172, 46, 'Find Closest Node to Given Two Nodes', 2359, 'MEDIUM', 'https://leetcode.com/problems/find-closest-node-to-given-two-nodes/'),
    (173, 46, 'Detonate the Maximum Bombs', 2101, 'MEDIUM', 'https://leetcode.com/problems/detonate-the-maximum-bombs/'),
    (174, 46, 'Is Graph Bipartite?', 785, 'MEDIUM', 'https://leetcode.com/problems/is-graph-bipartite/'),
    (175, 47, 'Path with Minimum Effort', 1631, 'MEDIUM', 'https://leetcode.com/problems/path-with-minimum-effort/'),
    (176, 47, 'Path with Maximum Probability', 1514, 'MEDIUM', 'https://leetcode.com/problems/path-with-maximum-probability/'),
    (177, 48, 'Find Critical and Pseudo Critical Edges in Minimum Spanning Tree', 1489, 'HARD', 'https://leetcode.com/problems/find-critical-and-pseudo-critical-edges-in-minimum-spanning-tree/'),
    (178, 51, 'N-th Tribonacci Number', 1137, 'EASY', 'https://leetcode.com/problems/n-th-tribonacci-number/'),
    (179, 51, 'Delete And Earn', 740, 'MEDIUM', 'https://leetcode.com/problems/delete-and-earn/'),
    (180, 51, 'Paint House', 256, 'MEDIUM', 'https://leetcode.com/problems/paint-house/'),
    (181, 51, 'Solving Questions With Brainpower', 2140, 'MEDIUM', 'https://leetcode.com/problems/solving-questions-with-brainpower/'),
    (182, 51, 'Count Ways to Build Good Strings', 2466, 'MEDIUM', 'https://leetcode.com/problems/count-ways-to-build-good-strings/'),
    (183, 51, 'Check if There is a Valid Partition For The Array', 2369, 'MEDIUM', 'https://leetcode.com/problems/check-if-there-is-a-valid-partition-for-the-array/'),
    (184, 52, 'Perfect Squares', 279, 'MEDIUM', 'https://leetcode.com/problems/perfect-squares/'),
    (185, 52, 'Combination Sum IV', 377, 'MEDIUM', 'https://leetcode.com/problems/combination-sum-iv/'),
    (186, 52, 'Integer Break', 343, 'MEDIUM', 'https://leetcode.com/problems/integer-break/'),
    (187, 52, 'Minimum Cost For Tickets', 983, 'MEDIUM', 'https://leetcode.com/problems/minimum-cost-for-tickets/'),
    (188, 53, 'Last Stone Weight II', 1049, 'MEDIUM', 'https://leetcode.com/problems/last-stone-weight-ii/'),
    (189, 54, 'Number of Longest Increasing Subsequence', 673, 'MEDIUM', 'https://leetcode.com/problems/number-of-longest-increasing-subsequence/'),
    (190, 54, 'Best Team with no Conflicts', 1626, 'MEDIUM', 'https://leetcode.com/problems/best-team-with-no-conflicts/'),
    (191, 54, 'Find the Longest Valid Obstacle Course at Each Position', 1964, 'HARD', 'https://leetcode.com/problems/find-the-longest-valid-obstacle-course-at-each-position/'),
    (192, 55, 'Unique Length 3 Palindromic Subsequences', 1930, 'MEDIUM', 'https://leetcode.com/problems/unique-length-3-palindromic-subsequences/'),
    (193, 56, 'Maximum Sum Circular Subarray', 918, 'MEDIUM', 'https://leetcode.com/problems/maximum-sum-circular-subarray/'),
    (194, 56, 'Longest Turbulent Array', 978, 'MEDIUM', 'https://leetcode.com/problems/longest-turbulent-subarray/'),
    (195, 57, 'Unique Paths II', 63, 'MEDIUM', 'https://leetcode.com/problems/unique-paths-ii/'),
    (196, 57, 'Minimum Path Sum', 64, 'MEDIUM', 'https://leetcode.com/problems/minimum-path-sum/'),
    (197, 57, 'Maximal Square', 221, 'MEDIUM', 'https://leetcode.com/problems/maximal-square/'),
    (198, 57, 'Triangle', 120, 'MEDIUM', 'https://leetcode.com/problems/triangle/'),
    (199, 58, 'Uncrossed Lines', 1035, 'MEDIUM', 'https://leetcode.com/problems/uncrossed-lines/'),
    (200, 58, 'Longest Palindromic Subsequence', 516, 'MEDIUM', 'https://leetcode.com/problems/longest-palindromic-subsequence/'),
    (201, 58, 'Number of Ways to Form a Target String Given a Dictionary', 1639, 'HARD', 'https://leetcode.com/problems/number-of-ways-to-form-a-target-string-given-a-dictionary/'),
    (202, 59, 'Ones and Zeroes', 474, 'MEDIUM', 'https://leetcode.com/problems/ones-and-zeroes/'),
    (203, 59, 'Profitable Schemes', 879, 'HARD', 'https://leetcode.com/problems/profitable-schemes/'),
    (204, 59, 'Maximum Value of K Coins from Piles', 2218, 'HARD', 'https://leetcode.com/problems/maximum-value-of-k-coins-from-piles/'),
    (205, 60, 'Maximum Alternating Subsequence Sum', 1911, 'MEDIUM', 'https://leetcode.com/problems/maximum-alternating-subsequence-sum/'),
    (206, 60, 'Flip String to Monotone Increasing', 926, 'MEDIUM', 'https://leetcode.com/problems/flip-string-to-monotone-increasing/'),
    (207, 60, 'Count Vowels Permutation', 1220, 'HARD', 'https://leetcode.com/problems/count-vowels-permutation/'),
    (208, 61, 'Minimum Cost to Cut a Stick', 1547, 'HARD', 'https://leetcode.com/problems/minimum-cost-to-cut-a-stick/'),
    (209, 61, 'Stone Game', 877, 'MEDIUM', 'https://leetcode.com/problems/stone-game/'),
    (210, 61, 'Stone Game II', 1140, 'MEDIUM', 'https://leetcode.com/problems/stone-game-ii/'),
    (211, 63, 'Two City Scheduling', 1029, 'MEDIUM', 'https://leetcode.com/problems/two-city-scheduling/'),
    (212, 63, 'Eliminate Maximum Number of Monsters', 1921, 'MEDIUM', 'https://leetcode.com/problems/eliminate-maximum-number-of-monsters/'),
    (213, 63, 'Dota2 Senate', 649, 'MEDIUM', 'https://leetcode.com/problems/dota2-senate/'),
    (214, 63, 'Minimum Deletions to Make Character Frequencies Unique', 1647, 'MEDIUM', 'https://leetcode.com/problems/minimum-deletions-to-make-character-frequencies-unique/'),
    (215, 63, 'Can Place Flowers', 605, 'EASY', 'https://leetcode.com/problems/can-place-flowers/'),
    (216, 64, 'Optimal Partition of String', 2405, 'MEDIUM', 'https://leetcode.com/problems/optimal-partition-of-string/'),
    (217, 65, 'Remove Covered Intervals', 1288, 'MEDIUM', 'https://leetcode.com/problems/remove-covered-intervals/'),
    (218, 65, 'Data Stream as Disjoint Intervals', 352, 'HARD', 'https://leetcode.com/problems/data-stream-as-disjoint-intervals/'),
    (219, 66, 'Maximum Length of Pair Chain', 646, 'MEDIUM', 'https://leetcode.com/problems/maximum-length-of-pair-chain/'),
    (220, 67, 'Car Pooling', 1094, 'MEDIUM', 'https://leetcode.com/problems/car-pooling/'),
    (221, 68, 'Spiral Matrix II', 59, 'MEDIUM', 'https://leetcode.com/problems/spiral-matrix-ii/'),
    (222, 68, 'Matrix Diagonal Sum', 1572, 'EASY', 'https://leetcode.com/problems/matrix-diagonal-sum/'),
    (223, 68, 'Shift 2D Grid', 1260, 'EASY', 'https://leetcode.com/problems/shift-2d-grid/'),
    (224, 68, 'Zigzag Conversion', 6, 'MEDIUM', 'https://leetcode.com/problems/zigzag-conversion/'),
    (225, 69, 'Palindrome Number', 9, 'EASY', 'https://leetcode.com/problems/palindrome-number/'),
    (226, 69, 'Roman to Integer', 13, 'EASY', 'https://leetcode.com/problems/roman-to-integer/'),
    (227, 69, 'Integer to Roman', 12, 'MEDIUM', 'https://leetcode.com/problems/integer-to-roman/'),
    (228, 69, 'Excel Sheet Column Title', 168, 'EASY', 'https://leetcode.com/problems/excel-sheet-column-title/'),
    (229, 69, 'Ugly Number', 263, 'EASY', 'https://leetcode.com/problems/ugly-number/'),
    (230, 70, 'Count Odd Numbers in an Interval Range', 1523, 'EASY', 'https://leetcode.com/problems/count-odd-numbers-in-an-interval-range/'),
    (231, 70, 'Greatest Common Divisor of Strings', 1071, 'EASY', 'https://leetcode.com/problems/greatest-common-divisor-of-strings/'),
    (232, 70, 'Find Missing Observations', 2028, 'MEDIUM', 'https://leetcode.com/problems/find-missing-observations/'),
    (233, 70, 'Robot Bounded In Circle', 1041, 'MEDIUM', 'https://leetcode.com/problems/robot-bounded-in-circle/'),
    (234, 71, 'Maximum Points on a Line', 149, 'HARD', 'https://leetcode.com/problems/max-points-on-a-line/'),
    (235, 73, 'Add Binary', 67, 'EASY', 'https://leetcode.com/problems/add-binary/');

-- ---------------------------------------------------------------
-- Weekly tests: one per user per plan week (week 1 = start date + 0..6 days).
-- ---------------------------------------------------------------
CREATE TABLE weekly_tests (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week_number   INT         NOT NULL CHECK (week_number >= 1),
    week_start    DATE        NOT NULL,
    week_end      DATE        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    UNIQUE (user_id, week_number)
);

CREATE TABLE weekly_test_items (
    id                   BIGSERIAL   PRIMARY KEY,
    test_id              BIGINT      NOT NULL REFERENCES weekly_tests (id) ON DELETE CASCADE,
    position             SMALLINT    NOT NULL,
    practice_problem_id  SMALLINT    NOT NULL REFERENCES practice_problems (id),
    pattern_id           SMALLINT    NOT NULL REFERENCES patterns (id),         -- the right answer
    anchor_catalog_id    SMALLINT    NOT NULL REFERENCES catalog_problems (id), -- the problem you solved that week
    option_pattern_ids   VARCHAR(40) NOT NULL,                                  -- e.g. '7,1,12,3', in display order
    chosen_pattern_id    SMALLINT    REFERENCES patterns (id),
    outcome              VARCHAR(10) CHECK (outcome IN ('SOLVED', 'HINT', 'NOT_SOLVED')),
    answered_at          TIMESTAMPTZ,
    UNIQUE (test_id, position),
    UNIQUE (test_id, practice_problem_id),
    -- the solving step only comes after the pattern step
    CHECK (outcome IS NULL OR chosen_pattern_id IS NOT NULL)
);
CREATE INDEX weekly_test_items_test_idx ON weekly_test_items (test_id);
