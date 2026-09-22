package com.linkvideo.parser;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

final class UpdateStateStore {
    static final class VerifiedDownload {
        final String manifestJson;
        final String fileName;
        final String sha256;

        VerifiedDownload(String manifestJson, String fileName, String sha256) {
            this.manifestJson = manifestJson;
            this.fileName = fileName;
            this.sha256 = sha256;
        }
    }

    private static final String PREFERENCES = "linkvideo_update";
    private static final String MANIFEST = "verified_manifest";
    private static final String FILE_NAME = "verified_file";
    private static final String SHA256 = "verified_sha256";
    private static final String OUTCOME_KIND = "install_outcome_kind";
    private static final String OUTCOME_MESSAGE = "install_outcome_message";

    private UpdateStateStore() { }

    static void saveVerifiedManifest(Context context, UpdateManifest manifest, File apk) {
        preferences(context).edit()
                .putString(MANIFEST, manifest.rawJson())
                .putString(FILE_NAME, apk.getName())
                .putString(SHA256, manifest.sha256())
                .apply();
    }

    static VerifiedDownload loadVerifiedManifest(Context context) {
        SharedPreferences values = preferences(context);
        String manifest = values.getString(MANIFEST, "");
        String fileName = values.getString(FILE_NAME, "");
        String sha256 = values.getString(SHA256, "");
        if (manifest.isEmpty() || fileName.isEmpty() || sha256.isEmpty()) return null;
        return new VerifiedDownload(manifest, fileName, sha256);
    }

    static void clearVerifiedDownload(Context context) {
        preferences(context).edit().remove(MANIFEST).remove(FILE_NAME).remove(SHA256).apply();
    }

    static void saveInstallOutcome(Context context, InstallOutcome.Result result) {
        preferences(context).edit().putString(OUTCOME_KIND, result.kind().name())
                .putString(OUTCOME_MESSAGE, result.message()).apply();
    }

    static InstallOutcome.Result consumeInstallOutcome(Context context) {
        SharedPreferences values = preferences(context);
        String kind = values.getString(OUTCOME_KIND, "");
        String message = values.getString(OUTCOME_MESSAGE, "");
        if (kind.isEmpty() || message.isEmpty()) return null;
        values.edit().remove(OUTCOME_KIND).remove(OUTCOME_MESSAGE).apply();
        try {
            return new InstallOutcome.Result(InstallOutcome.Kind.valueOf(kind), message);
        } catch (IllegalArgumentException exception) {
            return new InstallOutcome.Result(InstallOutcome.Kind.FAILED,
                    "安装结果无法识别，当前版本未改变");
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
