package com.linkvideo.parser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImageLoaderTest {
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};

    @Test public void fallsBackAfterForbiddenAndAppliesOnlySerializableResourceHeaders() throws Exception {
        Network network = new Network();
        network.reply("https://img.example/first.jpg", 403, "text/plain", "denied".getBytes(UTF_8));
        Reply second = network.reply("https://backup.example/second.jpg", 200, "image/jpeg", JPEG);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", "https://www.douyin.com/note/1");
        headers.put("User-Agent", "mobile-agent");
        headers.put("Cookie", "ttwid=token");

        ImageLoader.Payload payload = network.loader().load(new ImageResource(Arrays.asList(
                "https://img.example/first.jpg", "https://backup.example/second.jpg"), headers, 0, 0));

        assertArrayEquals(JPEG, payload.bytes);
        assertEquals("https://backup.example/second.jpg", payload.finalUrl);
        assertEquals("https://www.douyin.com/note/1", second.getRequestProperty("Referer"));
        assertEquals("mobile-agent", second.getRequestProperty("User-Agent"));
        assertEquals(null, second.getRequestProperty("Cookie"));
    }

    @Test public void followsRelativeHttpsRedirect() throws Exception {
        Network network = new Network();
        network.reply("https://img.example/start.jpg", 302, null, new byte[0]).location = "/final.jpg";
        network.reply("https://img.example/final.jpg", 200, "image/jpeg", JPEG);
        ImageLoader.Payload payload = network.loader().load(resource("https://img.example/start.jpg"));
        assertEquals("https://img.example/final.jpg", payload.finalUrl);
    }

    @Test public void unsafeRedirectUsesNextCandidate() throws Exception {
        Network network = new Network();
        network.reply("https://img.example/start.jpg", 302, null, new byte[0]).location = "http://img.example/no.jpg";
        network.reply("https://backup.example/final.jpg", 200, "image/jpeg", JPEG);
        ImageLoader.Payload payload = network.loader().load(new ImageResource(Arrays.asList(
                "https://img.example/start.jpg", "https://backup.example/final.jpg"),
                Collections.emptyMap(), 0, 0));
        assertEquals("https://backup.example/final.jpg", payload.finalUrl);
    }

    @Test public void oversizedCandidateAndHtmlCandidateUseBackup() throws Exception {
        Network network = new Network();
        Reply large = network.reply("https://img.example/large.jpg", 200, "image/jpeg", JPEG);
        large.contentLength = 21L * 1024 * 1024;
        network.reply("https://img.example/fake.jpg", 200, "text/html", "<html>verify</html>".getBytes(UTF_8));
        network.reply("https://img.example/good.jpg", 200, "image/jpeg", JPEG);
        ImageLoader.Payload payload = network.loader().load(new ImageResource(Arrays.asList(
                "https://img.example/large.jpg", "https://img.example/fake.jpg", "https://img.example/good.jpg"),
                Collections.emptyMap(), 0, 0));
        assertEquals("https://img.example/good.jpg", payload.finalUrl);
    }

    @Test public void cancellationStartsNoRequest() throws Exception {
        Network network = new Network();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> network.loader().load(resource("https://img.example/a.jpg")));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, network.opened);
        } finally {
            Thread.interrupted();
        }
    }

    private static ImageResource resource(String url) {
        return new ImageResource(Collections.singletonList(url), Collections.emptyMap(), 0, 0);
    }

    private static final class Network {
        final Map<String, Reply> replies = new LinkedHashMap<>();
        int opened;

        Reply reply(String url, int status, String contentType, byte[] body) throws Exception {
            Reply reply = new Reply(new URL(url), status, contentType, body);
            replies.put(url, reply);
            return reply;
        }

        ImageLoader loader() {
            return new ImageLoader(url -> {
                opened++;
                Reply reply = replies.get(url.toString());
                if (reply == null) throw new AssertionError("Unexpected image request: " + url);
                return reply;
            });
        }
    }

    private static final class Reply extends HttpURLConnection {
        final int status;
        final String contentType;
        final byte[] body;
        String location;
        long contentLength = -1;

        Reply(URL url, int status, String contentType, byte[] body) {
            super(url);
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public String getContentType() { return contentType; }
        @Override public long getContentLengthLong() { return contentLength >= 0 ? contentLength : body.length; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
    }
}
