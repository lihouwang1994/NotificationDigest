package com.notificationdigest;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * 定时汇总 Worker
 * 每天执行一次：读取当日通知 → 调用 AI 生成简报 → 推送系统通知
 */
public class DigestWorker extends Worker {

    private static final String TAG = "DigestWorker";
    private static final String CHANNEL_ID = "digest_channel";
    private static final String CHANNEL_NAME = "通知简报";

    // 火山引擎 Ark API（OpenAI 兼容格式）
    private static final String API_BASE_URL = "https://ark.cn-beijing.volces.com";
    private static final String API_ENDPOINT = "/api/v3/chat/completions";

    public DigestWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Log.i(TAG, "开始生成每日通知简报...");

        DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
        String notifications = dbHelper.getTodayNotificationsForDigest();

        if (notifications == null || notifications.trim().isEmpty()) {
            Log.i(TAG, "今日无新通知，跳过");
            return Result.success();
        }

        // 从 SharedPreferences 读取配置
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        String modelId = prefs.getString("model_id", "");

        if (apiKey.isEmpty() || modelId.isEmpty()) {
            Log.e(TAG, "API Key 或模型 ID 未配置");
            return Result.failure();
        }

        // 调用 AI 生成简报
        String digest = callAiApi(notifications, apiKey, modelId);

        if (digest == null || digest.isEmpty()) {
            Log.e(TAG, "AI 生成简报失败");
            return Result.retry();
        }

        // 推送简报到系统通知栏
        sendDigestNotification(digest);

        // 清理 7 天前的旧数据
        dbHelper.cleanupOldData(7);

        Log.i(TAG, "每日简报已生成并推送");
        return Result.success();
    }

    /** 调用火山引擎 Ark API 生成简报 */
    private String callAiApi(String notificationsText, String apiKey, String modelId) {
        try {
            // 构造 Prompt
            String prompt = "以下是用户手机今日的通知汇总，请生成一份简洁的每日简报。\n" +
                    "要求：\n" +
                    "1. 按应用分类汇总\n" +
                    "2. 提炼关键信息，去除冗余\n" +
                    "3. 用中文输出，格式清晰\n" +
                    "4. 总长度控制在 300 字以内\n\n" +
                    "通知内容：\n" + notificationsText;

            // 构造请求体（OpenAI 兼容格式）
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", modelId); // 使用用户配置的模型 ID
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 500);

            // 发送 HTTP POST 请求
            String apiUrl = API_BASE_URL + API_ENDPOINT;
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            // 火山引擎 Ark API 认证
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.e(TAG, "API 请求失败，状态码: " + code);
                return null;
            }

            // 解析响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            String content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            return content.trim();

        } catch (Exception e) {
            Log.e(TAG, "调用 AI API 异常: " + e.getMessage(), e);
            return null;
        }
    }

    /** 发送简报通知到系统通知栏 */
    private void sendDigestNotification(String digestText) {
        NotificationManager nm = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建通知渠道（Android 8.0+ 必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("每日通知简报");
            nm.createNotificationChannel(channel);
        }

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(getApplicationContext(), CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(getApplicationContext());
        }

        builder.setContentTitle("📊 今日通知简报")
                .setContentText(digestText.length() > 100 ? digestText.substring(0, 100) + "..." : digestText)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(digestText))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        nm.notify(1001, builder.build());
    }

    /**
     * 调度每日定时任务（根据用户配置的推送时间）
     * 在 Application 类中调用
     */
    public static void scheduleDailyDigest(Context context) {
        // 取消已有任务
        WorkManager.getInstance(context).cancelAllWorkByTag("daily_digest");

        // 读取用户配置的推送时间
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String digestTime = prefs.getString("digest_time", "20:00");
        String[] timeParts = digestTime.split(":");
        int targetHour = Integer.parseInt(timeParts[0]);
        int targetMinute = Integer.parseInt(timeParts[1]);

        // 计算距离下次推送的延迟时间
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, targetMinute);
        target.set(Calendar.SECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        // 每天执行一次，灵活时间段（系统会在窗口期内执行）
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DigestWorker.class, 24, TimeUnit.HOURS)
                .addTag("daily_digest")
                .build();

        WorkManager.getInstance(context).enqueue(request);
        Log.i(TAG, "已调度每日简报任务，推送时间: " + digestTime);
    }
}
