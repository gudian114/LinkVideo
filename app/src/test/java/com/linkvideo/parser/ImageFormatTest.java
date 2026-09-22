package com.linkvideo.parser;

import org.junit.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImageFormatTest {
    @Test public void detectsPngBySignatureWhenCdnReturnsOctetStream() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        ImageFormat format = ImageFormat.detect("application/octet-stream",
                "https://img.example/resource", png);
        assertEquals("image/png", format.mimeType);
        assertEquals(".png", format.extension);
    }

    @Test public void detectsJpegWebpAndGifSignatures() throws Exception {
        assertEquals(".jpg", ImageFormat.detect(null, "https://img.example/a",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}).extension);
        assertEquals(".webp", ImageFormat.detect(null, "https://img.example/a",
                "RIFFxxxxWEBP".getBytes(UTF_8)).extension);
        assertEquals(".gif", ImageFormat.detect(null, "https://img.example/a",
                "GIF89a".getBytes(UTF_8)).extension);
    }

    @Test public void acceptsSupportedImageContentTypeWhenSignatureIsUnavailable() throws Exception {
        assertEquals(".jpg", ImageFormat.detect("image/jpeg; charset=binary",
                "https://img.example/resource", new byte[]{1, 2, 3}).extension);
    }

    @Test public void rejectsHtmlEvenWhenUrlEndsWithJpg() {
        assertThrows(IOException.class, () -> ImageFormat.detect("text/html",
                "https://img.example/fake.jpg", "<html>verify</html>".getBytes(UTF_8)));
    }
}
