package com.linkvideo.parser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class UpdateActivity extends Activity {
    private static final int REQUEST_INSTALL_PERMISSION = 4025;
    private static final String STATE_MANIFEST = "update_manifest";
    private static final String STATE_READY_MESSAGE = "update_ready_message";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final UpdateHttpClient http = new UpdateHttpClient();
    private ProgressBar progress;
    private TextView status;
    private TextView currentVersion;
    private TextView latestVersion;
    private TextView packageSize;
    private TextView releaseNotes;
    private TextView progressDetail;
    private Button primary;
    private Button secondary;
    private UpdateManifest manifest;
    private File verifiedApk;
    private CancellationToken cancellation;
    private Future<?> task;
    private int generation;
    private boolean waitingForPermission;
    private String preservedReadyMessage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_update);
        bindViews();
        applyWindowInsets();
        findViewById(R.id.update_back).setOnClickListener(ignored -> finish());
        showCurrentVersion();
        if (state != null && state.containsKey(STATE_MANIFEST)) {
            try {
                manifest = UpdateManifestParser.parse(state.getString(STATE_MANIFEST, ""));
            } catch (IllegalArgumentException ignored) {
                manifest = null;
            }
        }
        if (state != null) preservedReadyMessage = state.getString(STATE_READY_MESSAGE);
        InstallOutcome.Result outcome = UpdateStateStore.consumeInstallOutcome(this);
        if (outcome != null) {
            showInstallOutcome(outcome);
            return;
        }
        if (!restoreVerifiedDownload(preservedReadyMessage)) {
            if (manifest != null) {
                try {
                    renderManifestDecision(evaluatePolicy(manifest));
                } catch (RuntimeException exception) {
                    renderCheckError(message(exception, R.string.update_verification_failed));
                }
            } else {
                checkForUpdate();
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        InstallOutcome.Result outcome = UpdateStateStore.consumeInstallOutcome(this);
        if (outcome != null) showInstallOutcome(outcome);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL_PERMISSION) return;
        waitingForPermission = false;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            renderReadyPreserving(getString(R.string.update_permission_denied));
        } else {
            verifySavedDownload(true);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (manifest != null) state.putString(STATE_MANIFEST, manifest.rawJson());
        if (preservedReadyMessage != null) state.putString(STATE_READY_MESSAGE, preservedReadyMessage);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        cancelCurrentOperation();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        progress = findViewById(R.id.update_progress);
        status = findViewById(R.id.update_status);
        currentVersion = findViewById(R.id.update_current_version);
        latestVersion = findViewById(R.id.update_latest_version);
        packageSize = findViewById(R.id.update_package_size);
        releaseNotes = findViewById(R.id.update_release_notes);
        progressDetail = findViewById(R.id.update_progress_detail);
        primary = findViewById(R.id.update_primary);
        secondary = findViewById(R.id.update_secondary);
    }

    private void showCurrentVersion() {
        try {
            ApkDescriptor current = ApkArchiveInspector.current(this);
            currentVersion.setText(getString(R.string.update_current_version, current.versionName(), current.versionCode()));
        } catch (RuntimeException exception) {
            currentVersion.setText(getString(R.string.update_current_version_unknown));
        }
    }

    private void checkForUpdate() {
        int operation = beginOperation();
        final CancellationToken operationCancellation = cancellation;
        manifest = null;
        verifiedApk = null;
        preservedReadyMessage = null;
        renderBusy(getString(R.string.update_checking), false);
        task = executor.submit(() -> {
            try {
                UpdateManifest remote = UpdateManifestParser.parse(http.fetchManifest(operationCancellation));
                UpdatePolicy.Decision decision = evaluatePolicy(remote);
                post(operation, () -> {
                    manifest = remote;
                    showManifest(remote);
                    if (decision == UpdatePolicy.Decision.UNSUPPORTED_DEVICE) renderUnsupported();
                    else if (decision == UpdatePolicy.Decision.LATEST) renderLatest();
                    else renderAvailable();
                });
            } catch (Exception exception) {
                post(operation, () -> renderCheckError(message(exception, R.string.update_network_failed)));
            }
        });
    }

    private void downloadUpdate() {
        if (manifest == null) return;
        UpdatePolicy.Decision decision;
        try {
            decision = evaluatePolicy(manifest);
        } catch (RuntimeException exception) {
            renderCheckError(message(exception, R.string.update_verification_failed));
            return;
        }
        if (decision != UpdatePolicy.Decision.UPDATE_AVAILABLE) {
            renderManifestDecision(decision);
            return;
        }
        int operation = beginOperation();
        final UpdateManifest operationManifest = manifest;
        final CancellationToken operationCancellation = cancellation;
        File directory = updateDirectory();
        File partial = new File(directory, "LinkVideo-update-" + operationManifest.versionCode() + ".apk.part");
        File complete = new File(directory, "LinkVideo-update-" + operationManifest.versionCode() + ".apk");
        renderBusy(getString(R.string.update_downloading), true);
        secondary.setVisibility(View.VISIBLE);
        secondary.setText(R.string.update_cancel);
        secondary.setOnClickListener(ignored -> cancelDownload());
        task = executor.submit(() -> {
            try {
                operationCancellation.throwIfCanceled();
                if (partial.exists()) partial.delete();
                http.downloadApk(operationManifest, partial, operationCancellation, value -> post(operation,
                        () -> renderProgress(value)));
                operationCancellation.throwIfCanceled();
                verifyFile(operationManifest, partial, operationCancellation);
                operationCancellation.throwIfCanceled();
                moveAtomically(partial, complete);
                operationCancellation.throwIfCanceled();
                UpdateStateStore.saveVerifiedManifest(this, operationManifest, complete);
                post(operation, () -> {
                    manifest = operationManifest;
                    verifiedApk = complete;
                    renderReady(getString(R.string.update_ready_to_install));
                });
            } catch (Exception exception) {
                if (partial.exists()) partial.delete();
                if (operationCancellation.isCanceled() && complete.exists()) complete.delete();
                post(operation, () -> renderDownloadError(message(exception, R.string.update_download_failed)));
            }
        });
    }

    private void verifyFile(UpdateManifest expected, File apk,
            CancellationToken operationCancellation) throws Exception {
        operationCancellation.throwIfCanceled();
        requireAvailablePolicy(expected);
        operationCancellation.throwIfCanceled();
        if (!Sha256Verifier.matches(apk, expected.sha256())) {
            throw new UpdateFailure(getString(R.string.update_hash_mismatch));
        }
        operationCancellation.throwIfCanceled();
        ApkDescriptor installed = ApkArchiveInspector.current(this);
        ApkDescriptor candidate = ApkArchiveInspector.inspect(this, apk);
        operationCancellation.throwIfCanceled();
        ApkValidationPolicy.ValidationResult result = ApkValidationPolicy.validate(expected,
                candidate, installed.versionCode(), apk.length(), installed.signerDigests());
        if (!result.valid()) throw new UpdateFailure(result.message());
    }

    private boolean restoreVerifiedDownload(String readyMessage) {
        UpdateStateStore.VerifiedDownload saved = UpdateStateStore.loadVerifiedManifest(this);
        if (saved == null || !safeUpdateFileName(saved.fileName)) {
            if (saved != null) UpdateStateStore.clearVerifiedDownload(this);
            return false;
        }
        try {
            UpdateManifest restored = UpdateManifestParser.parse(saved.manifestJson);
            File file = new File(updateDirectory(), saved.fileName);
            if (!restored.sha256().equalsIgnoreCase(saved.sha256) || !file.isFile()) {
                UpdateStateStore.clearVerifiedDownload(this);
                return false;
            }
            UpdatePolicy.Decision decision = evaluatePolicy(restored);
            if (decision != UpdatePolicy.Decision.UPDATE_AVAILABLE) {
                UpdateStateStore.clearVerifiedDownload(this);
                manifest = restored;
                renderManifestDecision(decision);
                return true;
            }
            manifest = restored;
            verifiedApk = file;
            showManifest(restored);
            if (readyMessage == null) verifySavedDownload(false);
            else renderReadyPreserving(readyMessage);
            return true;
        } catch (RuntimeException exception) {
            UpdateStateStore.clearVerifiedDownload(this);
            return false;
        }
    }

    private void verifySavedDownload(boolean installAfter) {
        UpdateStateStore.VerifiedDownload saved = UpdateStateStore.loadVerifiedManifest(this);
        if (saved == null || !safeUpdateFileName(saved.fileName)) {
            UpdateStateStore.clearVerifiedDownload(this);
            renderDownloadError(getString(R.string.update_verified_file_missing));
            return;
        }
        int operation = beginOperation();
        final CancellationToken operationCancellation = cancellation;
        renderBusy(getString(R.string.update_verifying), false);
        task = executor.submit(() -> {
            File file = new File(updateDirectory(), saved.fileName);
            try {
                UpdateManifest restored = UpdateManifestParser.parse(saved.manifestJson);
                if (!restored.sha256().equalsIgnoreCase(saved.sha256)) {
                    throw new UpdateFailure(getString(R.string.update_hash_mismatch));
                }
                verifyFile(restored, file, operationCancellation);
                operationCancellation.throwIfCanceled();
                post(operation, () -> {
                    manifest = restored;
                    verifiedApk = file;
                    showManifest(restored);
                    if (installAfter) continueInstall();
                    else renderReady(getString(R.string.update_ready_to_install));
                });
            } catch (Exception exception) {
                if (!operationCancellation.isCanceled()) UpdateStateStore.clearVerifiedDownload(this);
                post(operation, () -> renderDownloadError(message(exception, R.string.update_verification_failed)));
            }
        });
    }

    private void continueInstall() {
        if (verifiedApk == null || !verifiedApk.isFile()) {
            renderDownloadError(getString(R.string.update_verified_file_missing));
            return;
        }
        UpdatePolicy.Decision decision;
        try {
            decision = evaluatePolicy(manifest);
        } catch (RuntimeException exception) {
            renderDownloadError(message(exception, R.string.update_verification_failed));
            return;
        }
        if (decision != UpdatePolicy.Decision.UPDATE_AVAILABLE) {
            renderManifestDecision(decision);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            waitingForPermission = true;
            status.setText(R.string.update_permission_required);
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQUEST_INSTALL_PERMISSION);
            return;
        }
        commitInstallerAsync();
    }

    private void commitInstallerAsync() {
        int operation = beginOperation();
        final UpdateManifest operationManifest = manifest;
        final File operationApk = verifiedApk;
        final CancellationToken operationCancellation = cancellation;
        renderInstalling();
        task = executor.submit(() -> {
            try {
                operationCancellation.throwIfCanceled();
                requireAvailablePolicy(operationManifest);
                operationCancellation.throwIfCanceled();
                UpdateInstaller.commit(this, operationApk);
            } catch (Exception exception) {
                post(operation, () -> renderReadyPreserving(
                        message(exception, R.string.update_install_failed)));
            }
        });
    }

    private void cancelDownload() {
        cancelCurrentOperation();
        renderDownloadError(getString(R.string.update_download_canceled));
    }

    private int beginOperation() {
        cancelCurrentOperation();
        generation++;
        cancellation = new CancellationToken();
        return generation;
    }

    private void cancelCurrentOperation() {
        generation++;
        if (cancellation != null) cancellation.cancel();
        if (task != null) task.cancel(true);
        cancellation = null;
        task = null;
    }

    private void post(int operation, Runnable action) {
        main.post(() -> {
            if (operation == generation && !isFinishing() && !isDestroyed()) action.run();
        });
    }

    private void renderBusy(String message, boolean determinate) {
        status.setText(message);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(!determinate);
        if (determinate) progress.setProgress(0);
        progressDetail.setVisibility(determinate ? View.VISIBLE : View.GONE);
        primary.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
    }

    private void renderProgress(DownloadTransfer.Progress value) {
        if (value.percent() < 0) {
            progress.setIndeterminate(true);
            progressDetail.setText(getString(R.string.update_downloaded_bytes,
                    humanSize(value.downloadedBytes())));
        } else {
            progress.setIndeterminate(false);
            progress.setProgress(value.percent());
            progressDetail.setText(getString(R.string.update_download_progress,
                    humanSize(value.downloadedBytes()), humanSize(value.expectedBytes()), value.percent()));
        }
    }

    private void renderLatest() {
        hideProgress();
        status.setText(R.string.update_is_latest);
        showPrimary(R.string.update_check_again, ignored -> checkForUpdate());
    }

    private void renderUnsupported() {
        hideProgress();
        status.setText(R.string.update_unsupported_device);
        showPrimary(R.string.update_check_again, ignored -> checkForUpdate());
    }

    private void renderAvailable() {
        showManifest(manifest);
        hideProgress();
        status.setText(R.string.update_available);
        showPrimary(R.string.update_download, ignored -> downloadUpdate());
    }

    private void renderReady(String message) {
        preservedReadyMessage = null;
        hideProgress();
        status.setText(message);
        showPrimary(R.string.update_install, ignored -> verifySavedDownload(true));
    }

    private void renderReadyPreserving(String message) {
        preservedReadyMessage = message;
        hideProgress();
        status.setText(message);
        showPrimary(R.string.update_install, ignored -> verifySavedDownload(true));
    }

    private void renderInstalling() {
        hideProgress();
        status.setText(R.string.update_system_installer);
        primary.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
    }

    private void renderCheckError(String message) {
        hideProgress();
        status.setText(message);
        showPrimary(R.string.update_retry, ignored -> checkForUpdate());
    }

    private void renderDownloadError(String message) {
        hideProgress();
        status.setText(message);
        if (manifest == null) showPrimary(R.string.update_retry, ignored -> checkForUpdate());
        else showPrimary(R.string.update_retry, ignored -> downloadUpdate());
    }

    private void showInstallOutcome(InstallOutcome.Result outcome) {
        hideProgress();
        status.setText(outcome.message());
        if (outcome.kind() == InstallOutcome.Kind.SUCCESS) {
            UpdateStateStore.clearVerifiedDownload(this);
            showPrimary(R.string.update_check_again, ignored -> checkForUpdate());
        } else if (restoreVerifiedDownload(outcome.message())) {
            preservedReadyMessage = outcome.message();
        } else {
            showPrimary(R.string.update_retry, ignored -> checkForUpdate());
        }
    }

    private UpdatePolicy.Decision evaluatePolicy(UpdateManifest expected) {
        ApkDescriptor current = ApkArchiveInspector.current(this);
        return UpdatePolicy.evaluate(expected, getPackageName(), current.versionCode(), Build.VERSION.SDK_INT);
    }

    private void requireAvailablePolicy(UpdateManifest expected) throws UpdateFailure {
        UpdatePolicy.Decision decision;
        try {
            decision = evaluatePolicy(expected);
        } catch (IllegalArgumentException exception) {
            throw new UpdateFailure(exception.getMessage());
        }
        if (decision == UpdatePolicy.Decision.LATEST) {
            throw new UpdateFailure(getString(R.string.update_version_not_higher));
        }
        if (decision == UpdatePolicy.Decision.UNSUPPORTED_DEVICE) {
            throw new UpdateFailure(getString(R.string.update_unsupported_device));
        }
    }

    private void renderManifestDecision(UpdatePolicy.Decision decision) {
        if (decision == UpdatePolicy.Decision.UNSUPPORTED_DEVICE) renderUnsupported();
        else if (decision == UpdatePolicy.Decision.LATEST) renderLatest();
        else renderAvailable();
    }

    private void showManifest(UpdateManifest value) {
        if (value == null) return;
        latestVersion.setText(getString(R.string.update_latest_version,
                value.versionName(), value.versionCode()));
        packageSize.setText(getString(R.string.update_package_size,
                value.sizeBytes() > 0 ? humanSize(value.sizeBytes()) : getString(R.string.update_size_unknown)));
        releaseNotes.setText(value.releaseNotes());
    }

    private void hideProgress() {
        progress.setVisibility(View.GONE);
        progressDetail.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
    }

    private void showPrimary(int text, View.OnClickListener listener) {
        primary.setVisibility(View.VISIBLE);
        primary.setText(text);
        primary.setOnClickListener(listener);
        secondary.setVisibility(View.GONE);
    }

    private File updateDirectory() {
        File directory = new File(getCacheDir(), "updates");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private boolean safeUpdateFileName(String name) {
        return name != null && name.matches("LinkVideo-update-[0-9]+\\.apk");
    }

    private static void moveAtomically(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String message(Exception exception, int fallback) {
        if (exception instanceof UpdateFailure && exception.getMessage() != null) return exception.getMessage();
        String value = exception.getMessage();
        return value == null || value.trim().isEmpty() ? getString(fallback) : value.trim();
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        if (bytes >= 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        View root = findViewById(R.id.update_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private static final class UpdateFailure extends Exception {
        UpdateFailure(String message) { super(message); }
    }
}
