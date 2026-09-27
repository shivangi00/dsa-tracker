package dev.shivangi.dsatracker.security;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * One-time recovery codes like {@code K7QM-2XPA-9RTB-HW4N}: 16 characters from Crockford's
 * base-32 alphabet (no I, L, O or U, so nothing looks alike), which is 80 random bits. Far too
 * many to guess, even without the rate limits in front of it.
 *
 * <p>Typing is forgiving: {@link #normalise} ignores case, spaces and dashes, and reads O as 0
 * and I or L as 1, so a code copied by hand still works.
 */
public final class RecoveryCodes {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    /** A new code, grouped in fours for reading: "K7QM-2XPA-9RTB-HW4N". */
    public String generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** The form that gets hashed and compared: upper case, no separators, look-alikes mapped. */
    public static String normalise(String typed) {
        if (typed == null) {
            return "";
        }
        return typed.toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]", "")
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
    }

    /** True if it has the shape of a code (after normalising). */
    public static boolean looksValid(String typed) {
        String n = normalise(typed);
        if (n.length() != LENGTH) {
            return false;
        }
        for (char c : n.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
