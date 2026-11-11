package dev.shivangi.dsatracker.github;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What your progress looks like in the GitHub repository. Pure: it turns a snapshot of the tracker's
 * data into file paths and contents, so it's easy to test and the same data always gives the same files.
 *
 * <pre>
 * README.md                                     your progress across all problems
 * 01 Arrays &amp; Hashing/
 *   001 Contains Duplicate/
 *     README.md                                 the problem, and every sitting at a glance
 *     Solved/            notes.md  v1.java  v2.py
 *     Revision 1/        notes.md  v1.java
 *     Revision 1 (try 2)/ …                     after an "Again"
 *     Revision 2/ …
 *     Revision 3/ …
 * </pre>
 * Each {@code notes.md} has the date, your rating, your notes, the drawing link and every saved
 * version's complexity analysis with its steps.
 */
public final class RepoLayout {

    public record Analysis(String time, String space, List<String> steps, String source) {
    }

    public record Version(int number, String language, String code, Analysis analysis) {
    }

    /** One sitting: the solve (revision 0) or a revision; {@code rating} is the label you chose. */
    public record Sitting(int revision, int tryNo, LocalDate on, String rating, String notes, String drawingUrl,
                          List<Version> versions) {
    }

    /**
     * @param number   the problem's NeetCode 150 position (folder order), or null for your own problems
     * @param topic    e.g. "Arrays &amp; Hashing"; topicOrder sorts the topic folders
     */
    public record Problem(Integer number, String topic, int topicOrder, String name, String difficulty, String url,
                          boolean fullyRevised, List<Sitting> sittings) {
    }

    public static final String ROOT_README = "README.md";

    private RepoLayout() {
    }

    // ---------------------------------------------------------------- paths

    public static String topicFolder(Problem p) {
        return String.format(Locale.ROOT, "%02d %s", p.topicOrder(), safe(p.topic()));
    }

    /** e.g. "01 Arrays &amp; Hashing/001 Contains Duplicate" */
    public static String problemFolder(Problem p) {
        String prefix = p.number() == null ? "" : String.format(Locale.ROOT, "%03d ", p.number());
        return topicFolder(p) + "/" + prefix + safe(p.name());
    }

    public static String sittingFolder(Sitting s) {
        String base = s.revision() == 0 ? "Solved" : "Revision " + s.revision();
        return s.tryNo() > 1 ? base + " (try " + s.tryNo() + ")" : base;
    }

    public static String extension(String language) {
        return switch (language == null ? "" : language) {
            case "JAVA" -> "java";
            case "PYTHON" -> "py";
            case "JAVASCRIPT" -> "js";
            case "CPP" -> "cpp";
            default -> "txt";
        };
    }

