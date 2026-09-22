package com.linkvideo.parser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class UpdateUiContractTest {
    @Test public void settingsExposesUpdateEntryAndManifestRegistersUpdateComponents() throws Exception {
        String settings = read("app/src/main/res/layout/activity_settings.xml");
        String manifest = read("app/src/main/AndroidManifest.xml");
        assertTrue(settings.contains("@+id/check_update"));
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertTrue(manifest.contains(".UpdateActivity"));
        assertTrue(manifest.contains(".UpdateInstallReceiver"));
    }

    @Test public void updateScreenContainsRequiredStatusAndActions() throws Exception {
        String layout = read("app/src/main/res/layout/activity_update.xml");
        assertTrue(layout.contains("@+id/update_progress"));
        assertTrue(layout.contains("@+id/update_release_notes"));
        assertTrue(layout.contains("@+id/update_primary"));
        assertTrue(layout.contains("@+id/update_secondary"));
    }

    @Test public void updateCopyCoversRetrySecurityAndInstallerOutcomes() throws Exception {
        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue(strings.contains("name=\"update_check\""));
        assertTrue(strings.contains("name=\"update_retry\""));
        assertTrue(strings.contains("name=\"update_hash_mismatch\""));
        assertTrue(strings.contains("name=\"update_signature_mismatch\""));
        assertTrue(strings.contains("name=\"update_permission_denied\""));
        assertTrue(strings.contains("name=\"update_install_canceled\""));
    }

    @Test public void installerClosesApkStreamsBeforeCommit() throws Exception {
        String installer = read("app/src/main/java/com/linkvideo/parser/UpdateInstaller.java");
        int sessionTry = installer.indexOf("try (PackageInstaller.Session session");
        int streamTry = installer.indexOf("try (InputStream input", sessionTry);
        int commit = installer.indexOf("session.commit(", streamTry);
        int streamClose = installer.indexOf("\n            }\n", streamTry);
        assertTrue(sessionTry >= 0);
        assertTrue(streamTry > sessionTry);
        assertTrue(streamClose > streamTry);
        assertTrue(commit > streamClose);
    }

    @Test public void activityStagesInstallerOffMainThreadAndCapturesDownloadState() throws Exception {
        String activity = read("app/src/main/java/com/linkvideo/parser/UpdateActivity.java");
        assertTrue(activity.contains("final UpdateManifest operationManifest = manifest;"));
        assertTrue(activity.contains("final CancellationToken operationCancellation = cancellation;"));
        assertTrue(count(activity, "operationCancellation.throwIfCanceled();") >= 4);
        assertTrue(activity.contains("private void commitInstallerAsync()"));
        assertTrue(activity.contains("task = executor.submit(() ->"));
    }

    @Test public void activityReevaluatesPolicyWhenRestoringAndVerifying() throws Exception {
        String activity = read("app/src/main/java/com/linkvideo/parser/UpdateActivity.java");
        assertTrue(count(activity, "evaluatePolicy(") >= 4);
        assertTrue(activity.contains("renderManifestDecision(evaluatePolicy(manifest))"));
        assertTrue(activity.contains("requireAvailablePolicy(expected)"));
    }

    @Test public void terminalInstallOutcomeIsRestoredWithoutAsyncStatusOverwrite() throws Exception {
        String activity = read("app/src/main/java/com/linkvideo/parser/UpdateActivity.java");
        assertTrue(activity.contains("restoreVerifiedDownload(outcome.message())"));
        assertFalse(activity.contains("else if (restoreVerifiedDownload())"));
    }

    @Test public void releaseScriptsDoNotRejectLegitimateCancellationTokenSource() throws Exception {
        String packageScript = readFromTaskRoot("work/package-linkvideo-v0.25.ps1");
        String verifyScript = readFromTaskRoot("work/verify-linkvideo-v0.25-archive.ps1");
        String broadRule = "(keystore|local\\.properties|token|password)";
        assertFalse(packageScript.contains(broadRule));
        assertFalse(verifyScript.contains(broadRule));
        assertTrue(packageScript.contains("$sensitiveFilePattern"));
        assertTrue(verifyScript.contains("$sensitiveFilePattern"));
    }

    private static String read(String relative) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir"));
        Path root = Files.isDirectory(current.resolve("outputs/LinkVideo"))
                ? current.resolve("outputs/LinkVideo") : current;
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static String readFromTaskRoot(String relative) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir"));
        Path root = Files.isDirectory(current.resolve("outputs/LinkVideo"))
                ? current : current.getParent().getParent();
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
