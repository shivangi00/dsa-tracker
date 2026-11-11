package dev.shivangi.dsatracker.github;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts GitHub tokens before they're stored (AES-256-GCM), so a copy of the database alone
 * doesn't give anyone access to your repository. The key lives only in the server's environment
 * ({@code GITHUB_TOKEN_KEY}, 32 random bytes in base64: {@code openssl rand -base64 32}).
 */
public final class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /** @throws IllegalArgumentException unless {@code base64Key} decodes to exactly 32 bytes */
    public TokenCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GITHUB_TOKEN_KEY isn't valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("GITHUB_TOKEN_KEY must be 32 bytes (openssl rand -base64 32), not " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Base64 of IV + ciphertext + tag. A fresh IV each time, so the same token encrypts differently. */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt the token", e);
        }
    }

    /** @throws IllegalStateException if the text was tampered with or encrypted with another key */
    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("The saved GitHub token can't be read (was GITHUB_TOKEN_KEY changed?). Connect again.", e);
        }
    }
}
