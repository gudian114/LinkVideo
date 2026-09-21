package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoverFormatTest {
    @Test public void keepsSupportedImageMimeAndExtension() {
        CoverFormat png = CoverFormat.fromContentType("image/png; charset=binary");
        assertEquals("image/png", png.mimeType);
        assertEquals(".png", png.extension);

        CoverFormat webp = CoverFormat.fromContentType("image/webp");
        assertEquals("image/webp", webp.mimeType);
        assertEquals(".webp", webp.extension);
    }

    @Test public void fallsBackToJpegForMissingOrUnknownTypes() {
        assertEquals(".jpg", CoverFormat.fromContentType(null).extension);
        assertEquals("image/jpeg", CoverFormat.fromContentType("application/octet-stream").mimeType);
    }
}
