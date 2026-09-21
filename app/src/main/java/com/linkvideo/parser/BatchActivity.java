package com.linkvideo.parser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class BatchActivity extends Activity {
    private static final String SAVED_BATCH = "batch_state";
    private static final String SAVED_DOWNLOADS = "pending_batch_downloads";
    private static final String SAVED_COVER_DOWNLOAD = "pending_batch_cover";
    private static final int REQUEST_STORAGE = 301;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService coverExecutor = Executors.newFixedThreadPool(2);
    private final List<Row> rows = new ArrayList<>();
    private BatchState batch;
    private LinearLayout entries;
    private Button parseAll;
    private Button downloadAll;
    private Button clearAll;
    private TextView status;
    private View normalHeader;
    private View selectionHeader;
    private Button counter;
    private Button deleteSelected;
    private Button selectAll;
    private Button cancelSelection;
    private ScrollView scroll;
    private boolean pendingCoverDownload;
    private Future<?> task;
    private ArrayList<Integer> pendingDownloads;
    private boolean busy;
    private boolean stopped;
    private volatile boolean destroyed;
    private AlertDialog videoOnlyDialog;
    private AlertDialog emptyContentDialog;
    private AlertDialog similarLinksDialog;
    private AlertDialog similarLinksInfoDialog;
    private AlertDialog errorDetailsDialog;
    private AlertDialog selectionTypeDialog;

    private static final class Row {
        int index;
        final View card;
        final BatchLinkInput input;
        final LinearLayout actions;
        final TextView status;
        final View progressPanel;
        final ProgressBar progress;
        final TextView percent;
        final TextView result;
        final TextView path;
        final Button errorDetails;
        final CheckBox inputSelection;
        final CheckBox videoSelection;
        final LinearLayout videoSlot;
        BatchVideoCard preview;
        final List<Button> buttons = new ArrayList<>();

        Row(int index, View card) {
            this.index = index;
            this.card = card;
            input = card.findViewById(R.id.batch_input);
            actions = card.findViewById(R.id.batch_entry_actions);
            status = card.findViewById(R.id.batch_entry_status);
            progressPanel = card.findViewById(R.id.batch_entry_progress_panel);
            progress = card.findViewById(R.id.batch_entry_progress);
            percent = card.findViewById(R.id.batch_entry_percent);
            result = card.findViewById(R.id.batch_entry_result);
            path = card.findViewById(R.id.batch_entry_path);
            errorDetails = card.findViewById(R.id.batch_error_details);
            inputSelection = card.findViewById(R.id.batch_select_input);
            videoSelection = card.findViewById(R.id.batch_select_video);
            videoSlot = card.findViewById(R.id.batch_video_slot);
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_batch);
        applyWindowInsets();
        if (state != null) {
            batch = Build.VERSION.SDK_INT >= 33 ? state.getSerializable(SAVED_BATCH, BatchState.class)
                    : (BatchState) state.getSerializable(SAVED_BATCH);
            pendingDownloads = state.getIntegerArrayList(SAVED_DOWNLOADS);
            pendingCoverDownload = state.getBoolean(SAVED_COVER_DOWNLOAD, false);
        }
        if (batch == null) batch = BatchDraftStore.load(draftFile());
        entries = findViewById(R.id.batch_entries);
        parseAll = findViewById(R.id.batch_parse_all_button);
        downloadAll = findViewById(R.id.batch_download_all_button);
        clearAll = findViewById(R.id.batch_clear_all_button);
        status = findViewById(R.id.batch_status);
        normalHeader = findViewById(R.id.batch_normal_header);
        selectionHeader = findViewById(R.id.batch_selection_header);
        counter = findViewById(R.id.batch_count);
        deleteSelected = findViewById(R.id.batch_delete_selected);
        selectAll = findViewById(R.id.batch_select_all);
        cancelSelection = findViewById(R.id.batch_cancel_selection);
        scroll = findViewById(R.id.batch_scroll);
        findViewById(R.id.batch_to_top).setOnClickListener(ignored -> scroll.smoothScrollTo(0, 0));
        findViewById(R.id.batch_to_bottom).setOnClickListener(ignored ->
                scroll.smoothScrollTo(0, scroll.getChildAt(0).getHeight()));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> updateJumpButtons());
        entries.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                scroll.post(this::updateJumpButtons));
        counter.setOnClickListener(ignored -> beginSelection());
        deleteSelected.setOnClickListener(ignored -> deleteSelection());
        selectAll.setOnClickListener(ignored -> {
            if (controlsLocked()) return;
            batch.toggleSelectAll();
            renderControls();
        });
        cancelSelection.setOnClickListener(ignored -> {
            if (controlsLocked()) return;
            batch.cancelSelection();
            renderControls();
        });
        findViewById(R.id.batch_back).setOnClickListener(ignored -> finish());
        findViewById(R.id.batch_settings).setOnClickListener(ignored ->
                startActivity(new Intent(this, SettingsActivity.class)));
        parseAll.setOnClickListener(ignored -> parse(batch.parseTargets()));
        downloadAll.setOnClickListener(ignored -> startDownloads());
        clearAll.setOnClickListener(ignored -> {
            if (controlsLocked() || batch.isSelecting()) return;
            for (Row row : rows) disposePreview(row);
            batch.clearInputs();
            rows.clear();
            entries.removeAllViews();
            addRow(0);
            rows.get(0).input.requestFocus();
            status.setText(R.string.ready);
            renderControls();
        });
        for (int i = 0; i < batch.size(); i++) addRow(i);
        renderControls();
    }

    @Override protected void onResume() {
        super.onResume();
        stopped = false;
        for (Row row : rows) renderActions(row);
        renderControls();
    }

    private void addRow(int index) {
        View card = LayoutInflater.from(this).inflate(R.layout.view_batch_entry, entries, false);
        card.setSaveFromParentEnabled(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        entries.addView(card, params);
        Row row = new Row(index, card);
        rows.add(row);
        row.inputSelection.setOnClickListener(ignored -> toggleSelection(row));
        row.videoSelection.setOnClickListener(ignored -> toggleSelection(row));
        ((TextView) card.findViewById(R.id.batch_entry_label)).setText(getString(R.string.batch_entry_label, index + 1));
        row.input.setText(batch.entry(index).input);
        row.input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (isCurrentRow(row)) batch.entry(row.index).input = text.toString();
            }

            @Override public void afterTextChanged(Editable text) { }
        });
        row.input.setOnNextInput(() -> advance(row));
        row.input.setOnEmptyBackspace(() -> removeEmptyRow(row));
        row.input.setOnKeyListener((view, key, event) -> {
            if (key != KeyEvent.KEYCODE_ENTER && key != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) advance(row);
            return true;
        });
        renderActions(row);
        bindResult(row);
    }

    private void advance(Row row) {
        if (controlsLocked() || batch.isSelecting() || destroyed || isFinishing() || !isCurrentRow(row)) return;
        int next = batch.next(row.index, SettingsActivity.shouldPreviewBatch(this));
        if (next == BatchState.EMPTY_INPUT) {
            if (emptyContentDialog == null || !emptyContentDialog.isShowing()) {
                emptyContentDialog = new AlertDialog.Builder(this).setMessage(R.string.batch_empty_content)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
            return;
        }
        if (next < 0) {
            toast(R.string.batch_limit);
            return;
        }
        while (rows.size() < batch.size()) addRow(rows.size());
        renderControls();
        EditText input = rows.get(next).input;
        input.requestFocus();
        input.setSelection(input.length());
        input.post(() -> input.requestRectangleOnScreen(new Rect(0, 0, input.getWidth(), input.getHeight()), false));
    }

    private boolean isCurrentRow(Row row) {
        return row.index >= 0 && row.index < rows.size() && rows.get(row.index) == row;
    }

    private void removeEmptyRow(Row row) {
        if (controlsLocked() || batch.isSelecting() || destroyed || isFinishing() || !isCurrentRow(row) || !batch.removeEmpty(row.index)) return;
        int removed = row.index;
        rows.remove(removed);
        disposePreview(row);
        row.index = -1;
        entries.removeView(row.card);
        for (int i = removed; i < rows.size(); i++) {
            Row remaining = rows.get(i);
            remaining.index = i;
            ((TextView) remaining.card.findViewById(R.id.batch_entry_label))
                    .setText(getString(R.string.batch_entry_label, i + 1));
        }
        renderControls();
        // A successful preview has no visible input. Prefer an editable row before it, then after it.
        for (int i = Math.min(removed - 1, rows.size() - 1); i >= 0; i--) {
            if (focusInput(rows.get(i))) return;
        }
        for (int i = removed; i < rows.size(); i++) if (focusInput(rows.get(i))) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(entries.getWindowToken(), 0);
    }

    private boolean focusInput(Row row) {
        if (row.input.getVisibility() != View.VISIBLE) return false;
        row.input.requestFocus();
        row.input.setSelection(row.input.length());
        return true;
    }

    private void beginSelection() {
        if (controlsLocked()) return;
        if (SettingsActivity.shouldAskBatchSelection(this)) {
            if (selectionTypeDialog != null && selectionTypeDialog.isShowing()) return;
            selectionTypeDialog = new AlertDialog.Builder(this).setTitle(R.string.batch_selection_type)
                    .setItems(R.array.batch_selection_types, (dialog, which) -> enterSelection(which == 1))
                    .setNegativeButton(R.string.batch_cancel_selection, null).show();
        } else enterSelection(!batch.videoTargets().isEmpty());
    }

    private void enterSelection(boolean videos) {
        if (controlsLocked() || destroyed || isFinishing()) return;
        if (videos && batch.videoTargets().isEmpty()) {
            toast(R.string.batch_no_videos);
            return;
        }
        batch.beginSelection(videos);
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
        renderControls();
    }

    private void toggleSelection(Row row) {
        if (controlsLocked() || !isCurrentRow(row) || !batch.canSelect(row.index)) return;
        batch.setSelected(row.index, !batch.entry(row.index).selected);
        renderControls();
    }

    private void deleteSelection() {
        if (controlsLocked() || batch.selectedTargets().isEmpty()) return;
        boolean resultsOnly = batch.selectingVideos() && !SettingsActivity.shouldDeleteVideoInputs(this);
        List<Integer> removed = batch.deleteSelected(!resultsOnly);
        if (resultsOnly) {
            for (int index : removed) {
                Row row = rows.get(index);
                bindResult(row);
                row.status.setVisibility(View.GONE);
                row.errorDetails.setVisibility(View.GONE);
                row.progressPanel.setVisibility(View.GONE);
            }
        } else {
            for (int i = removed.size() - 1; i >= 0; i--) {
                Row row = rows.remove((int) removed.get(i));
                disposePreview(row);
                row.index = -1;
                entries.removeView(row.card);
            }
            if (rows.isEmpty()) addRow(0);
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).index = i;
                ((TextView) rows.get(i).card.findViewById(R.id.batch_entry_label))
                        .setText(getString(R.string.batch_entry_label, i + 1));
            }
        }
        status.setText(R.string.ready);
        renderControls();
    }

    private boolean controlsLocked() { return busy || pendingDownloads != null; }

    private void renderControls() {
        boolean selecting = batch.isSelecting();
        boolean videos = batch.selectingVideos();
        boolean locked = controlsLocked();
        normalHeader.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionHeader.setVisibility(selecting ? View.VISIBLE : View.GONE);
        counter.setText(getString(R.string.batch_count, batch.size()));
        counter.setContentDescription(getString(R.string.batch_count_description, batch.size()));
        counter.setEnabled(!locked);
        deleteSelected.setEnabled(!locked && !batch.selectedTargets().isEmpty());
        selectAll.setText(batch.allSelected() ? R.string.batch_deselect_all : R.string.batch_select_all);
        selectAll.setEnabled(!locked && (!videos || !batch.videoTargets().isEmpty()));
        cancelSelection.setEnabled(!locked);
        parseAll.setEnabled(!locked && !selecting);
        clearAll.setEnabled(!locked && !selecting);
        downloadAll.setEnabled(!locked && (!selecting || videos));
        for (Row row : rows) {
            renderVideo(row);
            boolean eligible = batch.canSelect(row.index);
            TextView label = row.card.findViewById(R.id.batch_entry_label);
            label.setText(getString(batch.entry(row.index).result instanceof VideoResult
                    && !batch.entry(row.index).parseFailed ? R.string.batch_select_video : R.string.batch_entry_label, row.index + 1));
            label.setVisibility(selecting && !videos ? View.GONE : View.VISIBLE);
            row.inputSelection.setText(getString(R.string.batch_entry_label, row.index + 1));
            row.videoSelection.setText(getString(R.string.batch_select_video, row.index + 1));
            row.inputSelection.setVisibility(eligible && !videos ? View.VISIBLE : View.GONE);
            row.videoSelection.setVisibility(eligible && videos ? View.VISIBLE : View.GONE);
            row.inputSelection.setChecked(eligible && batch.entry(row.index).selected);
            row.videoSelection.setChecked(eligible && batch.entry(row.index).selected);
            row.inputSelection.setEnabled(!locked);
            row.videoSelection.setEnabled(!locked);
            row.input.setEnabled(!locked && !selecting);
            row.result.setEnabled(!locked);
            row.result.setTextIsSelectable(!selecting);
            row.result.setOnClickListener(eligible && videos ? ignored -> toggleSelection(row) : null);
            row.result.setClickable(eligible && videos);
            for (Button button : row.buttons) button.setEnabled(!locked && !selecting);
            if (row.preview != null) row.preview.setInteractive(!locked && !selecting);
        }
    }

    private void renderVideo(Row row) {
        BatchState.Entry entry = batch.entry(row.index);
        boolean show = SettingsActivity.shouldPreviewBatch(this) && entry.result instanceof VideoResult && !entry.parseFailed;
        if (!show || (row.preview != null && row.preview.result != entry.result)) disposePreview(row);
        if (show && row.preview == null) {
            View content = LayoutInflater.from(this).inflate(R.layout.view_video_result, row.videoSlot, false);
            row.videoSlot.addView(content);
            row.preview = new BatchVideoCard(this, content, entry, coverExecutor, () -> {
                for (Row other : rows) if (other != row && other.preview != null) other.preview.pauseAndRelease();
            }, () -> downloadOne(row, false), () -> downloadOne(row, true));
        }
        row.videoSlot.setVisibility(show ? View.VISIBLE : View.GONE);
        row.input.setVisibility(show ? View.GONE : View.VISIBLE);
        row.actions.setVisibility(show || row.buttons.isEmpty() ? View.GONE : View.VISIBLE);
        row.result.setVisibility(!show && entry.result != null ? View.VISIBLE : View.GONE);
    }

    private void disposePreview(Row row) {
        if (row.preview == null) return;
        row.preview.dispose(); row.preview = null;
        row.videoSlot.removeAllViews();
    }

    private void updateJumpButtons() {
        if (destroyed) return;
        findViewById(R.id.batch_to_top).setVisibility(scroll.canScrollVertically(-1) ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.batch_to_bottom).setVisibility(scroll.canScrollVertically(1) ? View.VISIBLE : View.INVISIBLE);
    }

    private void renderActions(Row row) {
        row.actions.removeAllViews();
        row.buttons.clear();
        if (SettingsActivity.batchButtonEnabled(this, SettingsActivity.BATCH_PARSE)) {
            row.buttons.add(action(R.string.parse, R.style.InputBlueButton,
                    ignored -> parse(Collections.singletonList(row.index))));
        }
        if (SettingsActivity.batchButtonEnabled(this, SettingsActivity.BATCH_REPARSE)) {
            row.buttons.add(action(R.string.reparse, R.style.InputGreenButton,
                    ignored -> parse(Collections.singletonList(row.index))));
        }
        if (SettingsActivity.batchButtonEnabled(this, SettingsActivity.BATCH_CLEAR)) {
            row.buttons.add(action(R.string.clear_link, R.style.InputRedButton,
                    ignored -> row.input.clearLink()));
        }
        row.actions.setVisibility(row.buttons.isEmpty() ? View.GONE : View.VISIBLE);
        if (row.buttons.size() == 3) {
            addButtonRow(row, row.buttons.subList(0, 1));
            addButtonRow(row, row.buttons.subList(1, 3));
        } else {
            for (Button button : row.buttons) addButtonRow(row, Collections.singletonList(button));
        }
    }

    private Button action(int text, int style, View.OnClickListener click) {
        Button button = new Button(this, null, 0, style);
        button.setText(text);
        button.setEnabled(!controlsLocked() && !batch.isSelecting());
        button.setOnClickListener(click);
        return button;
    }

    private void addButtonRow(Row row, List<Button> buttons) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (row.actions.getChildCount() > 0) lineParams.topMargin = dp(8);
        row.actions.addView(line, lineParams);
        for (int i = 0; i < buttons.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) params.setMarginStart(dp(4));
            if (i < buttons.size() - 1) params.setMarginEnd(dp(4));
            line.addView(buttons.get(i), params);
        }
    }

    private void parse(List<Integer> targets) {
        if (controlsLocked() || batch.isSelecting()) return;
        List<Integer> nonEmpty = new ArrayList<>();
        for (int index : targets) if (!BatchState.emptyInput(batch.entry(index).input)) nonEmpty.add(index);
        if (nonEmpty.isEmpty()) {
            toast(R.string.input_empty);
            return;
        }
        List<List<Integer>> groups = batch.similarLinks(nonEmpty);
        if (!groups.isEmpty()) {
            if (similarLinksDialog != null && similarLinksDialog.isShowing()) return;
            similarLinksDialog = new AlertDialog.Builder(this).setMessage(R.string.batch_similar_links_confirm)
                    .setPositiveButton(R.string.batch_yes, (dialog, which) -> parseConfirmed(nonEmpty))
                    .setNegativeButton(R.string.batch_no, (dialog, which) -> showSimilarLinks(groups)).show();
            return;
        }
        parseConfirmed(nonEmpty);
    }

    private void showSimilarLinks(List<List<Integer>> groups) {
        StringBuilder message = new StringBuilder();
        for (List<Integer> group : groups) {
            StringBuilder numbers = new StringBuilder();
            for (int index : group) {
                if (numbers.length() > 0) numbers.append("与");
                numbers.append(index + 1);
            }
            if (message.length() > 0) message.append('\n');
            message.append(getString(R.string.batch_similar_links_group, numbers));
        }
        similarLinksInfoDialog = new AlertDialog.Builder(this).setMessage(message.toString())
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void parseConfirmed(List<Integer> nonEmpty) {
        if (controlsLocked() || batch.isSelecting() || destroyed || isFinishing()) return;
        setBusy(true);
        task = executor.submit(() -> {
            int[] counts = {0, 0};
            int[] position = {0};
            BatchRunner.run(nonEmpty, index -> {
                Row row = rows.get(index);
                int current = ++position[0];
                post(() -> {
                    status.setText(getString(R.string.batch_parsing_item, current, nonEmpty.size()));
                    row.status.setText(R.string.loading);
                    row.errorDetails.setVisibility(View.GONE);
                    row.status.setTextColor(getColor(R.color.text_secondary));
                    row.status.setVisibility(View.VISIBLE);
                    row.progress.setIndeterminate(false);
                    row.progress.setProgress(0);
                    row.percent.setText(getString(R.string.parse_percent_value, 0));
                    row.progressPanel.setVisibility(View.VISIBLE);
                });
                ParsedResult result = new VideoParser(new AndroidHttpClient(this)).parse(batch.entry(index).input, percent -> post(() -> {
                    row.progress.setProgress(percent);
                    row.percent.setText(getString(R.string.parse_percent_value, percent));
                }));
                if (result instanceof ImagePostResult) {
                    counts[1]++;
                    post(() -> showImageNotSupported(row));
                    return;
                }
                counts[0]++;
                post(() -> {
                    batch.entry(index).acceptVideo((VideoResult) result);
                    row.progressPanel.setVisibility(View.GONE);
                    row.status.setText(R.string.result_ready);
                    row.status.setTextColor(getColor(R.color.accent));
                    bindResult(row);
                    renderControls();
                    saveDraftIfStopped();
                });
            }, (index, error) -> {
                counts[1]++;
                post(() -> showFailure(rows.get(index), error, R.string.parse_failed));
            });
            finishOperation(counts[0], counts[1]);
        });
    }

    private void bindResult(Row row) {
        BatchState.Entry entry = batch.entry(row.index);
        if (entry.result instanceof VideoResult) {
            VideoResult video = (VideoResult) entry.result;
            row.result.setText(getString(R.string.batch_video_result, video.platform, video.title));
            row.result.setTextColor(getColor(R.color.text_primary));
            row.result.setContentDescription(null);
            row.result.setOnClickListener(null);
            row.result.setClickable(false);
        }
        row.result.setVisibility(entry.result == null || row.preview != null ? View.GONE : View.VISIBLE);
        String paths = entry.downloadPath;
        if (entry.coverDownloadPath != null && !entry.coverDownloadPath.isEmpty()) {
            paths += (paths.isEmpty() ? "" : "\n") + entry.coverDownloadPath;
        }
        row.path.setText(paths.isEmpty() ? "" : getString(R.string.download_saved_path, paths));
        row.path.setVisibility(paths.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void startDownloads() {
        if (controlsLocked()) return;
        ArrayList<Integer> targets = new ArrayList<>(batch.downloadTargets());
        if (targets.isEmpty()) {
            toast(batch.selectingVideos() ? R.string.batch_choose_video : R.string.batch_no_videos);
            return;
        }
        requestDownload(targets, false);
    }

    private void downloadOne(Row row, boolean cover) {
        if (controlsLocked() || batch.isSelecting() || !isCurrentRow(row)) return;
        requestDownload(new ArrayList<>(Collections.singletonList(row.index)), cover);
    }

    private void requestDownload(ArrayList<Integer> targets, boolean cover) {
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownloads = targets;
            pendingCoverDownload = cover;
            renderControls();
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        download(targets, cover);
    }

    private void download(List<Integer> targets, boolean cover) {
        setBusy(true);
        task = executor.submit(() -> {
            int[] counts = {0, 0};
            int[] position = {0};
            BatchRunner.run(targets, index -> {
                Row row = rows.get(index);
                VideoResult video = (VideoResult) batch.entry(index).result;
                int current = ++position[0];
                post(() -> {
                    status.setText(getString(R.string.batch_downloading_item, current, targets.size()));
                    row.status.setText(cover ? R.string.cover_download_running : R.string.download_running);
                    row.status.setTextColor(getColor(R.color.text_secondary));
                    row.status.setVisibility(View.VISIBLE);
                    row.progress.setIndeterminate(true);
                    row.percent.setText(new DownloadProgress("", 0, 0).detail());
                    row.progressPanel.setVisibility(View.VISIBLE);
                });
                VideoDownloader.DownloadedFile saved = cover ? VideoDownloader.downloadCover(getApplicationContext(), video)
                        : VideoDownloader.download(getApplicationContext(), video,
                        progress -> post(() -> {
                            int percent = progress.percent();
                            row.progress.setIndeterminate(percent < 0);
                            if (percent >= 0) row.progress.setProgress(percent);
                            row.percent.setText(progress.detail());
                            row.status.setText(progress.message);
                        }));
                counts[0]++;
                post(() -> {
                    if (cover) batch.entry(index).coverDownloadPath = saved.displayPath;
                    else batch.entry(index).downloadPath = saved.displayPath;
                    row.progressPanel.setVisibility(View.GONE);
                    row.status.setText(R.string.download_complete);
                    row.status.setTextColor(getColor(R.color.action_green));
                    bindResult(row);
                    saveDraftIfStopped();
                });
            }, (index, error) -> {
                counts[1]++;
                post(() -> showFailure(rows.get(index), error, R.string.download_failed));
            });
            finishOperation(counts[0], counts[1]);
        });
    }

    private void showImageNotSupported(Row row) {
        batch.entry(row.index).parseFailed = true;
        saveDraftIfStopped();
        renderControls();
        row.progressPanel.setVisibility(View.GONE);
        row.status.setText(R.string.batch_video_only);
        row.status.setTextColor(getColor(R.color.error));
        row.status.setVisibility(View.VISIBLE);
        if (videoOnlyDialog == null || !videoOnlyDialog.isShowing()) {
            videoOnlyDialog = new AlertDialog.Builder(this).setMessage(R.string.batch_video_only)
                    .setPositiveButton(android.R.string.ok, null).show();
        }
    }

    private void showFailure(Row row, Exception error, int fallback) {
        if (fallback == R.string.parse_failed) {
            batch.entry(row.index).parseFailed = true;
            saveDraftIfStopped();
            renderControls();
        }
        row.progressPanel.setVisibility(View.GONE);
        String message = error.getMessage();
        row.status.setText(message == null || message.isEmpty() ? getString(fallback) : message);
        row.status.setTextColor(getColor(R.color.error));
        row.status.setVisibility(View.VISIBLE);
        if (error instanceof ParseFailure) {
            String details = ((ParseFailure) error).details;
            row.errorDetails.setVisibility(View.VISIBLE);
            row.errorDetails.setOnClickListener(ignored -> {
                if (errorDetailsDialog != null) errorDetailsDialog.dismiss();
                errorDetailsDialog = ParseErrorDialog.show(this, details);
            });
        } else row.errorDetails.setVisibility(View.GONE);
    }

    private void finishOperation(int succeeded, int failed) {
        post(() -> {
            task = null;
            setBusy(false);
            status.setText(getString(R.string.batch_operation_complete, succeeded, failed));
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        renderControls();
    }

    private void post(Runnable update) {
        handler.post(() -> { if (!destroyed && !isFinishing()) update.run(); });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_STORAGE) return;
        ArrayList<Integer> targets = pendingDownloads;
        boolean cover = pendingCoverDownload;
        pendingDownloads = null;
        pendingCoverDownload = false;
        if (destroyed || isFinishing()) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && targets != null) download(targets, cover);
        else {
            renderControls();
            toast(R.string.storage_permission_denied);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putSerializable(SAVED_BATCH, batch);
        state.putIntegerArrayList(SAVED_DOWNLOADS, pendingDownloads);
        state.putBoolean(SAVED_COVER_DOWNLOAD, pendingCoverDownload);
        super.onSaveInstanceState(state);
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        View root = findViewById(R.id.batch_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(int text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private File draftFile() { return new File(getFilesDir(), "batch-draft.bin"); }

    private void saveDraft() {
        try { BatchDraftStore.save(draftFile(), batch); }
        catch (IOException failure) { toast(R.string.batch_draft_save_failed); }
    }

    private void saveDraftIfStopped() {
        if (stopped && !destroyed && !isFinishing()) saveDraft();
    }

    @Override public void finish() {
        if (batch != null) {
            if (SettingsActivity.shouldClearBatchOnReturn(this)) {
                try { BatchDraftStore.clear(draftFile()); }
                catch (IOException failure) { toast(R.string.batch_draft_save_failed); }
            } else {
                batch.cancelSelection();
                saveDraft();
            }
        }
        super.finish();
    }

    @Override protected void onStop() {
        stopped = true;
        for (Row row : rows) if (row.preview != null) row.preview.pauseAndRelease();
        if (!isFinishing()) saveDraft();
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (task != null) task.cancel(true);
        executor.shutdownNow();
        for (Row row : rows) disposePreview(row);
        coverExecutor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (videoOnlyDialog != null) videoOnlyDialog.dismiss();
        if (emptyContentDialog != null) emptyContentDialog.dismiss();
        if (similarLinksDialog != null) similarLinksDialog.dismiss();
        if (similarLinksInfoDialog != null) similarLinksInfoDialog.dismiss();
        if (errorDetailsDialog != null) errorDetailsDialog.dismiss();
        if (selectionTypeDialog != null) selectionTypeDialog.dismiss();
        super.onDestroy();
    }
}
