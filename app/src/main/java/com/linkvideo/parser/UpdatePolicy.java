package com.linkvideo.parser;

final class UpdatePolicy {
    enum Decision { LATEST, UPDATE_AVAILABLE, UNSUPPORTED_DEVICE }

    private UpdatePolicy() { }

    static Decision evaluate(UpdateManifest manifest, String expectedPackage,
            long currentVersionCode, int deviceSdk) {
        if (manifest == null || !manifest.packageName().equals(expectedPackage)) {
            throw new IllegalArgumentException("更新清单包名不匹配");
        }
        if (manifest.minSdk() > deviceSdk) return Decision.UNSUPPORTED_DEVICE;
        if (manifest.versionCode() <= currentVersionCode) return Decision.LATEST;
        return Decision.UPDATE_AVAILABLE;
    }
}
