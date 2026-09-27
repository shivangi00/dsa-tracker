package dev.shivangi.dsatracker.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns source code into a tree of loops, functions and blocks: the shape the complexity
 * analysis works on. Not a full parser, just enough structure: where loops start and end,
 * what's nested in what, and which function each piece of code belongs to.
 *
 * <p>Two readers: Python by indentation, Java / JavaScript / C++ by braces. Comments and the
 * insides of string literals are blanked out first (same length, so line numbers still match),
 * so a "for" or "{" inside a string can't confuse the structure.
 */
final class CodeStructure {

    enum Kind { ROOT, FUNCTION, LOOP, BLOCK }

    /** One node of the tree. */
    static final class Node {
        final Kind kind;
        final int line;
        String header = "";
        String name;
        String params = "";
        /** Code that belongs directly to this node (children's code excluded). */
        final StringBuilder own = new StringBuilder();
        /** All code inside this node, children included (header excluded). */
        String body = "";
        final List<Node> children = new ArrayList<>();
        Node parent;
        /** Python: decorated with @cache / @lru_cache. */
        boolean cacheDecorator;

        Node(Kind kind, int line) {
            this.kind = kind;
            this.line = line;
        }

        void add(Node child) {
            child.parent = this;
            children.add(child);
        }

        List<Node> descendants() {
            List<Node> out = new ArrayList<>();
            for (Node c : children) {
                out.add(c);
                out.addAll(c.descendants());
            }
            return out;
        }
    }

    private CodeStructure() {
    }

    static Node parse(String code, CodeLanguage language) {
        String clean = language.usesIndentation() ? blankPython(code) : blankBraces(code);
        return language.usesIndentation() ? parsePython(clean) : parseBraces(clean);
    }

    /** The code with comments and string contents blanked, for regex checks elsewhere. */
    static String clean(String code, CodeLanguage language) {
        return language.usesIndentation() ? blankPython(code) : blankBraces(code);
    }

    // ------------------------------------------------------------------ blanking

