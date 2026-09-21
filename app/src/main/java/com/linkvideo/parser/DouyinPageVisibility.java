package com.linkvideo.parser;

/** One browser session's deadlines, using monotonic elapsed milliseconds supplied by the caller. */
final class DouyinPageVisibility {
    private final boolean showInitially;
    private final long started;
    private long lastProgress;
    private long shownAt;
    private int highestProgress, highestReady;
    private boolean visible, finished;

    DouyinPageVisibility(boolean showInitially, long now) {
        this.showInitially = showInitially;
        started = lastProgress = now;
    }

    void observe(int progress, String ready, long now) {
        if (finished || visible) return;
        int readiness = "complete".equals(ready) ? 2 : "interactive".equals(ready) ? 1 : 0;
        if (progress > highestProgress || readiness > highestReady) lastProgress = now;
        highestProgress = Math.max(highestProgress, progress);
        highestReady = Math.max(highestReady, readiness);
    }

    boolean shouldReveal(long now, boolean foreground) {
        return !finished && !visible && foreground && !expired(now)
                && (showInitially || now - lastProgress >= 8000 || now - started >= 16000);
    }

    void shown(long now) {
        if (finished || visible) return;
        visible = true;
        shownAt = now;
    }

    boolean expired(long now) {
        return visible ? now - shownAt >= 60000 : now - started >= 25000;
    }

    void finish() { finished = true; }
}
