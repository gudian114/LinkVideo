package com.linkvideo.parser;

import java.util.Set;

final class ApkValidationPolicy {
    static final class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static ValidationResult success() { return new ValidationResult(true, ""); }
        static ValidationResult invalid(String message) { return new ValidationResult(false, message); }
        boolean valid() { return valid; }
        String message() { return message; }
    }

    private ApkValidationPolicy() { }

    static ValidationResult validate(UpdateManifest manifest, ApkDescriptor apk,
            long currentVersionCode, long actualSize, Set<String> installedSigners) {
        if (manifest == null || apk == null) return ValidationResult.invalid("无法读取安装包信息");
        if (manifest.sizeBytes() > 0 && manifest.sizeBytes() != actualSize) {
            return ValidationResult.invalid("安装包大小不匹配");
        }
        if (!manifest.packageName().equals(apk.packageName())) {
            return ValidationResult.invalid("安装包包名不匹配");
        }
        if (apk.versionCode() <= currentVersionCode) {
            return ValidationResult.invalid("安装包版本没有提高");
        }
        if (manifest.versionCode() != apk.versionCode()) {
            return ValidationResult.invalid("安装包版本与更新清单不一致");
        }
        if (!manifest.versionName().equals(apk.versionName())) {
            return ValidationResult.invalid("安装包版本名称不匹配");
        }
        if (manifest.minSdk() != apk.minSdk()) {
            return ValidationResult.invalid("安装包最低系统版本不匹配");
        }
        if (!SignerIdentity.sameActiveSigners(installedSigners, apk.signerDigests())) {
            return ValidationResult.invalid("安装包签名不匹配");
        }
        return ValidationResult.success();
    }
}
