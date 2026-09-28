package dev.shivangi.dsatracker.repetition;

/**
 * How a solve or a review went, in your own judgement, as in Anki.
 * When marking a problem done the page calls these Forgot / Hard / Medium / Easy;
 * at reviews, Again / Hard / Good / Easy.
 */
public enum Rating {
    /** Couldn't do it without a hint or the solution. */
    AGAIN,
    /** Got there, with real effort. */
    HARD,
    /** Solved it with normal effort. */
    GOOD,
    /** Solved it quickly and confidently. */
    EASY;

    /** Everything except AGAIN counts as solved (for the recall rate). */
    public boolean solved() {
        return this != AGAIN;
    }
}
