package com.linkvideo.parser;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinTimingTest {
    @Test public void remainingBudgetUsesMonotonicClockAndRoundsUp() throws Exception {
        AtomicLong nanos = new AtomicLong(10_000_000L);
        DouyinTiming timing = new DouyinTiming(nanos::get, 90_000L);
        assertEquals(90_000L, timing.remainingMillis());
        nanos.addAndGet(89_999_500_000L);
        assertEquals(1L, timing.remainingMillis());
        timing.check("网页回退");
    }

    @Test public void expiredBudgetReportsTheCurrentSafeStage() {
        AtomicLong nanos = new AtomicLong(0L);
        DouyinTiming timing = new DouyinTiming(nanos::get, 1_000L);
        nanos.set(1_000_000_000L);
        SocketTimeoutException error = assertThrows(SocketTimeoutException.class,
                () -> timing.check("原生详情"));
        assertTrue(error.getMessage().contains("原生详情"));
        assertFalse(error.getMessage().contains("http"));
    }
}
