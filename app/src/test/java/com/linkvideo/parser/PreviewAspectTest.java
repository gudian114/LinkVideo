package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PreviewAspectTest {
    @Test public void sizesLandscapePreviewFromMediaRatio() {
        assertEquals(405, PreviewAspect.heightForWidth(720, 1920, 1080));
    }

    @Test public void sizesSquarePreviewFromMediaRatio() {
        assertEquals(720, PreviewAspect.heightForWidth(720, 1080, 1080));
    }

    @Test public void expandsPortraitPreviewFromMediaRatio() {
        assertEquals(1280, PreviewAspect.heightForWidth(720, 1080, 1920));
    }

    @Test public void clampsExtremeAndInvalidRatios() {
        assertEquals(405, PreviewAspect.heightForWidth(720, 4000, 100));
        assertEquals(1280, PreviewAspect.heightForWidth(720, 100, 4000));
        assertEquals(405, PreviewAspect.heightForWidth(720, 0, 0));
        assertEquals(0, PreviewAspect.heightForWidth(0, 1080, 1920));
    }
}
