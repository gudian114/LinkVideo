package com.linkvideo.parser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

final class ApkArchiveInspector {
    private ApkArchiveInspector() { }

    static ApkDescriptor inspect(Context context, File apk) {
        if (apk == null || !apk.isFile()) throw new IllegalArgumentException("安装包不存在");
        PackageInfo info = archiveInfo(context.getPackageManager(), apk);
        if (info == null || info.applicationInfo == null) {
            throw new IllegalArgumentException("无法读取安装包信息");
        }
        return descriptor(info);
    }

    static ApkDescriptor current(Context context) {
        try {
            PackageInfo info = installedInfo(context.getPackageManager(), context.getPackageName());
            return descriptor(info);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IllegalStateException("无法读取当前应用信息", exception);
        }
    }

    private static PackageInfo archiveInfo(PackageManager manager, File apk) {
        int flags = signingFlags();
        if (Build.VERSION.SDK_INT >= 33) {
            return manager.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(flags));
        }
        return manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    }

    private static PackageInfo installedInfo(PackageManager manager, String packageName)
            throws PackageManager.NameNotFoundException {
        int flags = signingFlags();
        if (Build.VERSION.SDK_INT >= 33) {
            return manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags));
        }
        return manager.getPackageInfo(packageName, flags);
    }

    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    private static ApkDescriptor descriptor(PackageInfo info) {
        if (info == null || info.applicationInfo == null || info.packageName == null) {
            throw new IllegalArgumentException("无法读取安装包信息");
        }
        long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        int minSdk = info.applicationInfo.minSdkVersion;
        return new ApkDescriptor(info.packageName, versionCode,
                info.versionName == null ? "" : info.versionName, minSdk, signers(info));
    }

    private static Set<String> signers(PackageInfo info) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                if (signature != null) result.add(SignerIdentity.sha256(signature.toByteArray()));
            }
        }
        return result;
    }
}
