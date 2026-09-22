package com.linkvideo.parser;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.OutputStream;

public final class ImageDownloader {
    public static final class DownloadedFile {
        public final Uri uri;
        public final String displayName;
        public final String displayPath;

        DownloadedFile(Uri uri, String displayName, String folder) {
            this.uri = uri;
            this.displayName = displayName;
            this.displayPath = PublicDownloadStore.displayPath(folder, displayName);
        }
    }

    private ImageDownloader() { }

    public static DownloadedFile download(Context context, ImagePostResult post,
                                          ImageResource image, int oneBasedIndex) throws Exception {
        if (context == null || post == null || image == null) throw new Exception("图片信息不存在");
        ImageLoader.Payload payload = new ImageLoader().load(image);
        ImageFormat format = ImageFormat.detect(payload.contentType, payload.finalUrl, payload.bytes);
        ContentResolver resolver = context.getContentResolver();
        PublicDownloadStore.CreatedFile created = PublicDownloadStore.create(context,
                PublicDownloadStore.FOLDER_IMAGE_POST, format.extension, format.mimeType, true);
        if (created == null) throw new Exception("无法在系统下载目录中创建图片文件");
        Uri output = created.uri;
        try {
            try (OutputStream target = resolver.openOutputStream(output, "w")) {
                if (target == null) throw new Exception("无法写入图片文件");
                target.write(payload.bytes);
            }
            PublicDownloadStore.finish(resolver, output);
            String displayName = PublicDownloadStore.queryDisplayName(
                    resolver, output, created.requestedName);
            return new DownloadedFile(output, displayName, created.folder);
        } catch (Exception error) {
            resolver.delete(output, null, null);
            throw error;
        }
    }

}
