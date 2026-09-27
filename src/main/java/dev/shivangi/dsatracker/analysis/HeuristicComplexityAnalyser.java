package dev.shivangi.dsatracker.analysis;

import dev.shivangi.dsatracker.analysis.CodeStructure.Kind;
import dev.shivangi.dsatracker.analysis.CodeStructure.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates time and space complexity from the structure of the code, with no outside service.
 *
 * <p>How it works:
 * <ol>
 *   <li>{@link CodeStructure} turns the code into a tree of functions, loops and blocks.</li>
 *   <li>Each loop gets a factor: O(n) for a pass over the input, O(1) for a fixed count (26
 *       letters, 4 directions), O(log n) when it halves or doubles, O(1) amortised when the
 *       inner loop of a sliding window or monotonic stack moves each element at most once.</li>
 *   <li>Nested loops multiply; siblings take the larger. Library calls add their usual cost
 *       (sort O(n log n), heap push/pop O(log n), hash map O(1)).</li>
 *   <li>Recursion is classified: tree traversal O(n), visit-once DFS O(n), divide and conquer
 *       (a recurrence), memoised (states × work), backtracking (2ⁿ or n!), or plain exponential.</li>
 *   <li>Space is the largest thing the code allocates (arrays, maps, sets, 2-D tables) or the
 *       recursion depth, not counting the answer itself.</li>
 * </ol>
 * It records each step as a reason, so the answer can be checked. It's a heuristic: it can be
 * wrong on unusual code, which is why it reports its confidence.
 */
public final class HeuristicComplexityAnalyser implements ComplexityAnalyser {

