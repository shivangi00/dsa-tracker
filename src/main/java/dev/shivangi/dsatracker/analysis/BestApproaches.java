package dev.shivangi.dsatracker.analysis;

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

/**
 * Reads {@code best-approaches.json}: for each NeetCode 150 problem (by catalog id), the best known
 * approaches with their time and space complexity and the idea behind them.
 */
public final class BestApproaches {

    public static final String RESOURCE = "/best-approaches.json";
    public static final String TIPS = "/interview-tips.json";

    private BestApproaches() {
    }

    /** The approaches, each problem with its interview talking points. */
    public static Map<Integer, KnownProblem> load(ObjectMapper json) {
        try {
            Map<Integer, KnownProblem> byId = parse(json, read(RESOURCE));
            Map<Integer, List<String>> tips = parseTips(json, read(TIPS));
            Map<Integer, KnownProblem> merged = new LinkedHashMap<>();
            byId.forEach((id, p) -> merged.put(id, new KnownProblem(p.approaches(), p.fixedSize(),
                    tips.getOrDefault(id, List.of()))));
            return merged;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = BestApproaches.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Map<Integer, List<String>> parseTips(ObjectMapper json, String text) throws IOException {
        Map<Integer, List<String>> byId = new LinkedHashMap<>();
        for (JsonNode problem : json.readTree(text)) {
            List<String> tips = new ArrayList<>();
            for (JsonNode t : problem.path("tips")) {
                tips.add(t.asText());
            }
            if (byId.put(problem.path("id").asInt(), List.copyOf(tips)) != null) {
                throw new IllegalStateException("Duplicate id " + problem.path("id").asInt() + " in " + TIPS);
            }
        }
        return byId;
    }

    static Map<Integer, KnownProblem> parse(ObjectMapper json, String text) throws IOException {
        Map<Integer, KnownProblem> byId = new LinkedHashMap<>();
        for (JsonNode problem : json.readTree(text)) {
            List<Approach> list = new ArrayList<>();
            for (JsonNode a : problem.path("approaches")) {
                list.add(new Approach(a.path("name").asText(), a.path("time").asText(), a.path("space").asText(),
                        a.path("idea").asText(), a.path("advanced").asBoolean()));
            }
            String fixedSize = problem.path("fixedSize").asText("");
            KnownProblem known = new KnownProblem(list, fixedSize.isBlank() ? null : fixedSize);
            if (byId.put(problem.path("id").asInt(), known) != null) {
                throw new IllegalStateException("Duplicate id " + problem.path("id").asInt() + " in " + RESOURCE);
            }
        }
        return byId;
    }
}
