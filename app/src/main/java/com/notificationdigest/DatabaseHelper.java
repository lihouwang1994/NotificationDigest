package com.notificationdigest;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SQLite 数据库帮助类
 * 存储当日通知，供定时汇总使用
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DB_NAME = "notifications.db";
    private static final int DB_VERSION = 1;

    // 表结构
    private static final String TABLE_NOTIFS = "notifications";
    private static final String COL_ID = "_id";
    private static final String COL_PACKAGE = "package_name";
    private static final String COL_TITLE = "title";
    private static final String COL_TEXT = "text";
    private static final String COL_TIME = "post_time";  // Unix 时间戳（毫秒）
    private static final String COL_DIGESTED = "digested"; // 0=未汇总，1=已汇总

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_NOTIFS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PACKAGE + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_TEXT + " TEXT, " +
                COL_TIME + " INTEGER, " +
                COL_DIGESTED + " INTEGER DEFAULT 0)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTIFS);
        onCreate(db);
    }

    /** 插入一条通知 */
    public void insertNotification(String packageName, String title, String text, long postTime) {
        // 去重：同一应用、同一标题、同一内容、1分钟内只记一次
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(postTime));
        long oneMinAgo = postTime - 60 * 1000;
        String where = COL_PACKAGE + "=? AND " + COL_TITLE + "=? AND " + COL_TEXT + "=? AND " + COL_TIME + ">?";
        Cursor cursor = getReadableDatabase().query(TABLE_NOTIFS, null, where,
                new String[]{packageName, title, text, String.valueOf(oneMinAgo)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            cursor.close();
            return; // 已存在，跳过
        }
        if (cursor != null) cursor.close();

        ContentValues cv = new ContentValues();
        cv.put(COL_PACKAGE, packageName);
        cv.put(COL_TITLE, title);
        cv.put(COL_TEXT, text);
        cv.put(COL_TIME, postTime);
        cv.put(COL_DIGESTED, 0);
        getWritableDatabase().insert(TABLE_NOTIFS, null, cv);
    }

    /**
     * 获取当日未汇总的通知，拼接为文本
     * 返回格式：应用名 | 时间 | 标题：内容
     */
    public String getTodayNotificationsForDigest() {
        // 今天 00:00 的时间戳
        long todayStart = getTodayStartTime();

        String where = COL_TIME + ">=? AND " + COL_DIGESTED + "=0";
        Cursor cursor = getReadableDatabase().query(TABLE_NOTIFS, null, where,
                new String[]{String.valueOf(todayStart)}, null, null, COL_TIME + " ASC");

        if (cursor == null) return "";

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        int count = 0;
        while (cursor.moveToNext()) {
            String pkg = cursor.getString(cursor.getColumnIndexOrThrow(COL_PACKAGE));
            long time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIME));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
            String text = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEXT));

            String appName = getAppName(pkg);
            String timeStr = sdf.format(new Date(time));
            sb.append("[").append(timeStr).append("] ").append(appName);
            if (!TextUtils.isEmpty(title)) sb.append(" - ").append(title);
            if (!TextUtils.isEmpty(text)) sb.append("：").append(text);
            sb.append("\n");
            count++;
        }
        cursor.close();

        if (count == 0) return "";

        // 标记已汇总
        markDigested(todayStart);
        return sb.toString();
    }

    /** 标记当日通知为已汇总 */
    private void markDigested(long todayStart) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DIGESTED, 1);
        getWritableDatabase().update(TABLE_NOTIFS, cv,
                COL_TIME + ">=? AND " + COL_DIGESTED + "=0",
                new String[]{String.valueOf(todayStart)});
    }

    /** 获取今天 00:00:00 的时间戳 */
    private long getTodayStartTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 根据包名获取应用名称（简化版，实际应走 PackageManager） */
    private String getAppName(String packageName) {
        // 常见应用映射
        if (packageName.contains("wechat")) return "微信";
        if (packageName.contains("qq")) return "QQ";
        if (packageName.contains("mail") || packageName.contains("gmail")) return "邮件";
        if (packageName.contains("taobao") || packageName.contains("jd")) return "购物";
        return packageName;
    }

    /** 清理 N 天前的旧数据（可选，避免数据库过大） */
    public void cleanupOldData(int daysToKeep) {
        long cutoff = System.currentTimeMillis() - daysToKeep * 24 * 60 * 60 * 1000L;
        getWritableDatabase().delete(TABLE_NOTIFS, COL_TIME + "<?", new String[]{String.valueOf(cutoff)});
    }
}
