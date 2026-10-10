package com.linkvideo.parser;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class VideoDownloader {
    private static final long MAX_COVER_BYTES = 20L * 1024 * 1024;
    public interface Progress { void update(DownloadProgress progress); }

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

    private VideoDownloader() { }

    public static DownloadedFile download(Context context, VideoResult result, Progress progress) throws Exception {
        DownloadPlan plan = DownloadPlan.create(result);
        ContentResolver resolver = context.getContentResolver();
        PublicDownloadStore.CreatedFile created = PublicDownloadStore.create(context,
                PublicDownloadStore.FOLDER_VIDEO, ".mp4", "video/mp4", false);
        if (created == null) throw new Exception("无法在系统下载目录中创建文件");
        Uri output = created.uri;
        try {
            if (plan.requiresMux()) {
                File video = downloadTemp(context, plan.video, progress, "正在下载 B站 H.264 画面…");
                try {
                    File audio = downloadTemp(context, plan.audio, progress, "正在下载 B站 AAC 音频…");
                    try {
                        update(progress, "正在封装 MP4，请保持应用在前台…", 0, 0);
                        muxToMp4(resolver, output, video, audio);
                    } finally {
                        deleteQuietly(audio);
                    }
                } finally {
                    deleteQuietly(video);
                }
            } else {
                try (OutputStream target = resolver.openOutputStream(output, "w")) {
                    if (target == null) throw new Exception("无法写入下载文件");
                    download(plan.video, target, progress, "正在下载 MP4，请保持应用在前台…");
                }
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

    public static DownloadedFile downloadCover(Context context, VideoResult result) throws Exception {
        if (result == null || result.coverUrl == null || result.coverUrl.isEmpty()) {
            throw new Exception("该视频没有可下载的封面");
        }
        URL current = checkedUrl(result.coverUrl);
        for (int hop = 0; hop <= 5; hop++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            Map<String, String> baseHeaders = new LinkedHashMap<>();
            baseHeaders.put("Accept", "image/*");
            baseHeaders.put("Accept-Encoding", "identity");
            baseHeaders.put("User-Agent", "Mozilla/5.0 (Android) LinkVideo/1.0");
            if (result.sourceUrl != null && !result.sourceUrl.isEmpty()) {
                baseHeaders.put("Referer", result.sourceUrl);
            }
            for (Map.Entry<String, String> header : DouyinRuntimeHeaders.forRequest(
                    current.toString(), baseHeaders).entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == 5) throw new Exception("封面地址跳转失败");
                    current = checkedUrl(new URL(current, location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new Exception("视频封面下载失败（HTTP " + status + "）");
                }
                long size = connection.getContentLengthLong();
                if (size > MAX_COVER_BYTES) throw new Exception("视频封面文件过大");
                String mime = imageMime(connection.getContentType());
                ContentResolver resolver = context.getContentResolver();
                PublicDownloadStore.CreatedFile created = PublicDownloadStore.create(context,
                        PublicDownloadStore.FOLDER_COVER, imageExtension(mime), mime, true);
                if (created == null) throw new Exception("无法在系统下载目录中创建封面文件");
                Uri output = created.uri;
                try {
                    try (InputStream input = connection.getInputStream();
                            OutputStream target = resolver.openOutputStream(output, "w")) {
                        if (target == null) throw new Exception("无法写入视频封面");
                        byte[] buffer = new byte[32 * 1024];
                        long written = 0;
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new Exception("封面下载已取消");
                            written += count;
                            if (written > MAX_COVER_BYTES) throw new Exception("视频封面文件过大");
                            target.write(buffer, 0, count);
                        }
                    }
                    PublicDownloadStore.finish(resolver, output);
                    String displayName = PublicDownloadStore.queryDisplayName(
                            resolver, output, created.requestedName);
                    return new DownloadedFile(output, displayName, created.folder);
                } catch (Exception error) {
                    resolver.delete(output, null, null);
                    throw error;
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("封面地址跳转次数过多");
    }

    private static String imageMime(String raw) throws Exception {
        String mime = raw == null ? "" : raw.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        if ("image/jpg".equals(mime)) return "image/jpeg";
        if ("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime)
                || "image/gif".equals(mime) || "image/avif".equals(mime)) return mime;
        throw new Exception("平台返回的封面不是支持的图片格式");
    }

    private static String imageExtension(String mime) {
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        if ("image/avif".equals(mime)) return ".avif";
        return ".jpg";
    }

    private static File downloadTemp(Context context, MediaStream stream, Progress progress,
                                     String message) throws Exception {
        File file = new File(context.getCacheDir(), "track-" + UUID.randomUUID() + ".m4s");
        try (OutputStream output = new FileOutputStream(file)) {
            download(stream, output, progress, message);
        } catch (Exception error) {
            deleteQuietly(file);
            throw error;
        }
        return file;
    }

    private static void download(MediaStream stream, OutputStream output, Progress progress,
                                 String message) throws Exception {
        URL current = checkedUrl(stream.url);
        for (int hop = 0; hop <= 5; hop++) {
            if (Thread.currentThread().isInterrupted()) throw new Exception("下载已取消");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            for (Map.Entry<String, String> header : DouyinRuntimeHeaders.forRequest(
                    current.toString(), stream.headers).entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == 5) throw new Exception("媒体地址跳转失败");
                    current = checkedUrl(new URL(current, location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new Exception("媒体下载失败（HTTP " + status + "），请重新解析后再试");
                }
                long total = connection.getContentLengthLong();
                long downloaded = 0;
                long lastUpdateTime = 0;
                update(progress, message, 0, total);
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new Exception("下载已取消");
                        output.write(buffer, 0, count);
                        downloaded += count;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime >= 200) {
                            update(progress, message, downloaded, total);
                            lastUpdateTime = now;
                        }
                    }
                }
                update(progress, message, downloaded, total > 0 ? total : downloaded);
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("媒体地址跳转次数过多");
    }

    private static void update(Progress progress, String message, long downloaded, long total) {
        if (progress != null) progress.update(new DownloadProgress(message, downloaded, total));
    }

    private static URL checkedUrl(String value) throws Exception {
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new Exception("媒体地址不是安全的 HTTPS 地址");
        }
        return uri.toURL();
    }

    private static void muxToMp4(ContentResolver resolver, Uri output, File video, File audio) throws Exception {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        ParcelFileDescriptor descriptor = null;
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            videoExtractor.setDataSource(video.getAbsolutePath());
            audioExtractor.setDataSource(audio.getAbsolutePath());
            int videoInput = findTrack(videoExtractor, "video/");
            int audioInput = findTrack(audioExtractor, "audio/");
            if (videoInput < 0 || audioInput < 0) throw new Exception("下载的 B站轨道不是可识别的 MP4 媒体");
            descriptor = resolver.openFileDescriptor(output, "rw");
            if (descriptor == null) throw new Exception("无法创建 MP4 文件");
            muxer = new MediaMuxer(descriptor.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoOutput = muxer.addTrack(videoExtractor.getTrackFormat(videoInput));
            int audioOutput = muxer.addTrack(audioExtractor.getTrackFormat(audioInput));
            muxer.start();
            started = true;
            copyTrack(videoExtractor, videoInput, muxer, videoOutput);
            copyTrack(audioExtractor, audioInput, muxer, audioOutput);
        } finally {
            if (muxer != null) {
                if (started) muxer.stop();
                muxer.release();
            }
            if (descriptor != null) descriptor.close();
            videoExtractor.release();
            audioExtractor.release();
        }
    }

    private static int findTrack(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    private static void copyTrack(MediaExtractor extractor, int inputTrack, MediaMuxer muxer,
                                  int outputTrack) throws Exception {
        extractor.selectTrack(inputTrack);
        MediaFormat format = extractor.getTrackFormat(inputTrack);
        int size = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 1024 * 1024;
        size = Math.max(1024 * 1024, Math.min(size, 16 * 1024 * 1024));
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new Exception("MP4 封装已取消");
            info.offset = 0;
            info.size = extractor.readSampleData(buffer, 0);
            if (info.size < 0) break;
            info.presentationTimeUs = extractor.getSampleTime();
            int sampleFlags = extractor.getSampleFlags();
            info.flags = 0;
            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            muxer.writeSampleData(outputTrack, buffer, info);
            extractor.advance();
        }
        extractor.unselectTrack(inputTrack);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
