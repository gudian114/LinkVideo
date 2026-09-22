package com.linkvideo.parser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class ApkDescriptor {
    private final String packageName;
    private final long versionCode;
    private final String versionName;
    private final int minSdk;
    private final Set<String> signerDigests;

    ApkDescriptor(String packageName, long versionCode, String versionName, int minSdk,
            Set<String> signerDigests) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.signerDigests = signerDigests == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(signerDigests));
    }

    String packageName() { return packageName; }
    long versionCode() { return versionCode; }
    String versionName() { return versionName; }
    int minSdk() { return minSdk; }
    Set<String> signerDigests() { return signerDigests; }
}
