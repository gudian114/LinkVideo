package com.linkvideo.parser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Extracts only in-memory identity values; callers must never log or persist them. */
final class DouyinCookieIdentity {
    private static final String[] KEYS = {"ttwid", "odin_tt", "sessionid", "sessionid_ss"};

    static boolean hasStableIdentity(String cookies) {
        return !identity(cookies).isEmpty();
    }

    static boolean changed(String before, String after) {
        return !identity(before).equals(identity(after));
    }

    private static Map<String, String> identity(String cookies) {
        Map<String, String> result = new LinkedHashMap<>();
        if (cookies == null || cookies.trim().isEmpty()) return result;
        for (String part : cookies.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            String name = part.substring(0, separator).trim().toLowerCase(Locale.US);
            if (!isIdentityKey(name)) continue;
            String value = part.substring(separator + 1).trim();
            if (!value.isEmpty()) result.put(name, value);
        }
        return result;
    }

    private static boolean isIdentityKey(String name) {
        for (String key : KEYS) if (key.equals(name)) return true;
        return false;
    }

    private DouyinCookieIdentity() { }
}
