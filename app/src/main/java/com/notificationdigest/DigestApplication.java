package com.notificationdigest;

import android.app.Application;
import androidx.work.WorkManager;

/**
 * Application 类
 * 初始化 WorkManager，调度每日简报任务
 */
public class DigestApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化 WorkManager（如果还没初始化）
        try {
            WorkManager.getInstance(this);
        } catch (IllegalStateException e) {
            // 未初始化，WorkManager 会自动初始化
        }

        // 调度每日简报任务（每天 20:00 执行）
        DigestWorker.scheduleDailyDigest(this);
    }
}
