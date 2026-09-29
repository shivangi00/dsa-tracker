package dev.shivangi.dsatracker.sd;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.stream.Stream;

/**
 * A written design in the five parts of a Hello Interview-style answer. Any part may be blank.
 *
 * @param requirements functional and non-functional requirements (and rough numbers)
 * @param entities     core entities
 * @param api          the API or interface
 * @param highLevel    the high-level design: components and how a request flows
 * @param deepDives    deep dives: scaling, bottlenecks, trade-offs
 */
public record DesignSections(String requirements, String entities, String api, String highLevel, String deepDives) {

    public static final int MAX_SECTION = 4000;
    /** A part counts as written once it has at least this many characters. */
    public static final int MIN_FILLED = 30;

    /** Trims each part; blank parts become null. */
    public DesignSections normalised() {
        return new DesignSections(clean(requirements), clean(entities), clean(api), clean(highLevel), clean(deepDives));
    }

    public Stream<String> parts() {
        return Stream.of(requirements, entities, api, highLevel, deepDives);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return parts().allMatch(p -> p == null || p.isBlank());
    }

    public int filled() {
        return (int) parts().filter(p -> p != null && p.strip().length() >= MIN_FILLED).count();
    }

    public String allText() {
        StringBuilder sb = new StringBuilder();
        parts().filter(p -> p != null).forEach(p -> sb.append(p).append('\n'));
        return sb.toString();
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
