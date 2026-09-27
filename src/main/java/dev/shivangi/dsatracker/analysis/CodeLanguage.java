package dev.shivangi.dsatracker.analysis;

/** The languages the code box accepts. Python is read by indentation, the others by braces. */
public enum CodeLanguage {
    JAVA("Java"),
    PYTHON("Python"),
    JAVASCRIPT("JavaScript"),
    CPP("C++");

    private final String label;

    CodeLanguage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean usesIndentation() {
        return this == PYTHON;
    }
}
