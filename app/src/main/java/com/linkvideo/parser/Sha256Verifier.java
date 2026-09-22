package com.linkvideo.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class Sha256Verifier {
    private Sha256Verifier() { }

    static String digest(File file) throws IOException {
        byte[] value = digestBytes(file);
        StringBuilder hex = new StringBuilder(64);
        for (byte item : value) hex.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return hex.toString();
    }

    static boolean matches(File file, String expected) throws IOException {
        if (expected == null || !expected.matches("(?i)[0-9a-f]{64}")) return false;
        return MessageDigest.isEqual(digestBytes(file), decodeHex(expected));
    }

    private static byte[] digestBytes(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("设备不支持 SHA-256", exception);
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