    /**
     * A name that's safe as a folder on every system: no slashes or characters Windows refuses
     * ({@code \ / : * ? " < > |}), no leading/trailing dots or spaces, at most 80 characters.
     */
    static String safe(String name) {
        String s = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ").strip().replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (s.length() > 80) {
            s = s.substring(0, 80).strip();
        }
        return s.isEmpty() ? "Untitled" : s;
    }

    // ---------------------------------------------------------------- files

    /** Every file of one problem, keyed by path from the repository root. */
    public static Map<String, String> problemFiles(Problem p) {
        Map<String, String> files = new LinkedHashMap<>();
        String folder = problemFolder(p);
        files.put(folder + "/README.md", problemReadme(p));
        for (Sitting s : p.sittings()) {
            String dir = folder + "/" + sittingFolder(s);
            files.put(dir + "/notes.md", sittingNotes(p, s));
            for (Version v : s.versions()) {
                files.put(dir + "/v" + v.number() + "." + extension(v.language()), withNewline(v.code()));
            }
        }
        return files;
    }

    static String problemReadme(Problem p) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(p.name()).append("\n\n");
        List<String> meta = new ArrayList<>();
        if (p.difficulty() != null) {
            meta.add(cap(p.difficulty()));
        }
        meta.add(p.topic());
        if (p.url() != null && !p.url().isBlank()) {
            meta.add("[Problem](" + p.url() + ")");
        }
        md.append(String.join(" · ", meta)).append("\n\n");
        md.append("| Sitting | Date | Rating | Best time | Best space | Code |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (Sitting s : p.sittings()) {
            Version best = latestAnalysed(s);
            md.append("| [").append(sittingFolder(s)).append("](").append(link(sittingFolder(s))).append("/notes.md) | ")
                    .append(s.on()).append(" | ").append(nz(s.rating())).append(" | ")
                    .append(best == null ? "–" : code(best.analysis().time())).append(" | ")
                    .append(best == null ? "–" : code(best.analysis().space())).append(" | ")
                    .append(s.versions().isEmpty() ? "–" : s.versions().size() + (s.versions().size() == 1 ? " version" : " versions"))
                    .append(" |\n");
        }
        md.append("\n_Pushed from my DSA tracker._\n");
        return md.toString();
    }

    static String sittingNotes(Problem p, Sitting s) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(p.name()).append(" · ").append(sittingFolder(s)).append("\n\n");
        md.append("**Date:** ").append(s.on());
        if (s.rating() != null) {
            md.append(" · **Rating:** ").append(s.rating());
        }
        md.append("\n\n## Notes\n\n").append(s.notes() == null || s.notes().isBlank() ? "_No notes._" : s.notes().strip()).append("\n");
        if (s.drawingUrl() != null && !s.drawingUrl().isBlank()) {
            md.append("\n[Drawing on Excalidraw](").append(s.drawingUrl()).append(")\n");
        }
        for (Version v : s.versions()) {
            String file = "v" + v.number() + "." + extension(v.language());
            md.append("\n## Version ").append(v.number()).append(" · [").append(file).append("](").append(file).append(")\n\n");
            if (v.analysis() == null) {
                md.append("_Not analysed._\n");
                continue;
            }
            md.append("**Time:** ").append(code(v.analysis().time())).append(" · **Space:** ").append(code(v.analysis().space()));
            if ("claude".equals(v.analysis().source())) {
                md.append(" · analysed by Claude");
            }
            md.append("\n");
            if (!v.analysis().steps().isEmpty()) {
                md.append("\n");
                for (String step : v.analysis().steps()) {
                    md.append("- ").append(step.strip()).append("\n");
                }
            }
        }
        return md.toString();
    }

    /** The repository's front page: every problem pushed so far, by topic. */
    public static String rootReadme(List<Problem> problems, String owner) {
        List<Problem> sorted = new ArrayList<>(problems);
        sorted.sort(Comparator.comparingInt(Problem::topicOrder)
                .thenComparing(p -> p.number() == null ? Integer.MAX_VALUE : p.number())
                .thenComparing(Problem::name));
        long revised = problems.stream().filter(Problem::fullyRevised).count();
        StringBuilder md = new StringBuilder();
        md.append("# NeetCode 150 · ").append(owner == null || owner.isBlank() ? "my" : owner).append("'s solutions\n\n");
        md.append(problems.size()).append(problems.size() == 1 ? " problem" : " problems")
                .append(" solved, ").append(revised).append(" fully revised. Each problem has a folder per sitting ")
                .append("(Solved, Revision 1–3) with my notes, every version of the code and its complexity analysis.\n\n");
        String topic = null;
        for (Problem p : sorted) {
            if (!p.topic().equals(topic)) {
                topic = p.topic();
                md.append("\n## ").append(topic).append("\n\n| Problem | Difficulty | Sittings | Latest |\n| --- | --- | --- | --- |\n");
            }
            Sitting last = p.sittings().isEmpty() ? null : p.sittings().get(p.sittings().size() - 1);
            Version v = null;
            for (Sitting s : p.sittings()) {
                Version x = latestAnalysed(s);
                if (x != null) {
                    v = x;
                }
            }
            md.append("| [").append(p.name()).append("](").append(link(problemFolder(p))).append(") | ")
                    .append(p.difficulty() == null ? "–" : cap(p.difficulty())).append(" | ")
                    .append(last == null ? "–" : sittingFolder(last)).append(" | ")
                    .append(v == null ? "–" : code(v.analysis().time()) + " time, " + code(v.analysis().space()) + " space")
                    .append(" |\n");
        }
        return md.toString();
    }

    /** The commit message for pushing one problem. */
    public static String commitMessage(Problem p) {
        if (p.sittings().isEmpty()) {
            return p.name();
        }
        Sitting last = p.sittings().get(p.sittings().size() - 1);
        StringBuilder m = new StringBuilder(p.name()).append(": ").append(sittingFolder(last).toLowerCase(Locale.ROOT));
        if (last.rating() != null) {
            m.append(" (").append(last.rating()).append(")");
        }
        Version v = latestAnalysed(last);
        if (v != null) {
            m.append(" · ").append(v.analysis().time()).append(" time");
        }
        return m.toString();
    }

    private static Version latestAnalysed(Sitting s) {
        Version best = null;
        for (Version v : s.versions()) {
            if (v.analysis() != null) {
                best = v;
            }
        }
        return best;
    }

    /** A relative Markdown link to a path with spaces and symbols. */
    static String link(String path) {
        StringBuilder sb = new StringBuilder();
        for (char c : path.toCharArray()) {
            switch (c) {
                case ' ' -> sb.append("%20");
                case '&' -> sb.append("%26");
                case '(' -> sb.append("%28");
                case ')' -> sb.append("%29");
                case '#' -> sb.append("%23");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String code(String s) {
        return s == null ? "–" : "`" + s + "`";
    }

    private static String nz(String s) {
        return s == null ? "–" : s;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String withNewline(String code) {
        String c = code == null ? "" : code.stripTrailing();
        return c + "\n";
    }
}