    static String blankBraces(String s) {
        StringBuilder out = new StringBuilder(s);
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    out.setCharAt(i++, ' ');
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    blank(out, i++);
                }
                if (i < n) {
                    out.setCharAt(i++, ' ');
                    if (i < n) {
                        out.setCharAt(i++, ' ');
                    }
                }
            } else if (c == '"' || c == '\'' || c == '`') {
                i = blankString(s, out, i, c);
            } else {
                i++;
            }
        }
        return out.toString();
    }

    static String blankPython(String s) {
        StringBuilder out = new StringBuilder(s);
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '#') {
                while (i < n && s.charAt(i) != '\n') {
                    out.setCharAt(i++, ' ');
                }
            } else if ((c == '"' || c == '\'') && s.startsWith(String.valueOf(c).repeat(3), i)) {
                String quote = String.valueOf(c).repeat(3);
                int end = s.indexOf(quote, i + 3);
                end = end < 0 ? n : end + 3;
                for (int k = i + 3; k < end - 3 && k < n; k++) {
                    blank(out, k);
                }
                i = end;
            } else if (c == '"' || c == '\'') {
                i = blankString(s, out, i, c);
            } else {
                i++;
            }
        }
        return out.toString();
    }

    /** Blanks a string literal's contents (keeps the quotes); returns the index after it. */
    private static int blankString(String s, StringBuilder out, int start, char quote) {
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                blank(out, i);
                blank(out, i + 1);
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            if (c == '\n' && quote != '`') {
                return i;   // unterminated: stop at the line end
            }
            blank(out, i);
            i++;
        }
        return i;
    }

    private static void blank(StringBuilder out, int i) {
        if (out.charAt(i) != '\n') {
            out.setCharAt(i, ' ');
        }
    }

    // ------------------------------------------------------------------ Python

    private static final Pattern PY_LOOP = Pattern.compile("^(for|while)\\b(.*)$");
    private static final Pattern PY_DEF = Pattern.compile("^(?:async\\s+)?def\\s+(\\w+)\\s*\\((.*)\\).*:\\s*$");

    static Node parsePython(String s) {
        Node root = new Node(Kind.ROOT, 1);
        Deque<Node> stack = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();
        stack.push(root);
        indents.push(-1);
        java.util.Map<Node, StringBuilder> bodyOf = new java.util.IdentityHashMap<>();
        bodyOf.put(root, new StringBuilder());

        String[] lines = s.split("\n", -1);
        boolean pendingCache = false;
        for (int idx = 0; idx < lines.length; idx++) {
            String raw = lines[idx].replace("\t", "    ");
            String text = raw.strip();
            if (text.isEmpty()) {
                continue;
            }
            int indent = raw.length() - raw.stripLeading().length();
            while (stack.size() > 1 && indent <= indents.peek()) {
                stack.pop();
                indents.pop();
            }
            Node parent = stack.peek();
            // every open block contains this line
            for (Node open : stack) {
                bodyOf.get(open).append(text).append('\n');
            }

            Node opened = null;
            Matcher loop = PY_LOOP.matcher(text);
            Matcher def = PY_DEF.matcher(text);
            if (loop.matches() && text.contains(":")) {
                opened = new Node(Kind.LOOP, idx + 1);
                int colon = headerColon(text);
                opened.header = colon < 0 ? text : text.substring(0, colon);
                String rest = colon < 0 ? "" : text.substring(colon + 1).strip();
                if (!rest.isEmpty()) {
                    opened.own.append(rest).append('\n');   // one-line loop body
                }
            } else if (def.matches()) {
                opened = new Node(Kind.FUNCTION, idx + 1);
                opened.name = def.group(1);
                opened.params = def.group(2);
                opened.header = text;
                opened.cacheDecorator = pendingCache;
            } else if (text.endsWith(":")) {
                opened = new Node(Kind.BLOCK, idx + 1);
                opened.header = text;
            } else {
                parent.own.append(text).append('\n');
            }
            pendingCache = text.startsWith("@") && text.matches("@(functools\\.)?(lru_)?cache\\b.*");
            if (opened != null) {
                parent.add(opened);
                bodyOf.put(opened, new StringBuilder());
                if (!opened.own.isEmpty()) {
                    bodyOf.get(opened).append(opened.own);
                }
                stack.push(opened);
                indents.push(indent);
            }
        }
        for (var e : bodyOf.entrySet()) {
            e.getKey().body = e.getValue().toString();
        }
        return root;
    }

    /** The colon that ends a Python header (not one inside brackets, like a slice). */
    private static int headerColon(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ braces

    private static final Set<String> NOT_FUNCTIONS = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "return", "new", "else", "do",
            "try", "finally", "function", "sizeof", "throw");
    private static final Pattern ARG_LOOP = Pattern.compile(".*\\.(forEach|map|filter|flatMap|reduce|some|every|for_each)\\s*\\($");

    static Node parseBraces(String s) {
        Node root = new Node(Kind.ROOT, 1);
        root.body = s;
        fill(root, s, 0, s.length());
        return root;
    }

    private static void fill(Node parent, String s, int from, int to) {
        int i = from;
        int segStart = from;
        int boundary = from;   // where the current statement began, for looking back from a '{'
        while (i < to) {
            char c = s.charAt(i);
            if (Character.isJavaIdentifierStart(c) && (i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1)))) {
                int wEnd = i;
                while (wEnd < to && Character.isJavaIdentifierPart(s.charAt(wEnd))) {
                    wEnd++;
                }
                String w = s.substring(i, wEnd);
                if (w.equals("for") || w.equals("while")) {
                    int p = skipWs(s, wEnd, to);
                    int close = p < to && s.charAt(p) == '(' ? match(s, p, to) : -1;
                    if (close > 0) {
                        parent.own.append(s, segStart, i);
                        Node loop = new Node(Kind.LOOP, lineOf(s, i));
                        loop.header = s.substring(i, close + 1);
                        int j = skipWs(s, close + 1, to);
                        int bodyFrom;
                        int bodyTo;
                        int next;
                        if (j < to && s.charAt(j) == '{') {
                            int e = match(s, j, to);
                            e = e < 0 ? to : e;
                            bodyFrom = j + 1;
                            bodyTo = e;
                            next = Math.min(e + 1, to);
                        } else if (j < to && s.charAt(j) == ';') {
                            bodyFrom = j;
                            bodyTo = j;
                            next = j + 1;
                        } else {
                            int e = statementEnd(s, j, to);
                            bodyFrom = j;
                            bodyTo = e;
                            next = e;
                        }
                        loop.body = s.substring(bodyFrom, bodyTo);
                        fill(loop, s, bodyFrom, bodyTo);
                        parent.add(loop);
                        i = next;
                        segStart = next;
                        boundary = next;
                        continue;
                    }
                } else if (w.equals("do")) {
                    int j = skipWs(s, wEnd, to);
                    int e = j < to && s.charAt(j) == '{' ? match(s, j, to) : -1;
                    if (e > 0) {
                        parent.own.append(s, segStart, i);
                        Node loop = new Node(Kind.LOOP, lineOf(s, i));
                        int w2 = skipWs(s, e + 1, to);
                        int next = e + 1;
                        if (s.startsWith("while", w2)) {
                            int p = skipWs(s, w2 + 5, to);
                            int close = p < to && s.charAt(p) == '(' ? match(s, p, to) : -1;
                            if (close > 0) {
                                loop.header = "while " + s.substring(p, close + 1);
                                next = close + 1;
                                int semi = skipWs(s, next, to);
                                if (semi < to && s.charAt(semi) == ';') {
                                    next = semi + 1;
                                }
                            }
                        }
                        loop.body = s.substring(j + 1, e);
                        fill(loop, s, j + 1, e);
                        parent.add(loop);
                        i = next;
                        segStart = next;
                        boundary = next;
                        continue;
                    }
                }
                i = wEnd;
                continue;
            }
            if (c == '{') {
                int e = match(s, i, to);
                e = e < 0 ? to : e;
                String look = s.substring(boundary, i);
                Node child = classify(look, lineOf(s, Math.max(boundary, skipWs(s, boundary, i))));
                // A function's signature isn't a call, so keep it out of the parent's own code.
                parent.own.append(s, segStart, child.kind == Kind.FUNCTION ? boundary : i);
                child.body = s.substring(i + 1, e);
                fill(child, s, i + 1, e);
                parent.add(child);
                i = Math.min(e + 1, to);
                segStart = i;
                boundary = i;
                continue;
            }
            if (c == ';' || c == '}') {
                boundary = i + 1;
            }
            i++;
        }
        parent.own.append(s, segStart, to);
    }

    /** Decides what a '{' opens by looking at the text before it. */
    private static Node classify(String look, int line) {
        String t = look.strip();
        t = t.replaceAll("\\)\\s*->\\s*[\\w:<>,\\s*&]+$", ")");                    // C++ trailing return type
        t = t.replaceAll("\\)\\s*(const\\s*)?(noexcept\\s*)?(override\\s*)?(throws\\s+[\\w.,\\s]+)?$", ")");
        t = t.replaceAll("\\s*(=>|->)\\s*$", "");                                   // lambda arrows
        if (t.endsWith(")")) {
            int open = matchBack(t, t.length() - 1);
            if (open >= 0) {
                String params = t.substring(open + 1, t.length() - 1);
                String before = t.substring(0, open).strip();
                if (before.endsWith("]")) {                                        // C++ lambda capture
                    int cap = before.lastIndexOf('[');
                    before = cap >= 0 ? before.substring(0, cap).strip() : before;
                }
                if (before.endsWith("function")) {
                    before = before.substring(0, before.length() - "function".length()).strip();
                }
                if (before.endsWith("async")) {
                    before = before.substring(0, before.length() - "async".length()).strip();
                }
                if (ARG_LOOP.matcher(before).matches()) {
                    Node loop = new Node(Kind.LOOP, line);
                    loop.header = t;
                    return loop;
                }
                String name = null;
                if (before.endsWith("=") && !before.endsWith("==")) {
                    Matcher m = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*=$").matcher(before);
                    name = m.find() ? m.group(1) : null;
                } else {
                    Matcher m = Pattern.compile("([A-Za-z_$~][\\w$]*)$").matcher(before);
                    name = m.find() ? m.group(1) : null;
                }
                if (name != null && !NOT_FUNCTIONS.contains(name)) {
                    Node fn = new Node(Kind.FUNCTION, line);
                    fn.name = name;
                    fn.params = params;
                    fn.header = t;
                    return fn;
                }
            }
        }
        Node block = new Node(Kind.BLOCK, line);
        block.header = t;
        return block;
    }

    /** Where the single statement starting at {@code j} ends (for loops without braces). */
    private static int statementEnd(String s, int j, int to) {
        j = skipWs(s, j, to);
        if (j >= to) {
            return to;
        }
        if (s.charAt(j) == '{') {
            int e = match(s, j, to);
            return e < 0 ? to : e + 1;
        }
        for (String kw : List.of("for", "while", "if")) {
            if (s.startsWith(kw, j) && (j + kw.length() >= to || !Character.isJavaIdentifierPart(s.charAt(j + kw.length())))) {
                int p = skipWs(s, j + kw.length(), to);
                int close = p < to && s.charAt(p) == '(' ? match(s, p, to) : -1;
                if (close > 0) {
                    int end = statementEnd(s, close + 1, to);
                    if (kw.equals("if")) {
                        int e2 = skipWs(s, end, to);
                        if (s.startsWith("else", e2)) {
                            end = statementEnd(s, e2 + 4, to);
                        }
                    }
                    return end;
                }
            }
        }
        int depth = 0;
        for (int k = j; k < to; k++) {
            char c = s.charAt(k);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth < 0) {
                    return k;
                }
            } else if (c == ';' && depth == 0) {
                return k + 1;
            }
        }
        return to;
    }

    /** Index of the bracket closing the one at {@code open}, or -1. */
    private static int match(String s, int open, int to) {
        char o = s.charAt(open);
        char c = o == '(' ? ')' : o == '[' ? ']' : '}';
        int depth = 0;
        for (int i = open; i < to; i++) {
            char ch = s.charAt(i);
            if (ch == o) {
                depth++;
            } else if (ch == c && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int matchBack(String s, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char ch = s.charAt(i);
            if (ch == ')') {
                depth++;
            } else if (ch == '(' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWs(String s, int i, int to) {
        while (i < to && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int i = 0; i < index && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
