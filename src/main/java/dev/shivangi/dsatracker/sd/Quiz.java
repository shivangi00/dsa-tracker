package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A topic's quiz: {@value #SIZE} of its questions, the ones you haven't seen first, then the ones you
 * got wrong last time. Options are shuffled every time; grading happens on the server.
 */
public final class Quiz {

    public static final int SIZE = 5;

    /** A question as the page sees it: options shuffled, the answer not marked. */
    public record Asked(int index, String q, List<String> options) {
    }

    /** Your answer to one question: its index in the topic and the option text you chose. */
    public record Answer(int index, String choice) {
    }

    public record Graded(int index, String q, String chosen, String answer, boolean right, String why) {
    }

    /**
     * @param detail what to store: "3+,7-,1+" (question index, right or wrong)
     */
    public record Result(int correct, int total, String detail, Rating rating, List<Graded> questions) {
        public int percent() {
            return Math.round(100f * correct / total);
        }
    }

    private final Random random;

    public Quiz(Random random) {
        this.random = random;
    }

    /** Picks the questions: unseen first, then last answered wrong, then the rest; random within each group. */
    public List<Asked> pick(SdCatalog.Topic topic, List<String> pastDetails) {
        Map<Integer, Boolean> last = lastOutcomes(pastDetails);
        List<Integer> unseen = new ArrayList<>();
        List<Integer> wrong = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        for (int i = 0; i < topic.questions().size(); i++) {
            Boolean outcome = last.get(i);
            (outcome == null ? unseen : outcome ? right : wrong).add(i);
        }
        Collections.shuffle(unseen, random);
        Collections.shuffle(wrong, random);
        Collections.shuffle(right, random);
        List<Integer> order = new ArrayList<>(unseen);
        order.addAll(wrong);
        order.addAll(right);

        List<Asked> asked = new ArrayList<>();
        for (int index : order.subList(0, Math.min(SIZE, order.size()))) {
            SdCatalog.Question q = topic.questions().get(index);
            List<String> options = new ArrayList<>(q.options());
            Collections.shuffle(options, random);
            asked.add(new Asked(index, q.q(), List.copyOf(options)));
        }
        return asked;
    }

    /**
     * Grades a quiz.
     *
     * @throws IllegalArgumentException unless there are {@value #SIZE} answers (or all of a shorter
     *                                  topic) to distinct questions of this topic
     */
    public Result grade(SdCatalog.Topic topic, List<Answer> answers) {
        int expected = Math.min(SIZE, topic.questions().size());
        if (answers == null || answers.size() != expected) {
            throw new IllegalArgumentException("Answer all " + expected + " questions");
        }
        Set<Integer> seen = new HashSet<>();
        List<Graded> graded = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        int correct = 0;
        for (Answer a : answers) {
            if (a.index() < 0 || a.index() >= topic.questions().size() || !seen.add(a.index())) {
                throw new IllegalArgumentException("Unknown or repeated question " + a.index());
            }
            SdCatalog.Question q = topic.questions().get(a.index());
            boolean right = q.answer().equals(a.choice());
            if (right) {
                correct++;
            }
            graded.add(new Graded(a.index(), q.q(), a.choice(), q.answer(), right, q.why()));
            if (!detail.isEmpty()) {
                detail.append(',');
            }
            detail.append(a.index()).append(right ? '+' : '-');
        }
        return new Result(correct, answers.size(), detail.toString(), ratingFor(correct, answers.size()), List.copyOf(graded));
    }

    /** 90%+ Easy, 70%+ Good, 50%+ Hard, below that Again. With 5 questions: 5 Easy, 4 Good, 3 Hard, 0–2 Again. */
    public static Rating ratingFor(int correct, int total) {
        double share = (double) correct / total;
        if (share >= 0.9) {
            return Rating.EASY;
        }
        if (share >= 0.7) {
            return Rating.GOOD;
        }
        if (share >= 0.5) {
            return Rating.HARD;
        }
        return Rating.AGAIN;
    }

    /** The latest outcome of each question across past quizzes (oldest first), from "3+,7-" strings. */
    static Map<Integer, Boolean> lastOutcomes(List<String> pastDetails) {
        Map<Integer, Boolean> last = new LinkedHashMap<>();
        for (String d : pastDetails) {
            if (d == null || d.isBlank()) {
                continue;
            }
            for (String part : d.split(",")) {
                String p = part.strip();
                if (p.length() >= 2) {
                    try {
                        last.put(Integer.parseInt(p.substring(0, p.length() - 1)), p.endsWith("+"));
                    } catch (NumberFormatException ignored) {
                        // skip anything malformed
                    }
                }
            }
        }
        return last;
    }
}
