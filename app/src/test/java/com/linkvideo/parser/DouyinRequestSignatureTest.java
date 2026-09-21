package com.linkvideo.parser;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class DouyinRequestSignatureTest {
    @Test public void sm3MatchesPublishedAbcVector() {
        byte[] digest = DouyinRequestSignature.sm3("abc".getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", hex.toString());
    }

    @Test public void signatureMatchesIndependentPythonReference() {
        String query = "aid=6383&device_platform=webapp&aweme_id=7685366931792203054";
        String expected = "E7mhBdLkdD2kDDyh5VcLfY3q6fWVYmBN0SVkMD2f0aDOXL39HMY29exoMWzvqY8jFs/jIeEjy4hbT3ohrQ2y0Hwf9W0L/25ksDSkKl5Q5xSSs1X9eghgJ04qmkt5SMx2RvB-rOXmqhZHKRbp09oHmhK4b1dzFgf3qJLzME==";
        assertEquals(expected, DouyinRequestSignature.sign(query, 1789568000000L,
                1789568000006L, new int[]{1234, 2345, 3456}));
    }
}
