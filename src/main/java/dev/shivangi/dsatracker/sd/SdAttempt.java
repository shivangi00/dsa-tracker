package dev.shivangi.dsatracker.sd;

import dev.shivangi.dsatracker.repetition.Rating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;

/**
 * One sitting: the first study or design (revision 0), or a revision. Topics keep the quiz result.
 * Editable on its own day, frozen after, like the DSA attempts.
 */
@Entity
@Table(name = "sd_attempts")
public class SdAttempt {

    public static final int FIRST = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int revision;

    @Column(name = "try_no", nullable = false)
    private int tryNo;

    @Column(name = "attempted_on", nullable = false)
    private LocalDate attemptedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rating rating;

    private String notes;

    @Column(name = "excalidraw_url")
    private String excalidrawUrl;

    @Column(name = "quiz_score")
    private Integer quizScore;

    @Column(name = "quiz_total")
    private Integer quizTotal;

    @Column(name = "quiz_detail")
    private String quizDetail;

    /** The option chosen for each question, one per line, in quiz_detail's order. Null before V13. */
    @Column(name = "quiz_choices")
    private String quizChoices;

    protected SdAttempt() {
        // for JPA
    }

    public SdAttempt(Long itemId, int revision, int tryNo, LocalDate attemptedOn, Rating rating,
                     String notes, String excalidrawUrl) {
        this.itemId = itemId;
        this.revision = revision;
        this.tryNo = tryNo;
        this.attemptedOn = attemptedOn;
        this.rating = rating;
        this.notes = notes;
        this.excalidrawUrl = excalidrawUrl;
    }

    public void recordQuiz(Quiz.Result result) {
        this.quizScore = result.correct();
        this.quizTotal = result.total();
        this.quizDetail = result.detail();
        this.quizChoices = String.join("\n", result.questions().stream()
                .map(q -> clip(q.chosen() == null ? "" : q.chosen().replaceAll("[\\r\\n]+", " "), 300)).toList());
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public boolean isEditableOn(LocalDate today) {
        return attemptedOn.equals(today);
    }

    /** @throws IllegalStateException once the day is over */
    public void requireEditable(LocalDate today) {
        if (!isEditableOn(today)) {
            throw new IllegalStateException("Frozen: this " + (revision == FIRST ? "attempt" : "revision")
                    + " could only be changed on " + attemptedOn + ", the day you did it");
        }
    }

    public void editNotes(String notes, String excalidrawUrl, LocalDate today) {
        requireEditable(today);
        this.notes = notes;
        this.excalidrawUrl = excalidrawUrl;
    }

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public int getRevision() { return revision; }
    public int getTryNo() { return tryNo; }
    public LocalDate getAttemptedOn() { return attemptedOn; }
    public Rating getRating() { return rating; }
    public String getNotes() { return notes; }
    public String getExcalidrawUrl() { return excalidrawUrl; }
    public Integer getQuizScore() { return quizScore; }
    public Integer getQuizTotal() { return quizTotal; }
    public String getQuizDetail() { return quizDetail; }
    public String getQuizChoices() { return quizChoices; }
}
