package com.linkvideo.parser;

/** One browser session's deadlines, using monotonic elapsed milliseconds supplied by the caller. */
final class DouyinPageVisibility {
    private static final long INTERACTION_GRACE_MILLIS = 5_000L;
    private static final long DETECTION_GAP_MILLIS = 1_500L;
    private final boolean showInitially;
    private final long started;
    private long shownAt, interactionSeenAt = -1L, interactionMissingAt = -1L;
    private boolean interactionRequired, visible, finished;

    DouyinPageVisibility(boolean showInitially, long now) {
        this.showInitially = showInitially;
        started = now;
    }

    void observe(int progress, String ready, long now) {
        // Page progress is diagnostic only. It must not postpone a verification prompt.
    }

    void observeInteraction(boolean required, long now) {
        if (finished || visible) return;
        if (!required) {
            if (interactionSeenAt < 0L) return;
            if (interactionMissingAt < 0L) { interactionMissingAt = now; return; }
            if (now - interactionMissingAt <= DETECTION_GAP_MILLIS) return;
            interactionSeenAt = -1L;
            interactionMissingAt = -1L;
            interactionRequired = false;
            return;
        }
        if (interactionSeenAt < 0L) interactionSeenAt = now;
        interactionMissingAt = -1L;
        if (now - interactionSeenAt >= INTERACTION_GRACE_MILLIS) interactionRequired = true;
    }

    boolean shouldReveal(long now, boolean foreground) {
        return !finished && !visible && foreground && !expired(now)
                && (showInitially || interactionRequired);
    }

    void shown(long now) {
        if (finished || visible) return;
        visible = true;
        shownAt = now;
    }

    boolean expired(long now) {
        if (!visible && interactionSeenAt >= 0L && (interactionMissingAt < 0L
                || now - interactionMissingAt <= DETECTION_GAP_MILLIS)) return false;
        return visible ? now - shownAt >= 60000 : now - started >= 25000;
    }

    void finish() { finished = true; }
}
