package com.linkvideo.parser;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApkValidationPolicyTest {
    private static final long SIZE = 152264L;

    @Test public void acceptsMatchingNewerApk() {
        assertTrue(validate(descriptor("com.linkvideo.parser", 25, "0.25.0", 26, "cert-a"), SIZE).valid());
    }

    @Test public void rejectsFileSizeMismatch() {
        assertInvalid("安装包大小不匹配", descriptor("com.linkvideo.parser", 25, "0.25.0", 26, "cert-a"), SIZE - 1);
    }

    @Test public void acceptsUnknownManifestSize() {
        UpdateManifest unknownSize = UpdateManifestParser.parse(
                UpdateManifestParserTest.validJson(25, 26).replace("152264", "0"));
        assertTrue(ApkValidationPolicy.validate(unknownSize,
                descriptor("com.linkvideo.parser", 25, "0.25.0", 26, "cert-a"), 24, SIZE,
                Collections.singleton("cert-a")).valid());
    }

    @Test public void rejectsWrongPackage() {
        assertInvalid("安装包包名不匹配", descriptor("other.package", 25, "0.25.0", 26, "cert-a"), SIZE);
    }

    @Test public void rejectsManifestAndApkVersionCodeMismatch() {
        assertInvalid("安装包版本与更新清单不一致", descriptor("com.linkvideo.parser", 26, "0.25.0", 26, "cert-a"), SIZE);
    }

    @Test public void rejectsVersionThatDidNotIncrease() {
        assertInvalid("安装包版本没有提高", descriptor("com.linkvideo.parser", 24, "0.25.0", 26, "cert-a"), SIZE);
    }

    @Test public void rejectsVersionNameMismatch() {
        assertInvalid("安装包版本名称不匹配", descriptor("com.linkvideo.parser", 25, "0.25.1", 26, "cert-a"), SIZE);
    }

    @Test public void rejectsMinimumSdkMismatch() {
        assertInvalid("安装包最低系统版本不匹配", descriptor("com.linkvideo.parser", 25, "0.25.0", 27, "cert-a"), SIZE);
    }

    @Test public void rejectsSigningCertificateMismatch() {
        assertInvalid("安装包签名不匹配", descriptor("com.linkvideo.parser", 25, "0.25.0", 26, "cert-b"), SIZE);
    }

    private static ApkValidationPolicy.ValidationResult validate(ApkDescriptor descriptor, long size) {
        UpdateManifest manifest = UpdateManifestParser.parse(UpdateManifestParserTest.validJson(25, 26));
        return ApkValidationPolicy.validate(manifest, descriptor, 24, size,
                Collections.singleton("cert-a"));
    }

    private static void assertInvalid(String message, ApkDescriptor descriptor, long size) {
        ApkValidationPolicy.ValidationResult result = validate(descriptor, size);
        assertFalse(result.valid());
        assertEquals(message, result.message());
    }

    private static ApkDescriptor descriptor(String packageName, long versionCode,
            String versionName, int minSdk, String signer) {
        return new ApkDescriptor(packageName, versionCode, versionName, minSdk,
                Collections.singleton(signer));
    }
}
