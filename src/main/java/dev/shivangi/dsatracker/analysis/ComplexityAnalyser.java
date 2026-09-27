package dev.shivangi.dsatracker.analysis;

/** Works out the time and space complexity of a piece of code. */
public interface ComplexityAnalyser {

    ComplexityAnalysis analyse(String code, CodeLanguage language);
}
