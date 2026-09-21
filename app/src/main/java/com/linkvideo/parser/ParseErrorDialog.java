package com.linkvideo.parser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class ParseErrorDialog {
    static AlertDialog show(Activity activity, String details) {
        String version = "?";
        try { version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (Exception ignored) { }
        String report = "拾链 " + version + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" + details;
        TextView text = new TextView(activity);
        text.setText(report);
        text.setTextSize(14);
        text.setTextIsSelectable(true);
        text.setTextColor(activity.getColor(R.color.text_primary));
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(R.string.error_details_title).setView(scroll)
                .setPositiveButton(R.string.copy_error_details, null).setNegativeButton(android.R.string.cancel, null);
        if (details.contains("网页显示=关闭")) builder.setNeutralButton(R.string.douyin_browser_settings_open,
                (ignored, which) -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.error_details_title), report));
                Toast.makeText(activity, R.string.error_details_copied, Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
        return dialog;
    }
}
