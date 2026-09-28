package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.analysis.CodeLanguage;
import dev.shivangi.dsatracker.repetition.Rating;

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
     * A saved version of your code, with its analysis if it has one. {@code improved}: its time
     * complexity beats every version you saved before it, in this attempt or earlier ones.
     */
    public record Version(long id, int versionNo, String code, CodeLanguage codeLanguage, AnalysisView analysis,
                          boolean improved) {
    }
}
