package dev.dsatracker.dsa;

import dev.dsatracker.dsa.analysis.CodeLanguage;
import dev.dsatracker.dsa.analysis.Recommendation;
import dev.dsatracker.shared.repetition.Rating;

import java.time.LocalDate;
import java.util.List;

/**
 * One sitting: the solve day (revision 0) or a revision try.
 *
 * @param editable true only on the day it happened; frozen after
 */
public record AttemptView(long id, int revision, int tryNo, LocalDate attemptedOn, Rating rating, String learnings,
                          String excalidrawUrl, boolean editable, List<AttemptView.Version> versions) {

    /**
     * A saved version of your code.
     *
     * @param analysis        the built-in estimate (its recommendation is left out: see {@code recommendation})
     * @param yours           your own answer, or null
     * @param recommendation  how your answer (or, without one, the estimate) compares with the best known approaches
     * @param estimateDiffers a sentence when the estimate clearly disagrees with your answer, else null
     * @param improved        its time (yours, else the estimate's) beats every version you saved before it
     */
    public record Version(long id, int versionNo, String code, CodeLanguage codeLanguage, AnalysisView analysis,
                          Complexity yours, Recommendation recommendation, String estimateDiffers, boolean improved) {
    }

    /** A time and space pair, e.g. "O(n log k)" and "O(log k)"; space may be empty. */
    public record Complexity(String time, String space) {
    }
}
