package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizTest {

    private final Quiz quiz = new Quiz(new Random(7));
    private final SdCatalog.Topic topic = topic(10);

    private static SdCatalog.Topic topic(int n) {
        List<SdCatalog.Question> qs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            qs.add(new SdCatalog.Question("Q" + i, List.of("right" + i, "a" + i, "b" + i, "c" + i), "because " + i));
        }
        return new SdCatalog.Topic("t", "Core Concepts", "T", "u", qs);
    }

    @Test
    void picksFiveDistinctQuestionsWithShuffledOptions() {
        List<Quiz.Asked> asked = quiz.pick(topic, List.of());
        assertEquals(5, asked.size());
        Set<Integer> indexes = new HashSet<>();
        for (Quiz.Asked a : asked) {
            assertTrue(indexes.add(a.index()));
            assertEquals(new HashSet<>(topic.questions().get(a.index()).options()), new HashSet<>(a.options()));
        }
    }

    @Test
    void unseenQuestionsComeFirst() {
        List<Quiz.Asked> asked = quiz.pick(topic, List.of("0+,1+,2-", "3+,4-"));
        for (Quiz.Asked a : asked) {
            assertTrue(a.index() >= 5, "asked a seen question: " + a.index());
        }
    }

    @Test
    void thenTheOnesYouGotWrongLastTime() {
        // All seen; 2 and 8 were wrong most recently (8 was right before, wrong since; 4 was wrong, then right).
        List<Quiz.Asked> asked = quiz.pick(topic, List.of("0+,1+,2-,3+,4-", "5+,6+,7+,8+,9+", "8-,4+"));
        Set<Integer> picked = new HashSet<>();
        asked.forEach(a -> picked.add(a.index()));
        assertTrue(picked.contains(2) && picked.contains(8), "wrong answers should come back: " + picked);
    }

    @Test
    void gradesOnTheServerAndTurnsTheScoreIntoARating() {
        List<Quiz.Answer> answers = List.of(new Quiz.Answer(3, "right3"), new Quiz.Answer(1, "a1"),
                new Quiz.Answer(7, "right7"), new Quiz.Answer(0, "right0"), new Quiz.Answer(9, "right9"));
        Quiz.Result r = quiz.grade(topic, answers);
        assertEquals(4, r.correct());
        assertEquals(5, r.total());
        assertEquals(80, r.percent());
        assertEquals(Rating.GOOD, r.rating());
        assertEquals("3+,1-,7+,0+,9+", r.detail());
        assertEquals("right1", r.questions().get(1).answer());
        assertEquals("because 1", r.questions().get(1).why());
    }

    @Test
    void refusesIncompleteOrRepeatedAnswers() {
        assertThrows(IllegalArgumentException.class, () -> quiz.grade(topic, List.of(new Quiz.Answer(0, "right0"))));
        assertThrows(IllegalArgumentException.class, () -> quiz.grade(topic, List.of(new Quiz.Answer(0, "x"),
                new Quiz.Answer(0, "x"), new Quiz.Answer(1, "x"), new Quiz.Answer(2, "x"), new Quiz.Answer(3, "x"))));
        assertThrows(IllegalArgumentException.class, () -> quiz.grade(topic, List.of(new Quiz.Answer(10, "x"),
                new Quiz.Answer(0, "x"), new Quiz.Answer(1, "x"), new Quiz.Answer(2, "x"), new Quiz.Answer(3, "x"))));
    }

    @Test
    void ratingThresholds() {
        assertEquals(Rating.EASY, Quiz.ratingFor(5, 5));
        assertEquals(Rating.GOOD, Quiz.ratingFor(4, 5));
        assertEquals(Rating.HARD, Quiz.ratingFor(3, 5));
        assertEquals(Rating.AGAIN, Quiz.ratingFor(2, 5));
        assertEquals(Rating.AGAIN, Quiz.ratingFor(0, 5));
    }

    @Test
    void latestOutcomeWins() {
        assertEquals(Map.of(1, true, 2, false, 3, true), Quiz.lastOutcomes(List.of("1-,2+", "2-,1+,3+", "bad,")));
    }

    @Test
    void aSavedQuizCanBeShownAgainWithYourAnswers() {
        List<Quiz.Graded> shown = Quiz.replay(topic, "3+,1-,7+", "right3\na1\nright7");
        assertEquals(3, shown.size());
        assertEquals("Q1", shown.get(1).q());
        assertEquals("a1", shown.get(1).chosen());
        assertEquals("right1", shown.get(1).answer());
        assertEquals(false, shown.get(1).right());
        assertEquals("because 1", shown.get(1).why());
    }

    @Test
    void olderQuizzesWithoutStoredAnswersStillShowRightAndWrong() {
        List<Quiz.Graded> shown = Quiz.replay(topic, "3+,1-,99+,x", null);
        assertEquals(2, shown.size());                  // unknown and malformed entries are skipped
        assertEquals("right3", shown.get(0).chosen());  // a right answer must have been the right option
        assertEquals(null, shown.get(1).chosen());      // a wrong one: unknown
        assertEquals(List.of(), Quiz.replay(topic, null, null));
    }
}
