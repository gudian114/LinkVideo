package com.linkvideo.parser;

final class UpdateManifest {
    private final int schemaVersion;
    private final String packageName;
    private final long versionCode;
    private final String versionName;
    private final int minSdk;
    private final String apkUrl;
    private final long sizeBytes;
    private final String sha256;
    private final String releaseNotes;
    private final String rawJson;

    UpdateManifest(int schemaVersion, String packageName, long versionCode, String versionName,
            int minSdk, String apkUrl, long sizeBytes, String sha256, String releaseNotes,
            String rawJson) {
        this.schemaVersion = schemaVersion;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.apkUrl = apkUrl;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.releaseNotes = releaseNotes;
        this.rawJson = rawJson;
    }

    int schemaVersion() { return schemaVersion; }
    String packageName() { return packageName; }
    long versionCode() { return versionCode; }
    String versionName() { return versionName; }
    int minSdk() { return minSdk; }
    String apkUrl() { return apkUrl; }
    long sizeBytes() { return sizeBytes; }
    String sha256() { return sha256; }
    String releaseNotes() { return releaseNotes; }
    String rawJson() { return rawJson; }
}
