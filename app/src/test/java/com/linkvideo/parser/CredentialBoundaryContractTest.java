package com.linkvideo.parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CredentialBoundaryContractTest {
    @Test public void runtimeCookiesStayInsideRedirectAwareConnections() throws Exception {
        String main = read("MainActivity.java");
        String batch = read("BatchVideoCard.java");

        assertFalse(main.contains("previewVideo.setVideoURI(Uri.parse(stream.url),\n"
                + "                DouyinRuntimeHeaders.forRequest"));
        assertFalse(batch.contains("video.setVideoURI(Uri.parse(stream.url),\n"
                + "                    DouyinRuntimeHeaders.forRequest"));
        assertFalse(main.contains("for (java.util.Map.Entry<String, String> entry : DouyinRuntimeHeaders.forRequest"));
        assertFalse(batch.contains("for (java.util.Map.Entry<String, String> header : DouyinRuntimeHeaders.forRequest"));

        assertTrue(main.contains("new CoverLoader(DouyinRuntimeHeaders::forRequest)"));
        assertTrue(batch.contains("new CoverLoader(DouyinRuntimeHeaders::forRequest)"));
    }

    @Test public void browserVisibilityRequiresExplicitOrDetectedInteraction() throws Exception {
        String android = read("AndroidHttpClient.java");

        assertFalse(android.contains("scriptError || requestTimeout || status == 403"));
        assertTrue(android.contains("visibility.observeInteraction("));
        assertTrue(android.contains("result.optBoolean(\"interactionRequired\")"));
    }

    private static String read(String file) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        if (Files.isDirectory(root.resolve("outputs/LinkVideo"))) root = root.resolve("outputs/LinkVideo");
        return new String(Files.readAllBytes(root.resolve(
                "app/src/main/java/com/linkvideo/parser/" + file)), StandardCharsets.UTF_8);
    }
}
