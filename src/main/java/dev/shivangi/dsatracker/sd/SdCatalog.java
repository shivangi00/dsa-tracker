package dev.shivangi.dsatracker.sd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The system design content: Hello Interview's topics (with quiz questions written for this app)
 * and design problems (with a rubric and a reference design for those scored so far). Pages on
 * Hello Interview are linked, never copied.
 */
public final class SdCatalog {

    public static final String TOPICS = "/sd-topics.json";
    public static final String PROBLEMS = "/sd-problems.json";
    public static final String BASE_URL = "https://www.hellointerview.com/learn/system-design/";

    /** A quiz question. The correct answer is {@code options.get(0)}; options are shuffled when asked. */
    public record Question(String q, List<String> options, String why) {
        public String answer() {
            return options.get(0);
        }
    }

    public record Topic(String key, String section, String name, String url, List<Question> questions) {
    }

    /** One thing a strong answer covers, and words that show it was covered. */
    public record RubricPoint(String point, List<String> keywords) {
    }

    public record DeepDive(String title, String body) {
    }

    /** A reference design, written for this app, to compare your own against. */
    public record Reference(List<String> functional, List<String> nonFunctional, List<String> entities,
                            List<String> api, List<String> highLevel, List<DeepDive> deepDives) {
    }

    public record Problem(String key, String level, String name, String url, List<RubricPoint> rubric,
                          Reference reference) {
        /** True once this problem has a rubric (the four easy problems so far). */
        public boolean scored() {
            return !rubric.isEmpty();
        }
    }

    private final Map<String, Topic> topics;
    private final Map<String, Problem> problems;

    public SdCatalog(Map<String, Topic> topics, Map<String, Problem> problems) {
        this.topics = topics;
        this.problems = problems;
    }

    public static SdCatalog load(ObjectMapper json) {
        try {
            return new SdCatalog(parseTopics(json, read(TOPICS)), parseProblems(json, read(PROBLEMS)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Topic> topics() {
        return List.copyOf(topics.values());
    }

    public List<Problem> problems() {
        return List.copyOf(problems.values());
    }

    public Optional<Topic> topic(String key) {
        return Optional.ofNullable(topics.get(key));
    }

    public Optional<Problem> problem(String key) {
        return Optional.ofNullable(problems.get(key));
    }

    /** The name of a topic or problem, for lists that mix both. */
    public String name(SdKind kind, String key) {
        return kind == SdKind.TOPIC
                ? topic(key).map(Topic::name).orElse(key)
                : problem(key).map(Problem::name).orElse(key);
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = SdCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Map<String, Topic> parseTopics(ObjectMapper json, String text) throws IOException {
        Map<String, Topic> byKey = new LinkedHashMap<>();
        for (JsonNode t : json.readTree(text)) {
            List<Question> questions = new ArrayList<>();
            for (JsonNode q : t.path("questions")) {
                if (q.path("answer").asInt(0) != 0) {
                    throw new IllegalStateException("The correct option must come first: " + q.path("q").asText());
                }
                questions.add(new Question(q.path("q").asText(), strings(q.path("options")), q.path("why").asText()));
            }
            Topic topic = new Topic(t.path("key").asText(), t.path("section").asText(), t.path("name").asText(),
                    BASE_URL + t.path("path").asText(), List.copyOf(questions));
            if (byKey.put(topic.key(), topic) != null) {
                throw new IllegalStateException("Duplicate topic " + topic.key());
            }
        }
        return byKey;
    }

    static Map<String, Problem> parseProblems(ObjectMapper json, String text) throws IOException {
        Map<String, Problem> byKey = new LinkedHashMap<>();
        for (JsonNode p : json.readTree(text)) {
            List<RubricPoint> rubric = new ArrayList<>();
            for (JsonNode r : p.path("rubric")) {
                rubric.add(new RubricPoint(r.path("point").asText(), strings(r.path("keywords"))));
            }
            Reference reference = null;
            JsonNode ref = p.path("reference");
            if (!ref.isMissingNode()) {
                List<DeepDive> dives = new ArrayList<>();
                for (JsonNode d : ref.path("deepDives")) {
                    dives.add(new DeepDive(d.path("title").asText(), d.path("body").asText()));
                }
                reference = new Reference(strings(ref.path("functional")), strings(ref.path("nonFunctional")),
                        strings(ref.path("entities")), strings(ref.path("api")), strings(ref.path("highLevel")),
                        List.copyOf(dives));
            }
            Problem problem = new Problem(p.path("key").asText(), p.path("level").asText(), p.path("name").asText(),
                    BASE_URL + p.path("path").asText(), List.copyOf(rubric), reference);
            if (byKey.put(problem.key(), problem) != null) {
                throw new IllegalStateException("Duplicate problem " + problem.key());
            }
        }
        return byKey;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asText());
        }
        return List.copyOf(out);
    }
}
