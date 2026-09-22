package com.linkvideo.parser;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

final class PublicDownloadStore {
    static final String FOLDER_IMAGE_POST = "图文";
    static final String FOLDER_VIDEO = "视频";
    static final String FOLDER_COVER = "封面";

    static final class CreatedFile {
        final Uri uri;
        final String requestedName;
        final String folder;

        CreatedFile(Uri uri, String requestedName, String folder) {
            this.uri = uri;
            this.requestedName = requestedName;
            this.folder = folder;
        }
    }

    private PublicDownloadStore() { }

    static CreatedFile create(Context context, String folder, String extension,
            String mimeType, boolean image) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        // MediaStore assigns " (n)" for existing public files, including files from older installs.
        // Directly listing Downloads entries not owned by this install is blocked by scoped storage.
        Collection<String> existing = Build.VERSION.SDK_INT < 29
                ? existingNames(folder) : new ArrayList<>();
        String name = DownloadFileNaming.nextAvailableName(extension, existing);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + folder);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        } else {
            File directory = publicDirectory(folder);
            if (!directory.exists() && !directory.mkdirs()) throw new Exception("无法创建下载目录");
            values.put(MediaStore.MediaColumns.DATA, new File(directory, name).getAbsolutePath());
            uri = resolver.insert(image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        }
        return uri == null ? null : new CreatedFile(uri, name, folder);
    }

    static void finish(ContentResolver resolver, Uri output) {
        if (Build.VERSION.SDK_INT < 29) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(output, values, null, null);
    }

    static String queryDisplayName(ContentResolver resolver, Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    static String displayPath(String folder, String displayName) {
        return "/storage/emulated/0/Download/" + folder + "/" + displayName;
    }

    private static Collection<String> existingNames(String folder) {
        String[] names = publicDirectory(folder).list();
        return names == null ? new ArrayList<>() : Arrays.asList(names);
    }

    private static File publicDirectory(String folder) {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), folder);
    }
}
