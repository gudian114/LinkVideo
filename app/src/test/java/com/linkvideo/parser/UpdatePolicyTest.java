package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class UpdatePolicyTest {
    @Test public void sameVersionIsLatest() {
        assertEquals(UpdatePolicy.Decision.LATEST,
                UpdatePolicy.evaluate(manifest(24, 26), "com.linkvideo.parser", 24, 35));
    }

    @Test public void olderRemoteVersionIsLatest() {
        assertEquals(UpdatePolicy.Decision.LATEST,
                UpdatePolicy.evaluate(manifest(23, 26), "com.linkvideo.parser", 24, 35));
    }

    @Test public void higherRemoteVersionIsAvailable() {
        assertEquals(UpdatePolicy.Decision.UPDATE_AVAILABLE,
                UpdatePolicy.evaluate(manifest(25, 26), "com.linkvideo.parser", 24, 35));
    }

    @Test public void unsupportedDeviceIsReportedBeforeDownload() {
        assertEquals(UpdatePolicy.Decision.UNSUPPORTED_DEVICE,
                UpdatePolicy.evaluate(manifest(25, 36), "com.linkvideo.parser", 24, 35));
    }

    @Test public void wrongPackageIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdatePolicy.evaluate(manifest(25, 26), "another.package", 24, 35));
    }

    private static UpdateManifest manifest(long versionCode, int minSdk) {
        return UpdateManifestParser.parse(UpdateManifestParserTest.validJson(versionCode, minSdk));
    }
}
