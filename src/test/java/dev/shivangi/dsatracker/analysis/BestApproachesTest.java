package dev.shivangi.dsatracker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The approach list covers exactly the 150 catalog problems, and every complexity in it can be read. */
class BestApproachesTest {

    private final Map<Integer, KnownProblem> approaches = BestApproaches.load(new ObjectMapper());

    @Test
    void coversEveryCatalogProblem() throws IOException {
        Map<Integer, String> catalog = catalogNames();
        assertEquals(150, catalog.size());
        assertEquals(catalog.keySet(), approaches.keySet());
    }

    @Test
    void namesMatchTheCatalog() throws IOException {
        Map<Integer, String> catalog = catalogNames();
        String json = resource(BestApproaches.RESOURCE);
        Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+),\"problem\":\"([^\"]+)\"").matcher(json);
        int seen = 0;
        while (m.find()) {
            assertEquals(catalog.get(Integer.parseInt(m.group(1))), m.group(2));
            seen++;
        }
        assertEquals(150, seen);
    }

    @Test
    void everyEntryIsUsable() {
        approaches.forEach((id, problem) -> {
            List<Approach> list = problem.approaches();
            assertTrue(list.stream().anyMatch(a -> !a.advanced()), "problem " + id + " needs a usual approach");
            for (Approach a : list) {
                assertTrue(!a.name().isBlank() && !a.idea().isBlank(), "problem " + id + " has a blank field");
                assertTrue(ComplexityExpression.evaluate(a.time()).isPresent(), id + ": can't read " + a.time());
                assertTrue(ComplexityExpression.evaluate(a.space()).isPresent(), id + ": can't read " + a.space());
            }
        });
    }

    private static Map<Integer, String> catalogNames() throws IOException {
        String sql = resource("/db/migration/V3__neetcode_150.sql");
        Matcher m = java.util.regex.Pattern.compile("\\((\\d+),\\s*'[^']+',\\s*\\d+,\\s*'((?:[^']|'')+)'").matcher(sql);
        Map<Integer, String> names = new HashMap<>();
        while (m.find()) {
            names.put(Integer.parseInt(m.group(1)), m.group(2).replace("''", "'"));
        }
        return names;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = BestApproachesTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
