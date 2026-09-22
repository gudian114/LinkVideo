package com.linkvideo.parser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpdateHttpClientTest {
    @Test public void acceptsHttpsAndResolvesHttpsRedirect() throws Exception {
        URL source = UpdateHttpClient.requireHttps(new URL("https://github.com/release"));
        URL redirected = UpdateHttpClient.resolveRedirect(source, "/asset/latest.json");
        assertEquals("https", redirected.getProtocol());
        assertEquals("github.com", redirected.getHost());
    }

    @Test public void rejectsInitialHttpAndRedirectDowngrade() throws Exception {
        assertThrows(IOException.class,
                () -> UpdateHttpClient.requireHttps(new URL("http://github.com/release")));
        assertThrows(IOException.class,
                () -> UpdateHttpClient.resolveRedirect(new URL("https://github.com/release"), "http://example.com/file"));
    }

    @Test public void rejectsMissingRedirectLocation() throws Exception {
        assertThrows(IOException.class,
                () -> UpdateHttpClient.resolveRedirect(new URL("https://github.com/release"), "  "));
    }

    @Test public void readsBoundedUtf8Manifest() throws Exception {
        byte[] value = "{\"版本\":25}".getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"版本\":25}", UpdateHttpClient.readUtf8(
                new ByteArrayInputStream(value), value.length, new CancellationToken()));
    }

    @Test public void rejectsOversizedManifestWhileReading() {
        IOException error = assertThrows(IOException.class, () -> UpdateHttpClient.readUtf8(
                new ByteArrayInputStream(new byte[20]), 10, new CancellationToken()));
        assertTrue(error.getMessage().contains("过大"));
    }
}
