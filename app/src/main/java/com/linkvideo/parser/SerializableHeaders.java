package com.linkvideo.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Removes credentials before request metadata enters a Serializable result object. */
final class SerializableHeaders {
    private SerializableHeaders() { }

    static Map<String, String> copy(Map<String, String> source) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, String> header : source.entrySet()) {
                String name = header.getKey();
                String value = header.getValue();
                if (name == null || value == null || sensitive(name)) continue;
                safe.put(name, value);
            }
        }
        return Collections.unmodifiableMap(safe);
    }

    private static boolean sensitive(String name) {
        return "Cookie".equalsIgnoreCase(name)
                || "Authorization".equalsIgnoreCase(name)
                || "Proxy-Authorization".equalsIgnoreCase(name);
    }
}
