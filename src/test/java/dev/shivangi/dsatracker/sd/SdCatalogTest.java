package dev.shivangi.dsatracker.sd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The system design content is complete and well formed. */
class SdCatalogTest {

    private final SdCatalog catalog = SdCatalog.load(new ObjectMapper());

    @Test
    void hasThirtyOneTopicsInFourSections() {
        assertEquals(31, catalog.topics().size());
        Map<String, Long> bySection = catalog.topics().stream()
                .collect(Collectors.groupingBy(SdCatalog.Topic::section, Collectors.counting()));
        assertEquals(Map.of("Core Concepts", 9L, "Patterns", 7L, "Key Technologies", 10L, "Advanced Topics", 5L), bySection);
    }

    @Test
    void everyTopicHasTenWellFormedQuestions() {
        for (SdCatalog.Topic t : catalog.topics()) {
            assertEquals(10, t.questions().size(), t.key());
            assertTrue(t.url().startsWith(SdCatalog.BASE_URL), t.key());
            Set<String> questions = new HashSet<>();
            for (SdCatalog.Question q : t.questions()) {
                assertTrue(questions.add(q.q()), "repeated question in " + t.key() + ": " + q.q());
                assertEquals(4, q.options().size(), q.q());
                assertEquals(4, new HashSet<>(q.options()).size(), "repeated option: " + q.q());
                assertTrue(q.options().stream().noneMatch(String::isBlank), q.q());
                assertFalse(q.why().isBlank(), q.q());
            }
        }
    }

    @Test
    void hasThirtyTwoProblemsAndEveryOneIsScored() {
        List<SdCatalog.Problem> problems = catalog.problems();
        assertEquals(32, problems.size());
        Map<String, Long> byLevel = problems.stream()
                .collect(Collectors.groupingBy(SdCatalog.Problem::level, Collectors.counting()));
        assertEquals(Map.of("EASY", 4L, "MEDIUM", 16L, "HARD", 12L), byLevel);
        for (SdCatalog.Problem p : problems) {
            assertTrue(p.url().startsWith(SdCatalog.BASE_URL), p.key());
            assertTrue(p.scored(), p.key() + " has no rubric");
        }
    }

    @Test
    void scoredProblemsHaveARubricAndACompleteReference() {
        for (SdCatalog.Problem p : catalog.problems()) {
            // Harder problems are judged on more deep dives, so they have more key points.
            int min = p.level().equals("HARD") ? 10 : p.level().equals("MEDIUM") ? 9 : 8;
            assertTrue(p.rubric().size() >= min && p.rubric().size() <= 12, p.key() + ": " + p.rubric().size() + " points");
            for (SdCatalog.RubricPoint r : p.rubric()) {
                assertFalse(r.keywords().isEmpty(), r.point());
                for (String k : r.keywords()) {
                    assertEquals(k.toLowerCase(Locale.ROOT), k, "keywords are lower case: " + k);
                }
            }
            SdCatalog.Reference ref = p.reference();
            assertFalse(ref.functional().isEmpty(), p.key());
            assertFalse(ref.nonFunctional().isEmpty(), p.key());
            assertFalse(ref.entities().isEmpty(), p.key());
            assertFalse(ref.api().isEmpty(), p.key());
            assertFalse(ref.highLevel().isEmpty(), p.key());
            assertTrue(ref.deepDives().size() >= (p.level().equals("EASY") ? 2 : 3), p.key());
        }
    }

    @Test
    void theReferenceDesignScoresFullMarksOnItsOwnRubric() {
        DesignScorer scorer = new DesignScorer();
        for (SdCatalog.Problem p : catalog.problems()) {
            if (!p.scored()) {
                continue;
            }
            SdCatalog.Reference r = p.reference();
            String dives = r.deepDives().stream().map(d -> d.title() + ": " + d.body()).collect(Collectors.joining("\n"));
            DesignSections s = new DesignSections(String.join("\n", r.functional()) + "\n" + String.join("\n", r.nonFunctional()),
                    String.join("\n", r.entities()), String.join("\n", r.api()), String.join("\n", r.highLevel()), dives);
            DesignScorer.Score score = scorer.score(p, s);
            assertEquals(List.of(), score.missed(), p.key() + " reference misses its own rubric");
        }
    }
}
