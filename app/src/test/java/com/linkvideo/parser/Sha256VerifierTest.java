package com.linkvideo.parser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sha256VerifierTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void hashesKnownVector() throws Exception {
        File file = temporary.newFile("abc.bin");
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Verifier.digest(file));
    }

    @Test public void comparisonAcceptsUppercaseAndRejectsChangedDigest() throws Exception {
        File file = temporary.newFile("payload.bin");
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        String digest = Sha256Verifier.digest(file);
        assertTrue(Sha256Verifier.matches(file, digest.toUpperCase(Locale.ROOT)));
        assertFalse(Sha256Verifier.matches(file, "0" + digest.substring(1)));
    }
}
