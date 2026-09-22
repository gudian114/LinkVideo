package com.linkvideo.parser;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DownloadFileNaming {
    private static final String BASE_NAME = "downloadfile";

    private DownloadFileNaming() { }

    static String nextAvailableName(String extension, Collection<String> existingNames) {
        String suffix = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!suffix.isEmpty() && !suffix.startsWith(".")) suffix = "." + suffix;
        Set<String> occupied = new HashSet<>();
        if (existingNames != null) {
            for (String name : existingNames) {
                if (name != null) occupied.add(name.toLowerCase(Locale.ROOT));
            }
        }
        String candidate = BASE_NAME + suffix;
        if (!occupied.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        for (int index = 1; ; index++) {
            candidate = BASE_NAME + " (" + index + ")" + suffix;
            if (!occupied.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
    }
}
