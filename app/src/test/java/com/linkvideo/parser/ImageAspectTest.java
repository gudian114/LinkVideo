package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageAspectTest {
    @Test public void calculatesPortraitLandscapeAndSquareHeights() {
        assertEquals(600, ImageAspect.heightForWidth(450, 1080, 1440));
        assertEquals(338, ImageAspect.heightForWidth(450, 1440, 1080));
        assertEquals(450, ImageAspect.heightForWidth(450, 1000, 1000));
    }

    @Test public void invalidDimensionsUseSquarePlaceholder() {
        assertEquals(450, ImageAspect.heightForWidth(450, 0, 1440));
        assertEquals(0, ImageAspect.heightForWidth(0, 1080, 1440));
    }

    @Test public void clampsExtremeRatios() {
        assertEquals(1350, ImageAspect.heightForWidth(450, 1, 100));
        assertEquals(150, ImageAspect.heightForWidth(450, 100, 1));
    }
}
