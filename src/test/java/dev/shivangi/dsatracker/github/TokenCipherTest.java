package dev.shivangi.dsatracker.github;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

    @Test
    void roundTripsAndNeverStoresTheTokenAsIs() {
        TokenCipher c = new TokenCipher(KEY);
        String token = "github_pat_11ABCDEF_secret";
        String a = c.encrypt(token);
        String b = c.encrypt(token);
        assertNotEquals(a, b);                 // a fresh IV each time
        assertFalse(a.contains("secret"));
        assertEquals(token, c.decrypt(a));
        assertEquals(token, c.decrypt(b));
    }

    @Test
    void anotherKeyCannotReadIt() {
        String sealed = new TokenCipher(KEY).encrypt("github_pat_x");
        assertThrows(IllegalStateException.class, () -> new TokenCipher(OTHER_KEY).decrypt(sealed));
    }

    @Test
    void theKeyMustBe32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher("not base64!"));
    }
}
