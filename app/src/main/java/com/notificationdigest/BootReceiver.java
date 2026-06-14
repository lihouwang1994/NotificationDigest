package com.notificationdigest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启 Receiver
 * 手机重启后自动启动 NotificationListener 和 WorkManager
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "手机重启，重新调度每日简报任务");
            DigestWorker.scheduleDailyDigest(context);
        }
    }
}
