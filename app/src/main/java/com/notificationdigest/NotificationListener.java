package com.notificationdigest;

import android.app.Notification;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 通知监听核心服务
 * 监听系统所有通知，写入本地 SQLite 数据库
 */
public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "NotificationListener";
    private DatabaseHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        Log.i(TAG, "NotificationListener 启动");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 过滤：只处理实际通知（排除前台服务的持久通知）
        if (sbn.getNotification().flags == Notification.FLAG_FOREGROUND_SERVICE) {
            return;
        }

        String packageName = sbn.getPackageName();
        long postTime = sbn.getPostTime();
        String title = "";
        String text = "";

        // 提取通知内容
        Notification notification = sbn.getNotification();
        if (notification.extras != null) {
            CharSequence csTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence csText = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (csTitle != null) title = csTitle.toString();
            if (csText != null) text = csText.toString();
        }

        // 忽略空通知
        if (title.isEmpty() && text.isEmpty()) return;

        // 写入数据库
        dbHelper.insertNotification(packageName, title, text, postTime);
        Log.d(TAG, "记录通知: " + packageName + " | " + title);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 可选：处理通知被移除的事件
    }
}
