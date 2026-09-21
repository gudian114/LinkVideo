package com.linkvideo.parser;

import java.util.Locale;

final class PreviewTime {
    private PreviewTime() { }

    static String format(int positionMs, int durationMs) {
        int durationSeconds = Math.max(0, durationMs / 1000);
        int positionSeconds = Math.max(0, positionMs / 1000);
        positionSeconds = Math.min(positionSeconds, durationSeconds);
        return formatPart(positionSeconds) + " / " + formatPart(durationSeconds);
    }

    private static String formatPart(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }
}