    @Override
    public ComplexityAnalysis analyse(String code, CodeLanguage language) {
        try {
            return new Run(code, language).result();
        } catch (RuntimeException e) {
            return new ComplexityAnalysis("?", "?",
                    List.of("Couldn't read the structure of this code, so no estimate. Check that it's complete and in "
                            + language.label() + "."),
                    "low", ComplexityAnalysis.ESTIMATE);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static final Pattern SORT = Pattern.compile(
            "\\b(Arrays|Collections)\\.sort\\s*\\(|\\.sort\\s*\\(|\\bsorted\\s*\\(|\\bstd::sort\\s*\\(|(^|[^.\\w])sort\\s*\\(");
    private static final Pattern PY_HEAP_OP = Pattern.compile("\\bheapq\\.(heappush|heappop|heappushpop|heapreplace)\\s*\\(");
    private static final Pattern LINEAR_CALL = Pattern.compile(
            "\\.stream\\s*\\(|\\.substring\\s*\\(|\\.indexOf\\s*\\(|\\.index\\s*\\(|\\.join\\s*\\(|\\.split\\s*\\("
                    + "|\\.toCharArray\\s*\\(|\\.includes\\s*\\(|\\.reverse\\s*\\(|\\breversed\\s*\\(|\\.copy\\s*\\("
                    + "|\\.clone\\s*\\(|Arrays\\.fill\\s*\\(|Arrays\\.copyOf\\w*\\s*\\(|\\bnew\\s+ArrayList\\s*<[^>]*>\\s*\\(\\s*\\w"
                    + "|\\bheapq\\.heapify\\s*\\(|\\b(list|set|Counter)\\s*\\(\\s*\\w|\\w\\s*\\[[^\\]\\[]*:[^\\]\\[]*\\]");
    private static final Pattern AGGREGATE = Pattern.compile("\\b(sum|max|min|any|all)\\s*\\(");
    private static final Pattern RANGE_CONST = Pattern.compile("\\bin\\s+range\\s*\\(\\s*-?\\d+\\s*(,\\s*-?\\d+\\s*)?(,\\s*-?\\d+\\s*)?\\)");
    /** A C-style for loop whose start AND end are numbers: "for (int i = 0; i < 26; i++)". */
    private static final Pattern C_CONST = Pattern.compile(
            "\\(\\s*(\\w+\\s+)?\\w+\\s*=\\s*-?\\d+\\s*;\\s*\\w+\\s*(<|<=|>|>=|!=)\\s*-?\\d+\\s*;");
    private static final Pattern DIRECTIONS = Pattern.compile(
            "(?i)(:|\\bof|\\bin)\\s*(self\\.|this\\.)?(dirs|directions|dir|neighbors4|moves|deltas|offsets)\\b"
                    + "|\\bin\\s+[\\[(]\\s*[\\[(]|\\bin\\s+[\\[(]\\s*-?\\d|\\bin\\s+[\"']|(:|\\bof)\\s*[\\[{]\\s*[\\[{]");
    private static final Pattern DOUBLING = Pattern.compile("(\\w+)\\s*(\\*=|/=|>>=|<<=|//=)\\s*\\d+");
    private static final Pattern DOUBLING2 = Pattern.compile("(\\w+)\\s*=\\s*(\\w+)\\s*(\\*|/|//|>>|<<)\\s*\\d+");
    private static final Pattern BOUNDS = Pattern.compile(
            "(?i)\\b(l|r|lo|hi|left|right|low|high|start|end|beg|begin)\\b\\s*(<=|<)\\s*\\b(l|r|lo|hi|left|right|low|high|start|end|beg|begin)\\b");
    private static final Pattern MID = Pattern.compile("\\bmid\\b|\\bm\\s*=|/\\s*2\\b|>>\\s*1\\b|>>>\\s*1\\b|//\\s*2\\b");
    private static final Pattern POP = Pattern.compile(
            "\\.(heappop|pop|poll|pollLast|pollFirst|removeLast|removeFirst|popleft|shift|pop_back|pop_front|remove)\\s*\\(");
    private static final Pattern STACKISH = Pattern.compile("(?i)\\b(\\w*stack\\w*|stk|st|\\w*deque\\w*|dq|\\w*queue\\w*|q|\\w*heap\\w*|pq|window|mono\\w*)\\b");
    private static final Pattern QUEUEISH = Pattern.compile("(?i)\\b(\\w*queue\\w*|q|dq|\\w*deque\\w*|frontier|bfs|\\w*heap\\w*|pq|minheap|maxheap)\\b");
    private static final Pattern ADJACENCY = Pattern.compile(
            "(?i)\\b(graph|adj|adjlist|adjacency|neighbors|neighbours|children|edges|nei|nbrs|prereq\\w*|g)\\s*[\\[.(]|\\b(neighbors|neighbours|children)\\b");
    private static final Pattern VISITED = Pattern.compile(
            "(?i)\\b(visited|seen|visit|vis)\\b|\\w+\\s*\\[[^\\]]+\\]\\s*\\[[^\\]]+\\]\\s*=\\s*(['\"]|-1\\b|0\\b|true\\b|false\\b|True\\b|False\\b)");
    private static final Pattern GRID = Pattern.compile(
            "\\[0\\]\\.length|len\\s*\\(\\s*\\w+\\s*\\[\\s*0\\s*\\]\\s*\\)|\\[0\\]\\.size\\s*\\(|(?i)\\b(grid|board|matrix|mat|image|heights)\\b");
    private static final Pattern TWO_D_INDEX = Pattern.compile("\\w\\s*\\[[^\\]]+\\]\\s*\\[[^\\]]+\\]");
    private static final Pattern TREE_ARG = Pattern.compile("\\.(left|right|children|next|child)\\b|\\b(child|kid)\\b");
    private static final Pattern HALVING_ARG = Pattern.compile("\\bmid\\b|/\\s*2\\b|>>\\s*1\\b|//\\s*2\\b|\\bm\\s*[,)+-]|\\bm\\b\\s*$");
    private static final Pattern MEMO_NAME = Pattern.compile("(?i)\\b(memo\\w*|cache\\w*|dp\\w*)\\b");
    private static final Pattern MEMO_CHECK = Pattern.compile(
            "containsKey\\s*\\(|\\.get\\s*\\(|\\.has\\s*\\(|\\bin\\s+(self\\.)?(memo|cache|dp)\\w*\\b|\\.count\\s*\\(|\\.find\\s*\\(|!=\\s*-1\\b|!=\\s*null\\b|!==?\\s*undefined|is\\s+not\\s+None|\\bmemo\\w*\\s*\\[");
    private static final Pattern PERMUTATION = Pattern.compile("(?i)\\b(used|visited|seen|taken|swap)\\b|\\.contains\\s*\\(|\\bnot\\s+in\\b|\\bin\\s+(path|curr?|perm)\\b");
    private static final Pattern PATH_COPY = Pattern.compile(
            "new\\s+ArrayList\\s*<[^>]*>\\s*\\(\\s*\\w|\\[\\s*:\\s*\\]|\\.copy\\s*\\(|\\blist\\s*\\(\\s*\\w|\\.slice\\s*\\(|\\[\\s*\\.\\.\\.|\\+\\s*\\[");
    private static final Pattern INDEX_PARAM = Pattern.compile(
            "(?i)^(i|j|k|idx|index|start|end|left|right|l|r|lo|hi|pos|n|m|amount|target|remain|rem|remaining|sum|total|row|col|"
                    + "c|x|y|mask|count|cap|capacity|w|day|days|steps|step|buy|holding|prev|last|a|b|i1|i2|p1|p2|curr|cur)$");

    private static final Pattern OUTPUT_VAR = Pattern.compile("(?i)^\\s*([\\w<>\\[\\],.\\s]*?\\s)?(res|result|results|ans|answer|answers|output|out|ret)\\s*=");
    private static final Pattern NEW_2D = Pattern.compile("new\\s+\\w+\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]");
    private static final Pattern NEW_1D = Pattern.compile("new\\s+\\w+\\s*\\[([^\\]]+)\\](?!\\s*\\[)");
    private static final Pattern PY_2D = Pattern.compile("\\[\\s*\\[[^\\]]*\\]\\s*\\*\\s*\\(?([^\\]\\n]+?)\\)?\\s+for\\s+\\w+\\s+in\\s+range\\s*\\(([^)]*)\\)\\s*\\]");
    private static final Pattern PY_1D = Pattern.compile("\\[[^\\[\\]\\n]*\\]\\s*\\*\\s*\\(?([\\w.+\\-\\s()]+?)\\)?\\s*(\\n|$|#|,|\\))");
    private static final Pattern CPP_2D = Pattern.compile("vector\\s*<\\s*vector\\s*<[^;]*?\\(\\s*([^,()]+)\\s*,\\s*vector\\s*<[^(;]*\\(\\s*([^,()]+)");
    private static final Pattern CPP_1D = Pattern.compile("vector\\s*<[^<>]*>\\s+\\w+\\s*\\(\\s*([^,()]+)");
    private static final Pattern JS_1D = Pattern.compile("new\\s+Array\\s*\\(\\s*([^)]+)\\)|\\bArray\\s*\\(\\s*([^)]+)\\)\\s*\\.fill");
    private static final Pattern JS_2D = Pattern.compile("Array\\.from\\s*\\(\\s*\\{\\s*length\\s*:\\s*([^}]+)\\}\\s*,\\s*\\(\\)\\s*=>\\s*(new\\s+)?Array\\s*\\(([^)]+)\\)");
    private static final Pattern COLLECTION = Pattern.compile(
            "new\\s+(HashMap|HashSet|ArrayList|LinkedList|ArrayDeque|Stack|TreeMap|TreeSet|PriorityQueue|LinkedHashMap|LinkedHashSet|Map|Set)\\b"
                    + "|\\b(dict|set|defaultdict|Counter|deque|OrderedDict)\\s*\\(|=\\s*\\[\\s*\\]|=\\s*\\{\\s*\\}"
                    + "|\\b(unordered_map|unordered_set|map|set|vector|stack|queue|deque|priority_queue|multiset)\\s*<[^;()&]*>\\s+\\w+\\s*(;|\\{|=)"
                    + "|\\bStringBuilder\\b|\\bsorted\\s*\\(|\\.toCharArray\\s*\\(|\\.split\\s*\\(");
    private static final Pattern NUMBER = Pattern.compile("\\s*\\d+\\s*");
    private static final Pattern HEAP_DECL = Pattern.compile(
            "PriorityQueue\\s*<[^>]*>\\s+(\\w+)|(\\w+)\\s*=\\s*new\\s+PriorityQueue|priority_queue\\s*<[^;]*>\\s+(\\w+)|(TreeMap|TreeSet)\\s*<[^>]*>\\s+(\\w+)|(\\w+)\\s*=\\s*new\\s+(TreeMap|TreeSet)");

    private static final Pattern PY_FOR = Pattern.compile("^for\\s+(.+?)\\s+in\\s+(.+)$");
    private static final Pattern EACH_FOR = Pattern.compile(
            "^for\\s*\\(\\s*(?:final\\s+)?(?:const\\s+|let\\s+|var\\s+|auto\\s*&?\\s*|[\\w<>\\[\\],.?\\s]+?[\\s&*]+)(\\w+)\\s*(?::|\\bof\\b)\\s*(.+)\\)$");
    private static final Pattern C_FOR = Pattern.compile("^for\\s*\\(\\s*(?:\\w+\\s+)?(\\w+)\\s*=[^;]*;([^;]*);");
    private static final Pattern SIZE_OF = Pattern.compile("(\\w+)\\s*\\.\\s*(length|size)\\b|\\blen\\s*\\(\\s*(\\w+)\\s*\\)");
    private static final Pattern PARTITION = Pattern.compile("^(\\w+)\\s*(?:\\[\\s*(\\w+)\\s*\\]|\\.get\\s*\\(\\s*(\\w+)\\s*\\))");
    private static final Pattern DERIVED = Pattern.compile("(\\w+)\\s*=\\s*(?:(\\w+)\\.toCharArray\\s*\\(\\s*\\)|(?:list|sorted)\\s*\\(\\s*(\\w+)\\s*\\))");

    /** What a loop header iterates over, and the variables it binds. */
    record LoopHead(Set<String> vars, Set<String> elements, String iterable, String bound) {
        static LoopHead of(String header) {
            String h = header.strip();
            Set<String> vars = new HashSet<>();
            Set<String> elements = new HashSet<>();
            Matcher py = PY_FOR.matcher(h);
            if (py.find()) {
                String iter = py.group(2).strip();
                List<String> names = new ArrayList<>();
                for (String v : py.group(1).replaceAll("[()\\[\\]]", "").split(",")) {
                    if (!v.isBlank()) {
                        names.add(v.strip());
                    }
                }
                vars.addAll(names);
                if (iter.startsWith("enumerate(") && names.size() > 1) {
                    elements.addAll(names.subList(1, names.size()));
                } else if (!iter.startsWith("range(")) {
                    elements.addAll(names);
                }
                return new LoopHead(vars, elements, iter, rootOf(iter));
            }
            Matcher each = EACH_FOR.matcher(h);
            if (each.find()) {
                vars.add(each.group(1));
                elements.add(each.group(1));
                String iter = each.group(2).strip();
                return new LoopHead(vars, elements, iter, rootOf(iter));
            }
            Matcher c = C_FOR.matcher(h);
            if (c.find()) {
                vars.add(c.group(1));
                return new LoopHead(vars, elements, null, rootOf(c.group(2)));
            }
            return new LoopHead(vars, elements, null, null);
        }

        /** The collection a header's size comes from: "range(len(nums))" → nums, "i < s.length()" → s. */
        static String rootOf(String expr) {
            if (expr == null) {
                return null;
            }
            Matcher size = SIZE_OF.matcher(expr);
            if (size.find()) {
                return size.group(1) != null ? size.group(1) : size.group(3);
            }
            Matcher range = Pattern.compile("range\\s*\\((?:[^,()]*,)?\\s*(\\w+)").matcher(expr);
            if (range.find()) {
                return range.group(1);
            }
            Matcher cmp = Pattern.compile("(<|<=|>|>=)\\s*(\\w+)").matcher(expr);
            if (cmp.find()) {
                return cmp.group(2);
            }
            Matcher id = Pattern.compile("^\\s*([A-Za-z_]\\w*)").matcher(expr);
            return id.find() ? id.group(1) : null;
        }
    }

    /** One analysis. Holds the parsed tree and what's been learned so far. */
    private static final class Run {
        final CodeLanguage lang;
        final String clean;
        final Node root;
        final boolean grid;
        final boolean visitedPresent;
        final Map<String, Node> functions = new LinkedHashMap<>();
        final Set<String> heapVars = new HashSet<>();
        final Map<Node, Fn> fnInfo = new IdentityHashMap<>();
        final Set<Node> inProgress = new HashSet<>();
        final LinkedHashSet<String> reasons = new LinkedHashSet<>();
        int confidence = 3;   // 3 high, 2 medium, 1 low
        Cx globalTime = Cx.ONE;

        Run(String code, CodeLanguage lang) {
            this.lang = lang;
            this.clean = CodeStructure.clean(code, lang);
            this.root = CodeStructure.parse(code, lang);
            this.grid = GRID.matcher(clean).find() && TWO_D_INDEX.matcher(clean).find();
            this.visitedPresent = VISITED.matcher(clean).find();
            for (Node n : root.descendants()) {
                if (n.kind == Kind.FUNCTION && n.name != null) {
                    functions.putIfAbsent(n.name, n);
                }
            }
            Matcher h = HEAP_DECL.matcher(clean);
            while (h.find()) {
                for (int g = 1; g <= h.groupCount(); g++) {
                    String v = h.group(g);
                    if (v != null && !v.matches("TreeMap|TreeSet")) {
                        heapVars.add(v);
                    }
                }
            }
        }

        Cx units() {
            return grid ? Cx.N2 : Cx.N;
        }

        void reason(String r) {
            reasons.add(r);
        }

        void lower(int to) {
            confidence = Math.min(confidence, to);
        }

        // ------------------------------------------------------------------ time

        ComplexityAnalysis result() {
            Cx time = costOf(root, new Ctx(null, List.of(), List.of(), false));
            Cx depth = Cx.ONE;
            for (Node f : functions.values()) {
                Fn info = fn(f);
                time = Cx.max(time, info.total);
                depth = Cx.max(depth, info.depth);
            }
            time = Cx.max(time, globalTime);
            Cx space = space(depth);

            List<String> out = new ArrayList<>();
            reasons.stream().limit(10).forEach(out::add);
            if (out.isEmpty()) {
                out.add("No loops, recursion or growing data structures: every step runs a fixed number of times.");
            }
            out.add(grid
                    ? "m × n is the size of the grid (m rows, n columns)."
                    : "n is the size of the input (for two inputs, the larger one)."
                            + (time.usesK() || space.usesK() ? " k is the size of each item, such as a word's length." : ""));
            out.add("An estimate from the code's structure: check it against your own reasoning.");
            String conf = confidence >= 3 ? "high" : confidence == 2 ? "medium" : "low";
            return new ComplexityAnalysis(time.format(grid), space.format(grid), out, conf, ComplexityAnalysis.ESTIMATE);
        }

        /** Cost of running {@code node}'s code once. */
        Cx costOf(Node node, Ctx ctx) {
            String own = node.own.toString();
            if (lang.usesIndentation() && node.kind == Kind.BLOCK) {
                own = node.header + "\n" + own;   // e.g. "if sorted(a) == sorted(b):"
            }
            Cx c = statements(own, node, ctx);
            for (Node child : node.children) {
                switch (child.kind) {
                    case FUNCTION -> { }   // runs when called: counted at the call, and on its own
                    case BLOCK, ROOT -> c = Cx.max(c, costOf(child, ctx));
                    case LOOP -> {
                        // what the header computes once, like "for x in sorted(nums)"
                        c = Cx.max(c, statements(child.header.replaceFirst("^(for|while)\\b", ""), node, ctx));
                        Cx f = factor(child, ctx);
                        Ctx inner = ctx.enter(child, f, isQueueDrain(child));
                        Cx body = costOf(child, inner);
                        Cx total = f.times(body);
                        if (f.equals(Cx.N)) {
                            Cx chain = Cx.ONE;
                            for (Cx x : inner.factors) {
                                chain = chain.times(x);
                            }
                            String where = ctx.loops.isEmpty() ? ""
                                    : " inside the loop on line " + ctx.loops.get(ctx.loops.size() - 1).line;
                            String note = "";
                            if (!grid && !ctx.loops.isEmpty()) {
                                String innerOver = LoopHead.of(child.header).bound();
                                String outerOver = LoopHead.of(ctx.loops.get(ctx.loops.size() - 1).header).bound();
                                if (innerOver != null && outerOver != null && !innerOver.equals(outerOver)
                                        && !LoopHead.of(ctx.loops.get(ctx.loops.size() - 1).header).vars().contains(innerOver)) {
                                    note = " These loops go over different things (" + outerOver + " and " + innerOver
                                            + "): with sizes m and n it's O(m·n).";
                                    lower(2);
                                }
                            }
                            reason("Line " + child.line + ": a loop over the input" + where + " → " + chain.format(grid)
                                    + (chain.equals(f) ? "." : " (nested loops multiply).") + note);
                        }
                        c = Cx.max(c, total);
                    }
                }
            }
            return c;
        }

        /** Library calls and calls to your own functions in straight-line code. */
        Cx statements(String own, Node owner, Ctx ctx) {
            Cx c = Cx.ONE;
            String where = ctx.loops.isEmpty() ? "" : " inside the loop on line " + ctx.loops.get(ctx.loops.size() - 1).line;
            for (String stmt : own.split("[\\n;]")) {
                if (stmt.isBlank()) {
                    continue;
                }
                if (SORT.matcher(stmt).find()) {
                    boolean item = !grid && identifiers(stmt).stream().anyMatch(ctx.elements()::contains);
                    if (item) {
                        c = Cx.max(c, Cx.K_LOG_K);
                        reason("Sorting each item (size k)" + where + " costs O(k log k).");
                    } else {
                        c = Cx.max(c, Cx.N_LOG_N);
                        reason("Sorting costs O(n log n)" + where + ".");
                    }
                }
                if (PY_HEAP_OP.matcher(stmt).find() || heapOp(stmt)) {
                    c = Cx.max(c, Cx.LOG);
                    reason("Each heap (priority queue) push or pop costs O(log n)" + where + ".");
                }
                int fors = count(Pattern.compile("\\bfor\\b"), stmt);
                if (lang.usesIndentation() && fors > 0 && (stmt.contains("[") || stmt.contains("(") || stmt.contains("{"))) {
                    c = Cx.max(c, Cx.power(fors));
                    reason("A comprehension goes over the input" + where + " → " + Cx.power(fors).format(grid) + ".");
                }
                if (LINEAR_CALL.matcher(stmt).find()) {
                    c = Cx.max(c, Cx.N);
                    if (!where.isEmpty()) {
                        reason("A call that copies or scans a whole string or list (slice, substring, split, join, copy…)"
                                + where + " costs O(n) each time.");
                    }
                }
                Matcher agg = AGGREGATE.matcher(stmt);
                while (agg.find()) {
                    String args = argsAt(stmt, agg.end() - 1);
                    if (args != null && !topLevelComma(args) && !args.isBlank() && !args.matches("\\s*-?\\d+\\s*")) {
                        c = Cx.max(c, Cx.N);
                    }
                }
                for (Node f : functions.values()) {
                    if (f == ctx.function || f == owner) {
                        continue;
                    }
                    if (Pattern.compile("\\b" + Pattern.quote(f.name) + "\\s*\\(").matcher(stmt).find()) {
                        Fn info = fn(f);
                        c = Cx.max(c, info.perCall);
                    }
                }
            }
            return c;
        }

        boolean heapOp(String stmt) {
            for (String v : heapVars) {
                if (Pattern.compile("\\b" + Pattern.quote(v) + "\\.(offer|add|poll|remove|push|pop|put|pollFirst|pollLast|ceiling|floor|higher|lower|first|last|firstKey|lastKey|ceilingKey|floorKey)\\s*\\(").matcher(stmt).find()) {
                    return true;
                }
            }
            return false;
        }

        /** How many times a loop runs, relative to the input size. */
        Cx factor(Node loop, Ctx ctx) {
            String h = loop.header;
            if (isConstLoop(h)) {
                reason("Line " + loop.line + ": the loop runs a fixed number of times (like 26 letters or 4 directions), so it counts as O(1).");
                return Cx.ONE;
            }
            if (isLogLoop(loop)) {
                reason("Line " + loop.line + ": the loop halves (or doubles) its range each time → O(log n) iterations.");
                lower(2);
                return Cx.LOG;
            }
            if (ctx.traversal && ADJACENCY.matcher(h).find()) {
                reason("Line " + loop.line + ": going through each node's neighbours: over the whole search every edge is looked at once, so it adds O(edges) in total rather than multiplying.");
                lower(2);
                return Cx.ONE;
            }
            LoopHead head = LoopHead.of(h);
            if (!grid && !ctx.loops.isEmpty() && isElementLoop(head, ctx)) {
                reason("Line " + loop.line + ": goes through the contents of each item (a word's letters, say) of size k → multiplies by k.");
                return Cx.K;
            }
            if (!grid && !ctx.loops.isEmpty() && head.iterable() != null) {
                Matcher part = PARTITION.matcher(head.iterable());
                LoopHead outer = LoopHead.of(ctx.loops.get(ctx.loops.size() - 1).header);
                if (part.find()) {
                    String index = part.group(2) != null ? part.group(2) : part.group(3);
                    if (outer.vars().contains(index)) {
                        reason("Line " + loop.line + ": goes through " + part.group(1) + "[" + index + "] for every " + index
                                + ": each item sits in just one of those lists, so this adds up to O(n) in total rather than multiplying.");
                        lower(2);
                        return Cx.ONE;
                    }
                }
            }
            if (!ctx.loops.isEmpty() && isAmortised(loop, ctx.loops.get(ctx.loops.size() - 1))) {
                reason("Line " + loop.line + ": this inner loop only moves forward (each element is added and removed at most once over the whole run), so it's O(1) amortised per outer step, not O(n).");
                lower(2);
                return Cx.ONE;
            }
            if (isQueueDrain(loop) && visitedPresent && !ctx.loops.isEmpty()) {
                globalTime = Cx.max(globalTime, units());
                reason("Line " + loop.line + ": breadth-first search started from inside another loop: with the visited check each "
                        + (grid ? "cell" : "node") + " enters the queue once over the whole run → " + units().format(grid)
                        + " in total, not per start.");
                lower(2);
                return Cx.ONE;
            }
            if (isQueueDrain(loop) && visitedPresent) {
                reason("Line " + loop.line + ": breadth-first search: with the visited check, each " + (grid ? "cell" : "node")
                        + " enters the queue once → " + units().format(grid) + ".");
                lower(2);
                return units();
            }
            return Cx.N;
        }

        /** An inner loop over the outer loop's current item: "for c in s" inside "for s in words". */
        boolean isElementLoop(LoopHead head, Ctx ctx) {
            Set<String> items = ctx.elements();
            if (items.isEmpty()) {
                return false;
            }
            if (head.iterable() != null) {
                String root = head.iterable().replaceAll("^\\s*(sorted|list|reversed|set|enumerate)\\s*\\(", "");
                Matcher id = Pattern.compile("^\\s*([A-Za-z_]\\w*)").matcher(root);
                return id.find() && items.contains(id.group(1));
            }
            return head.bound() != null && items.contains(head.bound());
        }

        boolean isConstLoop(String h) {
            return RANGE_CONST.matcher(h).find() || C_CONST.matcher(h).find() || DIRECTIONS.matcher(h).find();
        }

        boolean isLogLoop(Node loop) {
            String h = loop.header;
            String own = loop.own.toString();
            Set<String> condVars = identifiers(h);
            for (Pattern p : List.of(DOUBLING, DOUBLING2)) {
                Matcher m = p.matcher(h + "\n" + own);
                while (m.find()) {
                    String v = m.group(1);
                    if (condVars.contains(v) && (p == DOUBLING || v.equals(m.group(2)))) {
                        return true;
                    }
                }
            }
            return h.startsWith("while") && BOUNDS.matcher(h).find() && MID.matcher(own).find();
        }

        /** An inner while loop whose pointer or stack only moves forward across the outer loop. */
        boolean isAmortised(Node inner, Node outer) {
            if (!inner.header.startsWith("while")) {
                return false;
            }
            String body = inner.own.toString();
            Set<String> resetInOuter = assigned(outer.own.toString());
            resetInOuter.addAll(identifiersBeforeIn(outer.header));
            Matcher st = STACKISH.matcher(inner.header);
            while (st.find()) {
                String name = st.group(1);
                if (POP.matcher(body).find() && !resetInOuter.contains(name)) {
                    return true;
                }
            }
            Matcher inc = Pattern.compile("(\\w+)\\s*(\\+\\+|--|\\+=\\s*1\\b|-=\\s*1\\b)|(\\+\\+|--)\\s*(\\w+)").matcher(body);
            while (inc.find()) {
                String v = inc.group(1) != null ? inc.group(1) : inc.group(4);
                if (v != null && !resetInOuter.contains(v)) {
                    return true;
                }
            }
            return false;
        }

        boolean isQueueDrain(Node loop) {
            return loop.header.startsWith("while") && QUEUEISH.matcher(loop.header).find() && POP.matcher(loop.body).find();
        }

        // ------------------------------------------------------------------ functions

        /** What we know about one function. */
        record Fn(Cx perCall, Cx total, Cx depth) {
        }

        Fn fn(Node f) {
            Fn known = fnInfo.get(f);
            if (known != null) {
                return known;
            }
            if (inProgress.contains(f)) {
                return new Fn(Cx.ONE, Cx.ONE, Cx.ONE);   // mutual recursion: don't loop forever
            }
            inProgress.add(f);
            Pattern call = Pattern.compile("\\b" + Pattern.quote(f.name) + "\\s*\\(");
            int selfCalls = count(call, f.body);
            boolean callInLoop = f.descendants().stream()
                    .anyMatch(d -> d.kind == Kind.LOOP && call.matcher(d.body + " " + d.header).find());
            List<String> args = callArgs(call, f.body);
            boolean memo = selfCalls > 0 && (f.cacheDecorator || (MEMO_CHECK.matcher(f.body).find() && MEMO_NAME.matcher(f.body).find()));
            boolean tree = selfCalls > 0 && args.stream().anyMatch(a -> TREE_ARG.matcher(a).find());
            boolean visitOnce = selfCalls > 0 && !tree && !memo && VISITED.matcher(f.body).find();
            boolean halving = args.stream().anyMatch(a -> HALVING_ARG.matcher(a).find());
            // In a traversal, looping over a node's neighbours adds up to O(edges) overall; it doesn't multiply.
            Cx body = costOf(f, new Ctx(f, List.of(), List.of(), tree || visitOnce));

            Fn result;
            String name = f.name + "()";
            if (selfCalls == 0) {
                result = new Fn(body, body, Cx.ONE);
            } else {
                if (memo) {
                    int k = Math.max(1, Math.min(3, indexParams(f.params)));
                    Cx states = Cx.power(k);
                    Cx total = states.times(body);
                    reason(name + " is recursive with memoisation: each distinct set of arguments is worked out once. About "
                            + states.format(grid) + " states × " + body.format(grid) + " work each → " + total.format(grid) + ".");
                    lower(2);
                    result = new Fn(total, total, Cx.N);
                } else if (tree) {
                    Cx total = Cx.N.times(body);
                    reason(name + " visits each tree node once → " + total.format(false)
                            + ". Its recursion goes as deep as the tree: O(h), which is O(log n) if balanced and O(n) if not.");
                    lower(2);
                    result = new Fn(total, total, Cx.H);
                } else if (visitOnce) {
                    Cx total = units().times(body);
                    reason(name + " is a depth-first search that marks what it has visited, so each "
                            + (grid ? "cell" : "node") + " is explored once: " + total.format(grid)
                            + " over the whole run, however many times it's started.");
                    lower(2);
                    globalTime = Cx.max(globalTime, total);
                    result = new Fn(Cx.ONE, total, units());
                } else if (halving && !callInLoop) {
                    Cx total;
                    if (selfCalls == 1) {
                        total = body.equals(Cx.ONE) ? Cx.LOG : body;
                    } else {
                        total = body.equals(Cx.ONE) ? Cx.N : body.times(Cx.LOG);
                    }
                    reason(name + " splits the problem in half " + (selfCalls == 1 ? "and recurses into one half" : "and recurses into both halves")
                            + ", doing " + body.format(grid) + " work per call → " + total.format(grid) + ". Recursion depth O(log n).");
                    lower(2);
                    result = new Fn(total, total, Cx.LOG);
                } else if (callInLoop) {
                    boolean perm = PERMUTATION.matcher(f.body).find();
                    Cx base = perm ? Cx.N_FACTORIAL : Cx.TWO_TO_N;
                    boolean copies = PATH_COPY.matcher(f.body).find();
                    Cx total = copies ? Cx.N.times(base) : base;
                    reason(name + " is backtracking: it tries every " + (perm ? "ordering (n choices, then n − 1, …)" : "include/skip choice")
                            + (copies ? ", copying an O(n) path for each answer" : "") + " → " + total.format(false) + ".");
                    lower(1);
                    result = new Fn(total, total, Cx.N);
                } else if (selfCalls >= 2 && PATH_COPY.matcher(f.body).find()) {
                    Cx total = Cx.N.times(Cx.TWO_TO_N);
                    reason(name + " is backtracking: for each element it tries both including and skipping it (2ⁿ combinations), copying an O(n) path for each answer → " + total.format(false) + ".");
                    lower(1);
                    result = new Fn(total, total, Cx.N);
                } else if (selfCalls >= 2) {
                    reason(name + " calls itself " + selfCalls + " times per call with no memoisation, so the work roughly doubles at each level → O(2ⁿ). Memoising it would make it much faster.");
                    lower(1);
                    result = new Fn(Cx.TWO_TO_N, Cx.TWO_TO_N, Cx.N);
                } else {
                    Cx total = Cx.N.times(body);
                    reason(name + " calls itself once per step, about n levels deep → " + total.format(grid) + ", with O(n) recursion depth.");
                    lower(2);
                    result = new Fn(total, total, Cx.N);
                }
            }
            inProgress.remove(f);
            fnInfo.put(f, result);
            return result;
        }

        int indexParams(String params) {
            int k = 0;
            for (String p : params.split(",")) {
                String t = p.replaceAll("=.*$", "").replaceAll(":.*$", "").strip();
                Matcher m = Pattern.compile("([A-Za-z_]\\w*)\\s*$").matcher(t);
                if (m.find() && INDEX_PARAM.matcher(m.group(1)).matches()) {
                    k++;
                }
            }
            return k;
        }

        // ------------------------------------------------------------------ space

        Cx space(Cx depth) {
            Cx space = Cx.ONE;
            String[] lines = clean.split("\n", -1);
            boolean growing = root.descendants().stream().anyMatch(n -> n.kind == Kind.LOOP)
                    || fnInfo.values().stream().anyMatch(f -> !f.depth.equals(Cx.ONE));
            boolean skippedOutput = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int no = i + 1;
                if (line.isBlank() || line.strip().startsWith("return")) {
                    continue;
                }
                if (OUTPUT_VAR.matcher(line).find()) {
                    skippedOutput = true;
                    continue;
                }
                Cx found = null;
                String what = null;
                Matcher m;
                if ((m = NEW_2D.matcher(line)).find() || (m = CPP_2D.matcher(line)).find()) {
                    found = dims(m.group(1), m.group(2));
                    what = "a 2-D table";
                } else if ((m = PY_2D.matcher(line)).find()) {
                    found = dims(m.group(1), m.group(2));
                    what = "a 2-D table";
                } else if ((m = JS_2D.matcher(line)).find()) {
                    found = dims(m.group(1), m.group(3));
                    what = "a 2-D table";
                } else if ((m = NEW_1D.matcher(line)).find() || (m = CPP_1D.matcher(line)).find() || (m = PY_1D.matcher(line)).find()) {
                    found = NUMBER.matcher(m.group(1)).matches() ? Cx.ONE : Cx.N;
                    what = found.equals(Cx.ONE) ? "a fixed-size array" : "an array sized by the input";
                } else if ((m = JS_1D.matcher(line)).find()) {
                    String d = m.group(1) != null ? m.group(1) : m.group(2);
                    found = NUMBER.matcher(d).matches() ? Cx.ONE : Cx.N;
                    what = found.equals(Cx.ONE) ? "a fixed-size array" : "an array sized by the input";
                } else if (COLLECTION.matcher(line).find()) {
                    found = growing ? Cx.N : Cx.ONE;
                    what = "a list, map, set, stack or string builder that can grow with the input";
                }
                if (found == null) {
                    continue;
                }
                if (found.equals(Cx.ONE)) {
                    reason("Line " + no + ": " + what + " (like 26 letter counts) is O(1) space.");
                } else {
                    reason("Line " + no + ": " + what + " → " + found.format(grid) + " space.");
                }
                space = Cx.max(space, found);
            }
            if (!depth.equals(Cx.ONE)) {
                reason("The recursion keeps up to " + depth.format(grid) + " calls on the call stack at once, which counts as space.");
            }
            if (skippedOutput) {
                reason("The answer being returned isn't counted as extra space (the usual convention).");
            }
            return Cx.max(space, depth);
        }

        Cx dims(String a, String b) {
            boolean fa = a != null && NUMBER.matcher(a).matches();
            boolean fb = b != null && NUMBER.matcher(b).matches();
            if (fa && fb) {
                return Cx.ONE;
            }
            return fa || fb ? Cx.N : Cx.N2;
        }

        // ------------------------------------------------------------------ helpers

        static Set<String> identifiers(String s) {
            Set<String> out = new HashSet<>();
            Matcher m = Pattern.compile("[A-Za-z_]\\w*").matcher(s);
            while (m.find()) {
                out.add(m.group());
            }
            return out;
        }

        /** Names assigned in a piece of code ("x = …", "int x = …", "a, b = …"). */
        static Set<String> assigned(String code) {
            Set<String> out = new HashSet<>();
            Matcher m = Pattern.compile("\\b(\\w+)\\s*=(?!=)").matcher(code);
            while (m.find()) {
                out.add(m.group(1));
            }
            Matcher t = Pattern.compile("(\\w+(?:\\s*,\\s*\\w+)+)\\s*=(?!=)").matcher(code);
            while (t.find()) {
                for (String part : t.group(1).split(",")) {
                    out.add(part.strip());
                }
            }
            return out;
        }

        /** The loop variable(s) of a for header: "for (int i = …" or "for i, x in …". */
        static Set<String> identifiersBeforeIn(String header) {
            Set<String> out = new HashSet<>();
            Matcher py = Pattern.compile("^for\\s+(.+?)\\s+in\\b").matcher(header);
            if (py.find()) {
                for (String p : py.group(1).split(",")) {
                    out.add(p.strip());
                }
            }
            out.addAll(assigned(header));
            return out;
        }

        static int count(Pattern p, String s) {
            int n = 0;
            Matcher m = p.matcher(s);
            while (m.find()) {
                n++;
            }
            return n;
        }

        static List<String> callArgs(Pattern call, String body) {
            List<String> out = new ArrayList<>();
            Matcher m = call.matcher(body);
            while (m.find()) {
                String a = argsAt(body, m.end() - 1);
                if (a != null) {
                    out.add(a);
                }
            }
            return out;
        }

        /** The text between the '(' at {@code open} and its matching ')'. */
        static String argsAt(String s, int open) {
            int depth = 0;
            for (int i = open; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    return s.substring(open + 1, i);
                }
            }
            return null;
        }

        static boolean topLevelComma(String args) {
            int depth = 0;
            for (char c : args.toCharArray()) {
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Where we are while walking the tree: the enclosing function and loops. */
    private record Ctx(Node function, List<Node> loops, List<Cx> factors, boolean traversal, Set<String> elements) {
        Ctx(Node function, List<Node> loops, List<Cx> factors, boolean traversal) {
            this(function, loops, factors, traversal, Set.of());
        }

        Ctx enter(Node loop, Cx factor, boolean startsTraversal) {
            List<Node> l = new ArrayList<>(loops);
            l.add(loop);
            List<Cx> f = new ArrayList<>(factors);
            f.add(factor);
            // Variables holding the current item (and copies like "chars = s.toCharArray()")
            Set<String> items = new HashSet<>(elements);
            if (factor.equals(Cx.N)) {
                items.addAll(LoopHead.of(loop.header).elements());
            }
            Matcher d = DERIVED.matcher(loop.own.toString());
            while (d.find()) {
                String from = d.group(2) != null ? d.group(2) : d.group(3);
                if (items.contains(from)) {
                    items.add(d.group(1));
                }
            }
            return new Ctx(function, l, f, traversal || startsTraversal, items);
        }
    }
}
