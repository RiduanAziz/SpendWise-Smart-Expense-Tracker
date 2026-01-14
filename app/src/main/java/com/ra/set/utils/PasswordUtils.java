package com.ra.set.utils;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordUtils {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256; // bits

    public static String generateSalt() {
        try {
            SecureRandom sr = SecureRandom.getInstanceStrong();
            byte[] salt = new byte[16];
            sr.nextBytes(salt);
            return Base64.encodeToString(salt, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            // fallback
            SecureRandom sr = new SecureRandom();
            byte[] salt = new byte[16];
            sr.nextBytes(salt);
            return Base64.encodeToString(salt, Base64.NO_WRAP);
        }
    }

    public static String hashPassword(final String password, final String saltBase64) {
        if (password == null || saltBase64 == null) return null;
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}

