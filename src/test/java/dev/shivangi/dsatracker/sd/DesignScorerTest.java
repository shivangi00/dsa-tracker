package dev.shivangi.dsatracker.sd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignScorerTest {

    private final DesignScorer scorer = new DesignScorer();

    private static final SdCatalog.Problem SHORTENER = new SdCatalog.Problem("s", "EASY", "Shortener", "u", List.of(
            new SdCatalog.RubricPoint("Short codes", List.of("base62", "counter")),
            new SdCatalog.RubricPoint("Caching", List.of("cache", "redis")),
            new SdCatalog.RubricPoint("Direct upload", List.of("pre-signed", "presigned")),
            new SdCatalog.RubricPoint("Expiry", List.of("expir", "ttl"))), null);

    private static final String LONG = "x".repeat(DesignSections.MIN_FILLED);

    @Test
    void problemsWithoutARubricAreNotScored() {
        SdCatalog.Problem unscored = new SdCatalog.Problem("u", "HARD", "U", "u", List.of(), null);
        assertNull(scorer.score(unscored, new DesignSections("a", null, null, null, null)));
    }

    @Test
    void fullAnswerGetsTen() {
        DesignSections s = new DesignSections(LONG, LONG, LONG, LONG + " base62 codes, Redis in front",
                LONG + " pre-signed URLs; links expire with a TTL");
        DesignScorer.Score score = scorer.score(SHORTENER, s);
        assertEquals(10.0, score.total(), 0.001);
        assertEquals(List.of(0, 1, 2, 3), score.covered());
        assertEquals(List.of(), score.missed());
    }

    @Test
    void structureIsPointFourPerWrittenPart() {
        DesignSections s = new DesignSections(LONG, "too short", null, LONG, null);
        DesignScorer.Score score = scorer.score(SHORTENER, s);
        assertEquals(0.8, score.structure(), 0.001);
        assertEquals(0.0, score.rubric(), 0.001);
        assertEquals(4, score.missed().size());
    }

    @Test
    void rubricIsTheShareOfPointsCovered() {
        DesignSections s = new DesignSections(null, null, null, "A counter encoded in base62", null);
        DesignScorer.Score score = scorer.score(SHORTENER, s);
        assertEquals(2.0, score.rubric(), 0.001);
        assertEquals(List.of("Short codes"), score.hits());
        assertEquals(List.of("Caching", "Direct upload", "Expiry"), score.missed());
    }

    @Test
    void matchesWordStartsAndCommonEndings() {
        String text = DesignScorer.normalise("We use CACHING, pre signed URLs, and links expiring after 30 days.");
        assertTrue(DesignScorer.mentionsAny(text, List.of("cache")));
        assertTrue(DesignScorer.mentionsAny(text, List.of("pre-signed")));
        assertTrue(DesignScorer.mentionsAny(text, List.of("expir")));
        assertFalse(DesignScorer.mentionsAny(DesignScorer.normalise("predisposed"), List.of("redis")));
        assertTrue(DesignScorer.mentionsAny(DesignScorer.normalise("reads:writes is 100:1"), List.of("100:1")));
    }
}
