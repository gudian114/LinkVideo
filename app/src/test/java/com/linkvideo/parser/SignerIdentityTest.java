package com.linkvideo.parser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignerIdentityTest {
    @Test public void hashesCertificateBytesWithSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SignerIdentity.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void acceptsExactSingleAndMultipleSignerSetsIgnoringHexCase() {
        assertTrue(SignerIdentity.sameActiveSigners(Collections.singleton("AA"), Collections.singleton("aa")));
        assertTrue(SignerIdentity.sameActiveSigners(
                new HashSet<>(Arrays.asList("aa", "bb")),
                new HashSet<>(Arrays.asList("BB", "AA"))));
    }

    @Test public void rejectsEmptyDifferentSubsetAndSupersetSignerSets() {
        assertFalse(SignerIdentity.sameActiveSigners(Collections.emptySet(), Collections.emptySet()));
        assertFalse(SignerIdentity.sameActiveSigners(Collections.singleton("aa"), Collections.singleton("bb")));
        assertFalse(SignerIdentity.sameActiveSigners(
                new HashSet<>(Arrays.asList("aa", "bb")), Collections.singleton("aa")));
        assertFalse(SignerIdentity.sameActiveSigners(
                Collections.singleton("aa"), new HashSet<>(Arrays.asList("aa", "bb"))));
    }
}
