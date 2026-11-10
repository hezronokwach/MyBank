package com.example.mybank.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts card numbers and CVVs before they are saved, and decrypts them when the customer
 * enters their PIN to see them. The database never holds them in plain text.
 *
 * It uses AES-GCM with a 256-bit key from the CARD_ENCRYPTION_KEY environment variable
 * (32 random bytes, Base64-encoded). GCM also detects if the saved text was changed.
 *
 * Saved format: Base64 of [12-byte IV][encrypted text + 16-byte check tag].
 * A new random IV is used every time, so the same card number never looks the same twice.
 *
 * The app starts without a key, so the rest of MyBank works; issuing or showing a card then fails clearly.
 */
@Component
public class CardEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final String base64Key;
    private final SecureRandom random = new SecureRandom();

    public CardEncryptor(@Value("${app.cards.encryption-key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] saved = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, saved, 0, iv.length);
            System.arraycopy(encrypted, 0, saved, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(saved);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt card details", e);
        }
    }

    public String decrypt(String savedText) {
        try {
            byte[] saved = Base64.getDecoder().decode(savedText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(saved, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[saved.length - IV_LENGTH];
            System.arraycopy(saved, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt card details", e);
        }
    }

    private SecretKeySpec key() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("CARD_ENCRYPTION_KEY is not set, so cards can't be issued or shown");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("CARD_ENCRYPTION_KEY must be 32 bytes, Base64-encoded");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
