package com.linkvideo.parser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !UpdateInstaller.ACTION_INSTALL_RESULT.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = confirmationIntent(intent);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
                return;
            }
            status = PackageInstaller.STATUS_FAILURE;
        }
        String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        InstallOutcome.Result outcome = InstallOutcome.fromStatus(status, detail);
        UpdateStateStore.saveInstallOutcome(context, outcome);
        if (outcome.kind() == InstallOutcome.Kind.SUCCESS) UpdateStateStore.clearVerifiedDownload(context);
        Toast.makeText(context, outcome.message(), Toast.LENGTH_LONG).show();
        if (outcome.kind() != InstallOutcome.Kind.SUCCESS) {
            Intent update = new Intent().setClassName(context,
                    context.getPackageName() + ".UpdateActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(update);
        }
    }

    private static Intent confirmationIntent(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        return source.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
