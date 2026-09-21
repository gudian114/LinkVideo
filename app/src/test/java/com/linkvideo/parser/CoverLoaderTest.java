package com.linkvideo.parser;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class CoverLoaderTest {
    private static final String FIRST = "https://image.example/first.jpg";
    private static final String SECOND = "https://backup.example/second.jpg";
    private static final String SOURCE = "https://www.douyin.com/video/123";

    @Test public void fallsBackWhenFirstCoverReturnsForbidden() throws Exception {
        Network net = new Network();
        Reply denied = net.reply(FIRST, 403, "denied");
        net.reply(SECOND, 200, "image");
        assertEquals("image", net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
        assertTrue(denied.closed);
    }

    @Test public void decodingFailureAlsoTriesNextCover() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "broken");
        net.reply(SECOND, 200, "image");
        assertEquals("image", net.loader().load(result(FIRST, SECOND), bytes -> {
            String text = decode(bytes);
            if (!"image".equals(text)) throw new IOException("unsupported/corrupt image");
            return text;
        }));
    }

    @Test public void nullDecodedBitmapAlsoTriesNextCover() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "broken");
        net.reply(SECOND, 200, "image");
        assertEquals("image", net.loader().load(result(FIRST, SECOND),
                bytes -> "image".equals(decode(bytes)) ? "image" : null));
    }

    @Test public void continuousSmallReadsCannotKeepSlowCoverAliveForever() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "slow").trickle = true;
        net.reply(SECOND, 200, "fast");
        assertEquals("fast", net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
    }

    @Test public void slowFirstHostDoesNotPreventUsingFastBackup() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "slow").delayMillis = 5000;
        net.reply(SECOND, 200, "fast");
        assertEquals("fast", net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
    }

    @Test public void stopsRequestingMoreHostsAfterOverallBudget() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "slow").delayMillis = 13000;
        net.reply(SECOND, 200, "fast");
        assertThrows(SocketTimeoutException.class,
                () -> net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
        assertEquals(1, net.opened);
    }

    @Test public void sendsImageAcceptAndRefererWithoutForwardingSessionCookies() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "image").requiresHeaders = true;
        assertEquals("image", net.loader().load(result(FIRST), CoverLoaderTest::decode));
    }

    @Test public void acceptsCoverLargerThanOldThreeMegabyteLimit() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 200, "").body = new byte[4 * 1024 * 1024];
        assertEquals(Integer.valueOf(4 * 1024 * 1024),
                net.loader().load(result(FIRST), bytes -> bytes.length));
    }

    @Test public void rejectsOversizedUnknownLengthBodyAndUsesBackup() throws Exception {
        Network net = new Network();
        Reply large = net.reply(FIRST, 200, "");
        large.body = new byte[9 * 1024 * 1024];
        large.unknownLength = true;
        net.reply(SECOND, 200, "image");
        assertEquals("image", net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
    }

    @Test public void followsRelativeHttpsRedirect() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 302, "").location = "/redirected.jpg";
        net.reply("https://image.example/redirected.jpg", 200, "image");
        assertEquals("image", net.loader().load(result(FIRST), CoverLoaderTest::decode));
    }

    @Test public void unsafeRedirectIsNeverFetchedAndBackupStillLoads() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 302, "").location = "http://image.example/insecure.jpg";
        net.reply(SECOND, 200, "image");
        assertEquals("image", net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
        assertEquals(2, net.opened);
    }

    @Test public void cancellationDoesNotStartNetworkRequests() throws Exception {
        Network net = new Network();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> net.loader().load(result(FIRST), CoverLoaderTest::decode));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, net.opened);
        } finally { Thread.interrupted(); }
    }

    @Test public void rejectsFailureOfEveryCoverInsteadOfReturningEmptySuccess() throws Exception {
        Network net = new Network();
        net.reply(FIRST, 403, "denied");
        net.reply(SECOND, 404, "missing");
        assertThrows(IOException.class, () -> net.loader().load(result(FIRST, SECOND), CoverLoaderTest::decode));
    }

    private static VideoResult result(String... urls) {
        return new VideoResult("抖音", "title", "author", Arrays.asList(urls), SOURCE,
                "", 1, Collections.singletonList(new MediaStream("https://video.example/a.mp4", "", "combined",
                "video/mp4", Collections.singletonMap("Cookie", "ttwid=not-for-image-host"))));
    }

    private static String decode(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }

    // Replace only external network I/O; loader, byte limits, time budgets and decoder are exercised.
    private static final class Network {
        final AtomicLong nanos = new AtomicLong();
        final Map<String, Reply> replies = new LinkedHashMap<>();
        int opened;
        Reply reply(String url, int status, String body) throws Exception {
            Reply r = new Reply(new URL(url), status, body, nanos);
            replies.put(url, r);
            return r;
        }
        CoverLoader loader() {
            return new CoverLoader(url -> {
                opened++;
                Reply r = replies.get(url.toString());
                if (r == null) throw new AssertionError("Unexpected cover request: " + url);
                return r;
            }, nanos::get);
        }
    }

    private static final class Reply extends HttpURLConnection {
        byte[] body;
        final int status;
        final AtomicLong nanos;
        boolean closed, unknownLength, requiresHeaders, trickle;
        String location;
        int delayMillis;
        Reply(URL url, int status, String body, AtomicLong nanos) {
            super(url);
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.nanos = nanos;
        }
        @Override public void connect() { }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() {
            nanos.addAndGet(delayMillis * 1_000_000L);
            if (requiresHeaders && (!SOURCE.equals(getRequestProperty("Referer"))
                    || getRequestProperty("Accept") == null || getRequestProperty("Cookie") != null)) return 403;
            return status;
        }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public long getContentLengthLong() { return unknownLength ? -1 : body.length; }
        @Override public InputStream getInputStream() {
            if (!trickle) return new ByteArrayInputStream(body);
            return new InputStream() {
                @Override public int read() { nanos.addAndGet(2_000_000_000L); return 1; }
                @Override public int read(byte[] b) { b[0] = (byte) read(); return 1; }
            };
        }
    }
}
