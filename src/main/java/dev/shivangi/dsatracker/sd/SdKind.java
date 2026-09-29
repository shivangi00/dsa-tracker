package dev.shivangi.dsatracker.sd;

/** What a system design item is: a concept topic (revised with quizzes) or a design problem. */
public enum SdKind {
    /** A Hello Interview topic: three revisions, each a short quiz. */
    TOPIC(3),
    /** A design problem: two revisions, each a fresh written design. */
    PROBLEM(2);

    private final int revisions;

    SdKind(int revisions) {
        this.revisions = revisions;
    }

    public int revisions() {
        return revisions;
    }
}
