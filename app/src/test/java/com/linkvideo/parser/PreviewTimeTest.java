package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PreviewTimeTest {
    @Test public void showsElapsedAndTotalTime() {
        assertEquals("0:07 / 0:31", PreviewTime.format(7_400, 31_900));
    }

    @Test public void supportsVideosLongerThanOneHour() {
        assertEquals("1:02:03 / 2:00:00", PreviewTime.format(3_723_000, 7_200_000));
    }

    @Test public void clampsInvalidAndOverrunPositions() {
        assertEquals("0:00 / 0:00", PreviewTime.format(-1, -1));
        assertEquals("0:31 / 0:31", PreviewTime.format(40_000, 31_000));
    }
}
