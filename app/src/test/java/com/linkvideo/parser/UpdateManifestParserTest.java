package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpdateManifestParserTest {
    @Test public void parsesCompleteManifest() {
        UpdateManifest value = UpdateManifestParser.parse(validJson(25, 26));
        assertEquals(1, value.schemaVersion());
        assertEquals("com.linkvideo.parser", value.packageName());
        assertEquals(25L, value.versionCode());
        assertEquals("0.25.0", value.versionName());
        assertEquals(26, value.minSdk());
        assertEquals(152264L, value.sizeBytes());
        assertTrue(value.apkUrl().startsWith("https://"));
        assertEquals(hash(), value.sha256());
        assertEquals("安全更新", value.releaseNotes());
        assertTrue(value.rawJson().contains("schemaVersion"));
    }

    @Test public void allowsZeroSizeForUnknownLength() {
        UpdateManifest value = UpdateManifestParser.parse(validJson(25, 26).replace("152264", "0"));
        assertEquals(0L, value.sizeBytes());
    }

    @Test public void rejectsMissingRequiredField() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace("\"packageName\":\"com.linkvideo.parser\",", "")));
    }

    @Test public void rejectsWrongFieldType() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace("\"versionCode\":25", "\"versionCode\":\"25\"")));
    }

    @Test public void rejectsUnknownSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
    }

    @Test public void rejectsHttpApkUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace("https://github.com", "http://github.com")));
    }

    @Test public void rejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace("152264", "-1")));
    }

    @Test public void rejectsInvalidHash() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(validJson(25, 26).replace(hash(), "abc")));
    }

    @Test public void rejectsMalformedJsonWithUserSafeReason() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> UpdateManifestParser.parse("{not-json"));
        assertTrue(error.getMessage().contains("更新清单"));
    }

    static String validJson(long versionCode, int minSdk) {
        return "{"
                + "\"schemaVersion\":1,"
                + "\"packageName\":\"com.linkvideo.parser\","
                + "\"versionCode\":" + versionCode + ","
                + "\"versionName\":\"0.25.0\","
                + "\"minSdk\":" + minSdk + ","
                + "\"apkUrl\":\"https://github.com/gudian114/LinkVideo/releases/download/v0.25.0/LinkVideo-v0.25.apk\","
                + "\"sizeBytes\":152264,"
                + "\"sha256\":\"" + hash() + "\","
                + "\"releaseNotes\":\"安全更新\""
                + "}";
    }

    private static String hash() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }
}
