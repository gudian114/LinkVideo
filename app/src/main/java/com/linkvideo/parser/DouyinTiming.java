package com.linkvideo.parser;

import java.net.SocketTimeoutException;
import java.util.function.LongSupplier;

/** One parse attempt's monotonic total budget. */
final class DouyinTiming {
    static final long DEFAULT_BUDGET_MILLIS = 90_000L;

    private final LongSupplier clock;
    private final long deadlineNanos;

    DouyinTiming(LongSupplier clock, long budgetMillis) {
        if (clock == null) throw new IllegalArgumentException("clock");
        this.clock = clock;
        long now = clock.getAsLong();
        long budgetNanos = Math.max(1, budgetMillis) * 1_000_000L;
        deadlineNanos = now > Long.MAX_VALUE - budgetNanos ? Long.MAX_VALUE : now + budgetNanos;
    }

    static DouyinTiming start() {
        return new DouyinTiming(System::nanoTime, DEFAULT_BUDGET_MILLIS);
    }

    long remainingMillis() throws SocketTimeoutException {
        long remaining = deadlineNanos - clock.getAsLong();
        if (remaining <= 0) throw new SocketTimeoutException("抖音解析已超过总等待时间");
        return Math.max(1L, (remaining + 999_999L) / 1_000_000L);
    }

    void check(String stage) throws SocketTimeoutException {
        try {
            remainingMillis();
        } catch (SocketTimeoutException timeout) {
            throw new SocketTimeoutException("抖音解析在“" + safeStage(stage) + "”阶段超过总等待时间");
        }
    }

    private static String safeStage(String stage) {
        if (stage == null || stage.isEmpty() || stage.length() > 24) return "当前";
        return stage.replaceAll("[^\\p{L}\\p{N}_ -]", "");
    }
}
