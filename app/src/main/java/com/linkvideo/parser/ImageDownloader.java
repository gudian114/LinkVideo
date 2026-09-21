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
import java.io.OutputStream;
import java.util.Locale;

public final class ImageDownloader {
    public static final class DownloadedFile {
        public final Uri uri;
        public final String displayName;
        public final String displayPath;

        DownloadedFile(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
            this.displayPath = "Pictures/拾链/" + displayName;
        }
    }

    private ImageDownloader() { }

    public static DownloadedFile download(Context context, ImagePostResult post,
                                          ImageResource image, int oneBasedIndex) throws Exception {
        if (context == null || post == null || image == null) throw new Exception("图片信息不存在");
        ImageLoader.Payload payload = new ImageLoader().load(image);
        ImageFormat format = ImageFormat.detect(payload.contentType, payload.finalUrl, payload.bytes);
        String base = post.title == null || post.title.trim().isEmpty()
                ? post.platform + "-图文" : post.title;
        String requestedName = safeName(base) + "-" + String.format(Locale.ROOT, "%02d",
                Math.max(1, oneBasedIndex)) + format.extension;
        ContentResolver resolver = context.getContentResolver();
        Uri output = createOutput(resolver, requestedName, format.mimeType);
        if (output == null) throw new Exception("无法在系统图片目录中创建文件");
        try {
            try (OutputStream target = resolver.openOutputStream(output, "w")) {
                if (target == null) throw new Exception("无法写入图片文件");
                target.write(payload.bytes);
            }
            finishOutput(resolver, output);
            return new DownloadedFile(output, queryDisplayName(resolver, output, requestedName));
        } catch (Exception error) {
            resolver.delete(output, null, null);
            throw error;
        }
    }

    private static Uri createOutput(ContentResolver resolver, String name, String mimeType) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/拾链");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
        File directory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "拾链");
        if (!directory.exists() && !directory.mkdirs()) throw new Exception("无法创建图片目录");
        values.put(MediaStore.MediaColumns.DATA, new File(directory, name).getAbsolutePath());
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private static void finishOutput(ContentResolver resolver, Uri output) {
        if (Build.VERSION.SDK_INT < 29) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(output, values, null, null);
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                null, null, null)) {
            return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private static String safeName(String value) {
        String safe = value == null ? "图片" : value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safe.isEmpty()) safe = "图片";
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }
}
