package com.linkvideo.parser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;

public final class SettingsActivity extends Activity {
    private static final String PREFERENCES = "linkvideo_settings";
    private static final String KEEP_VIDEO = "keep_video_for_image_post";
    private static final String SHOW_DOUYIN_PAGE = "show_douyin_parse_page";
    private static final String BATCH_SELECTION_OPTION = "batch_selection_option";
    private static final String BATCH_PREVIEW = "batch_video_preview";
    private static final String CLEAR_BATCH_ON_RETURN = "clear_batch_on_return";
    static final String BATCH_PARSE = "batch_parse_button";
    static final String BATCH_REPARSE = "batch_reparse_button";
    static final String BATCH_CLEAR = "batch_clear_button";
    private AnnouncementDialog announcement;
    private Button batchChoice;
    private PopupWindow batchOptions;
    private AlertDialog batchOptionHelp;

    static boolean shouldKeepVideo(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEEP_VIDEO, false);
    }

    static boolean batchButtonEnabled(Context context, String key) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(key, true);
    }

    static boolean shouldShowDouyinPage(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SHOW_DOUYIN_PAGE, false);
    }

    static boolean shouldAskBatchSelection(Context context) {
        return batchSelectionOption(context) == 1;
    }

    static boolean shouldPreviewBatch(Context context) {
        return context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(BATCH_PREVIEW, false);
    }

    static boolean shouldClearBatchOnReturn(Context context) {
        return context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(CLEAR_BATCH_ON_RETURN, false);
    }

    static boolean shouldDeleteVideoInputs(Context context) {
        return batchSelectionOption(context) == 2;
    }

    private static int batchSelectionOption(Context context) {
        return context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getInt(BATCH_SELECTION_OPTION, 0);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        announcement = new AnnouncementDialog(this);
        findViewById(R.id.settings_announcement).setOnClickListener(ignored -> announcement.show());
        announcement.restoreOrShow(state, false);
        findViewById(R.id.settings_back).setOnClickListener(ignored -> finish());
        applyWindowInsets();
        Spinner choice = findViewById(R.id.video_retention_choice);
        choice.setSelection(shouldKeepVideo(this) ? 1 : 0, false);
        choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                        .putBoolean(KEEP_VIDEO, position == 1).apply();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        bindSwitch(R.id.batch_parse_toggle, BATCH_PARSE);
        bindSwitch(R.id.batch_reparse_toggle, BATCH_REPARSE);
        bindSwitch(R.id.batch_clear_toggle, BATCH_CLEAR);
        bindSwitch(R.id.batch_preview_toggle, BATCH_PREVIEW, false);
        bindSwitch(R.id.batch_clear_on_return_toggle, CLEAR_BATCH_ON_RETURN, false);
        Switch browserToggle = findViewById(R.id.douyin_browser_toggle);
        browserToggle.setChecked(shouldShowDouyinPage(this));
        browserToggle.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(SHOW_DOUYIN_PAGE, checked).apply());
        batchChoice = findViewById(R.id.batch_selection_choice);
        renderBatchChoice();
        batchChoice.setOnClickListener(ignored -> showBatchOptions());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        announcement.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (announcement != null) announcement.dismiss();
        if (batchOptions != null) batchOptions.dismiss();
        if (batchOptionHelp != null) batchOptionHelp.dismiss();
        super.onDestroy();
    }

    private void renderBatchChoice() {
        batchChoice.setText(getResources().getStringArray(R.array.batch_selection_options)[batchSelectionOption(this)]);
    }

    private void showBatchOptions() {
        if (batchOptions != null && batchOptions.isShowing()) return;
        String[] labels = getResources().getStringArray(R.array.batch_selection_options);
        String[] descriptions = getResources().getStringArray(R.array.batch_selection_help);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int option = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            RadioButton choice = new RadioButton(this);
            choice.setText(labels[i]);
            choice.setTextSize(16);
            choice.setTextColor(getColor(R.color.text_primary));
            choice.setMinHeight(dp(56));
            choice.setChecked(batchSelectionOption(this) == i);
            row.addView(choice, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            choice.setOnClickListener(ignored -> {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putInt(BATCH_SELECTION_OPTION, option).apply();
                renderBatchChoice();
                batchOptions.dismiss();
            });
            Button help = new Button(this, null, 0, R.style.BatchToolbarButton);
            help.setText(R.string.batch_help_symbol);
            help.setTextSize(20);
            help.setContentDescription(getString(R.string.batch_help_description, labels[i]));
            Runnable explain = () -> {
                if (batchOptionHelp != null && batchOptionHelp.isShowing()) return;
                batchOptionHelp = new AlertDialog.Builder(this).setTitle(labels[option])
                        .setMessage(descriptions[option]).setPositiveButton(android.R.string.ok, null).show();
            };
            help.setOnLongClickListener(ignored -> { explain.run(); return true; });
            help.setOnClickListener(ignored -> explain.run());
            row.addView(help, new LinearLayout.LayoutParams(dp(48), dp(48)));
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        batchOptions = new PopupWindow(scroll, batchChoice.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        batchOptions.setBackgroundDrawable(getDrawable(R.drawable.card_background));
        batchOptions.setElevation(dp(8));
        batchOptions.setOutsideTouchable(true);
        batchOptions.showAsDropDown(batchChoice);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void bindSwitch(int id, String key) {
        bindSwitch(id, key, true);
    }

    private void bindSwitch(int id, String key, boolean defaultValue) {
        Switch toggle = findViewById(id);
        toggle.setChecked(getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(key, checked).apply());
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        View root = findViewById(R.id.settings_root);
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
}
