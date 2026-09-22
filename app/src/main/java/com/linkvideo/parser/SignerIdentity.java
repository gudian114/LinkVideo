package com.linkvideo.parser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class SignerIdentity {
    private SignerIdentity() { }

    static String sha256(byte[] certificate) {
        if (certificate == null) throw new IllegalArgumentException("签名证书为空");
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(certificate);
            StringBuilder result = new StringBuilder(64);
            for (byte item : value) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("设备不支持 SHA-256", exception);
        }
    }

    static boolean sameActiveSigners(Set<String> installed, Set<String> candidate) {
        Set<String> left = normalize(installed);
        Set<String> right = normalize(candidate);
        return !left.isEmpty() && left.equals(right);
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) return result;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
