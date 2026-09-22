package com.linkvideo.parser;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class UpdateInstaller {
    static final String ACTION_INSTALL_RESULT = "com.linkvideo.parser.UPDATE_INSTALL_RESULT";

    private UpdateInstaller() { }

    static int commit(Context context, File apk) throws IOException {
        if (apk == null || !apk.isFile()) throw new IOException("安装包不存在");
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk);
                    OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                }
                session.fsync(output);
            }
            Intent result = new Intent(context, UpdateInstallReceiver.class)
                    .setAction(ACTION_INSTALL_RESULT)
                    .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent callback = PendingIntent.getBroadcast(context, sessionId, result, flags);
            session.commit(callback.getIntentSender());
            committed = true;
            return sessionId;
        } finally {
            if (!committed) installer.abandonSession(sessionId);
        }
    }
}
