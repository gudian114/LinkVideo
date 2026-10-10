package com.linkvideo.parser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadDestinationContractTest {
    @Test public void publicDownloadsAreSeparatedIntoRequestedFolders() throws Exception {
        String store = read("app/src/main/java/com/linkvideo/parser/PublicDownloadStore.java");
        String video = read("app/src/main/java/com/linkvideo/parser/VideoDownloader.java");
        String image = read("app/src/main/java/com/linkvideo/parser/ImageDownloader.java");
        assertTrue(store.contains("FOLDER_IMAGE_POST = \"图文\""));
        assertTrue(store.contains("FOLDER_VIDEO = \"视频\""));
        assertTrue(store.contains("FOLDER_COVER = \"封面\""));
        assertTrue(store.contains("Environment.DIRECTORY_DOWNLOADS"));
        assertTrue(store.contains("/storage/emulated/0/Download/"));
        assertFalse(store.contains("resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI"));
        assertTrue(video.contains("PublicDownloadStore.FOLDER_VIDEO"));
        assertTrue(video.contains("PublicDownloadStore.FOLDER_COVER"));
        assertTrue(image.contains("PublicDownloadStore.FOLDER_IMAGE_POST"));
        assertFalse(video.contains("/拾链"));
        assertFalse(image.contains("/拾链"));
    }

    @Test public void batchHintIsCentered() throws Exception {
        String layout = read("app/src/main/res/layout/activity_batch.xml");
        int status = layout.indexOf("android:id=\"@+id/batch_status\"");
        int end = layout.indexOf("/>", status);
        String statusView = layout.substring(status, end);
        assertTrue(statusView.contains("android:gravity=\"center\""));
    }

    @Test public void v029KeepsOtaEntryAndComponents() throws Exception {
        String build = read("app/build.gradle");
        String manifest = read("app/src/main/AndroidManifest.xml");
        String settings = read("app/src/main/res/layout/activity_settings.xml");
        assertTrue(build.contains("versionCode 29"));
        assertTrue(build.contains("versionName '0.29.0'"));
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertTrue(manifest.contains(".UpdateActivity"));
        assertTrue(manifest.contains(".UpdateInstallReceiver"));
        assertTrue(settings.contains("@+id/check_update"));
    }

    @Test public void v026ReleaseManifestMatchesBuiltApk() throws Exception {
        Path root = projectRoot();
        UpdateManifest manifest = UpdateManifestParser.parse(new String(Files.readAllBytes(
                root.resolve("v0.26/latest.json")), StandardCharsets.UTF_8));
        Path apk = root.resolve("v0.26/LinkVideo-v0.26.apk");
        assertTrue(manifest.versionCode() == 26);
        assertTrue(manifest.sizeBytes() == Files.size(apk));
        assertTrue(Sha256Verifier.matches(apk.toFile(), manifest.sha256()));
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(projectRoot().resolve(relative)), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir"));
        return Files.isDirectory(current.resolve("outputs/LinkVideo"))
                ? current.resolve("outputs/LinkVideo") : current;
    }
}
