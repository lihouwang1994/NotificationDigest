package com.notificationdigest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * 主界面：引导用户开启必要权限
 * 1. 通知监听权限（核心）
 * 2. 电池优化白名单（OPPO 保活关键）
 * 3. 自启动权限（需手动在系统设置中开启）
 */
public class MainActivity extends Activity {

    private TextView tvStatus;
    private EditText etApiKey;
    private EditText etModelId;
    private EditText etDigestTime;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("config", MODE_PRIVATE);
        tvStatus = findViewById(R.id.tv_status);
        etApiKey = findViewById(R.id.et_api_key);
        etModelId = findViewById(R.id.et_model_id);
        etDigestTime = findViewById(R.id.et_digest_time);

        // 加载已保存的配置
        etApiKey.setText(prefs.getString("api_key", ""));
        etModelId.setText(prefs.getString("model_id", ""));
        etDigestTime.setText(prefs.getString("digest_time", "20:00"));

        Button btnNotification = findViewById(R.id.btn_notification);
        Button btnBattery = findViewById(R.id.btn_battery);
        Button btnAutostart = findViewById(R.id.btn_autostart);
        Button btnSaveConfig = findViewById(R.id.btn_save_config);
        Button btnGenerateNow = findViewById(R.id.btn_generate_now);

        // 保存配置
        btnSaveConfig.setOnClickListener(v -> {
            String apiKey = etApiKey.getText().toString().trim();
            String modelId = etModelId.getText().toString().trim();
            String digestTime = etDigestTime.getText().toString().trim();

            if (apiKey.isEmpty() || modelId.isEmpty()) {
                Toast.makeText(this, "请填写 API Key 和模型 ID", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putString("api_key", apiKey)
                    .putString("model_id", modelId)
                    .putString("digest_time", digestTime)
                    .apply();

            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        // 手动生成简报
        btnGenerateNow.setOnClickListener(v -> {
            Toast.makeText(this, "正在生成简报...", Toast.LENGTH_SHORT).show();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DigestWorker.class).build();
            WorkManager.getInstance(this).enqueue(request);
        });

        // 引导开启通知监听
        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        // 引导关闭电池优化（OPPO 保活关键）
        btnBattery.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        });

        // 引导开启自启动（OPPO 需手动在系统设置中开启）
        btnAutostart.setOnClickListener(v -> {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.safecenter",
                        "com.coloros.safecenter.startup.StartupAppListActivity");
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "请手动在系统设置中开启自启动权限", Toast.LENGTH_LONG).show();
            }
        });

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    /** 更新权限状态显示 */
    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("通知监听权限：")
                .append(isNotificationListenerEnabled() ? "✅ 已开启" : "❌ 未开启")
                .append("\n");
        sb.append("电池优化：")
                .append(isBatteryOptimizationDisabled() ? "✅ 已关闭" : "❌ 未关闭")
                .append("\n");
        sb.append("\nOPPO 保活额外步骤：\n");
        sb.append("1. 在手机管家 → 应用管理 → 通知简报 → 允许自启动\n");
        sb.append("2. 在设置 → 电池 → 应用耗电管理 → 通知简报 → 允许后台高耗电\n");

        tvStatus.setText(sb.toString());
    }

    /** 检查通知监听权限是否开启 */
    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat != null) {
            for (String name : flat.split(":")) {
                if (name.contains(pkgName)) return true;
            }
        }
        return false;
    }

    /** 检查电池优化是否已关闭 */
    private boolean isBatteryOptimizationDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }
}
