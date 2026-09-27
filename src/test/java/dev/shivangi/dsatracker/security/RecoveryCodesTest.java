package dev.shivangi.dsatracker.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCodesTest {

    private final RecoveryCodes codes = new RecoveryCodes();

    @Test
    void codesAreFourGroupsOfFourFromTheUnambiguousAlphabet() {
        String code = codes.generate();
        assertTrue(code.matches("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}"), code);
        assertTrue(RecoveryCodes.looksValid(code));
    }

    @Test
    void everyCodeIsDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(seen.add(codes.generate()));
        }
    }

    @Test
    void typingIsForgiving() {
        assertEquals("K7QM2XPA9RTBHW4N", RecoveryCodes.normalise("k7qm-2xpa-9rtb-hw4n"));
        assertEquals("K7QM2XPA9RTBHW4N", RecoveryCodes.normalise("  K7QM 2XPA 9RTB HW4N "));
        // O is read as zero, and I or L as one, because those letters are never used
        assertEquals("10Q01", RecoveryCodes.normalise("IOQOL"));
    }

    @Test
    void wrongShapesAreRejected() {
        assertFalse(RecoveryCodes.looksValid("K7QM-2XPA-9RTB"));          // too short
        assertFalse(RecoveryCodes.looksValid("K7QM-2XPA-9RTB-HW4NX"));    // too long
        assertFalse(RecoveryCodes.looksValid("K7QM-2XPA-9RTB-HW4U"));     // U isn't in the alphabet
        assertFalse(RecoveryCodes.looksValid(null));
    }
}
