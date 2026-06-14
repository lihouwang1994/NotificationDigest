# 通知简报 Android App

## 功能说明

监听手机所有通知，每日自动汇总并通过 AI 生成简报，推送到手机通知栏。

**主要功能：**
- 📱 监听所有应用通知（微信、QQ、邮件、购物等）
- 🤖 调用火山引擎 AI 生成每日简报
- ⏰ 自定义简报推送时间
- 🔧 手动触发简报生成
- 🔐 API Key 本地存储（安全）
- 🔋 OPPO ColorOS 保活优化

## 项目结构

```
NotificationDigest/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # 权限声明 + 服务注册
│       ├── java/com/notificationdigest/
│       │   ├── NotificationListener.java   # 通知监听核心服务
│       │   ├── DatabaseHelper.java          # SQLite 数据库操作
│       │   ├── DigestWorker.java            # 定时汇总 + AI 调用 + 推送
│       │   ├── MainActivity.java            # 引导界面（权限开启）
│       │   ├── DigestApplication.java       # Application 初始化
│       │   └── BootReceiver.java            # 开机自启
│       └── res/
│           ├── layout/activity_main.xml     # 主界面布局
│           └── values/styles.xml            # 主题
├── build.gradle                     # 项目配置
├── settings.gradle                  # 模块配置
└── README.md                        # 本文件
```

## 部署步骤

### 第一步：获取火山引擎 API Key

1. 登录 [火山引擎控制台](https://console.volcengine.com/)
2. 进入「火山方舟 Ark」→「API Key 管理」
3. 创建新的 API Key 并复制
4. 记录你的**模型 ID**（格式类似 `ep-2024xxxxx`）

> 💡 模型 ID 在 Ark 控制台的「模型列表」中查看

### 第二步：安装并配置应用

1. 安装 APK 后打开应用
2. 在首页填写配置：
   - **API Key**: 填入第一步获取的 API Key
   - **模型 ID**: 填入你的模型 ID（如 `ep-2024xxxxx`）
   - **简报推送时间**: 设置每日简报推送时间（如 `20:00`）
3. 点击「保存配置」
4. 点击「开启通知监听权限」
5. 点击「关闭电池优化（保活）」
6. 点击「OPPO 自启动设置」并手动开启

### 第三步：编译 APK

#### 方案 A：GitHub Actions 自动编译（推荐）

1. 在 GitHub 创建新 repo（例如 `NotificationDigest`）
2. 将本项目代码推送到 repo
3. 在 repo 设置中开启 Actions
4. 等待编译完成，在 Actions 页面下载 APK

> 📝 详细步骤见下方「GitHub Actions 编译步骤」

#### 方案 B：本地编译（需要 Android Studio）

1. 安装 Android Studio
2. 打开本项目
3. 点击 `Build → Build Bundle(s) / APK(s) → Build APK(s)`
4. 在 `app/build/outputs/apk/` 找到 APK 文件

#### 方案 C：手机端编译（无电脑）

1. 在手机上安装 **AIDE** 应用
2. 将本项目代码复制到手机
3. 用 AIDE 打开并编译

### 第四步：安装到 OPPO 手机

1. 将 APK 文件传到手机
2. 在手机上点击安装（需开启「允许安装未知来源应用」）
3. 安装完成后打开应用

### 第五步：开启权限（关键）

应用打开后，按界面提示依次开启：

1. **通知监听权限**（核心，必须开启）
2. **关闭电池优化**（OPPO 保活关键）
3. **OPPO 自启动设置**（手动在系统设置中开启）

#### OPPO ColorOS 保活额外步骤：

1. 打开「手机管家」→「应用管理」→「通知简报」→ 允许自启动
2. 打开「设置」→「电池」→「应用耗电管理」→「通知简报」→ 允许后台高耗电
3. 打开「设置」→「应用管理」→「通知简报」→「权限」→ 允许自启动和后台运行

### 第六步：测试

1. 让朋友发几条微信/QQ 消息（产生通知）
2. 打开应用，检查权限是否已全部开启
3. 等待每天 20:00 自动生成简报，或手动触发：
   - 在手机上执行：`adb shell am instrument -w com.notificationdigest/.DigestWorker`
   - 或在应用中添加「立即生成简报」按钮（需自行添加）

## 使用说明

### 手动生成简报

安装并配置完成后，点击首页的「立即生成简报」按钮，即可手动触发简报生成。

> ⚠️ 需要先产生一些通知（如让朋友发微信消息），否则会提示「今日无新通知」

### 修改推送时间

1. 在首页修改「简报推送时间」
2. 点击「保存配置」
3. 重启应用（或等待下次自动调度）

### 查看简报

简报会推送到手机通知栏，下拉通知栏即可查看。

## 故障排查

### 问题 1：收不到简报通知

- 检查通知监听权限是否开启（设置 → 通知与状态栏 → 通知管理 → 通知简报 → 允许通知）
- 检查电池优化是否关闭
- 检查 OPPO 自启动是否开启
- 检查 API Key 和模型 ID 是否填写正确

### 问题 2：AI 接口调用失败

- 检查 API Key 是否正确（在火山引擎控制台验证）
- 检查模型 ID 是否正确
- 检查手机网络是否正常
- 查看 logcat 日志：`adb logcat | grep DigestWorker`

### 问题 3：OPPO 后台被杀

- 按照「OPPO 保活额外步骤」全部设置一遍
- 将应用锁定在最近任务列表（下拉应用卡片，点击锁定图标）
- 在手机管家中将应用设为「允许后台高耗电」

### 问题 4：手动生成简报无反应

- 检查是否已开启通知监听权限
- 检查今日是否有新通知（否则会跳过）
- 检查 API Key 和模型 ID 是否已保存

## 自定义

### 修改 AI Prompt

打开 `DigestWorker.java`，修改 `callAiApi` 方法中的 `prompt` 变量。

当前 Prompt：
```
以下是用户手机今日的通知汇总，请生成一份简洁的每日简报。
要求：
1. 按应用分类汇总
2. 提炼关键信息，去除冗余
3. 用中文输出，格式清晰
4. 总长度控制在 300 字以内
```

### 修改简报推送方式

当前是推送到系统通知栏。如需推送到微信/企微，修改 `sendDigestNotification` 方法。

### 精确控制推送时间

当前使用 WorkManager，系统会在 24 小时窗口内自动选择时间（不精确）。

如需精确时间（如每天 20:00 准时推送），需要改用 `AlarmManager`。

修改 `DigestWorker.scheduleDailyDigest` 方法，将 WorkManager 替换为 AlarmManager。

> 💡 参考代码：https://developer.android.com/training/scheduling/alarms

## 技术栈

- **通知监听**: `NotificationListenerService`
- **本地存储**: SQLite（`DatabaseHelper`）+ SharedPreferences（配置）
- **定时任务**: WorkManager（`DigestWorker`，每日定时）+ 手动触发
- **AI 接口**: 火山引擎 Ark API（OpenAI 兼容格式）
- **保活策略**: 前台服务 + 电池优化白名单 + 开机自启 + OPPO 特殊设置

## GitHub Actions 编译步骤

1. 在 GitHub 创建新仓库（例如 `NotificationDigest`）
2. 将本地代码推送到 GitHub：
   ```bash
   cd C:\Users\李厚望\.qclaw\workspace-agent-636d4c66\NotificationDigest
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/你的用户名/NotificationDigest.git
   git push -u origin main
   ```
3. 推送后，GitHub Actions 会自动开始编译（约 5-10 分钟）
4. 在 repo 页面点击「Actions」标签
5. 等待编译完成（绿色 ✓）
6. 点击编译任务 → 在「Artifacts」区域下载 `NotificationDigest-APK.zip`
7. 解压得到 `app-debug.apk`

> 📝 每次推送代码到 main 分支，都会自动重新编译

## 注意事项

- 本应用需要读取所有通知，请确保在可信环境下使用
- OPPO ColorOS 对后台服务管控极严，需按「OPPO 保活额外步骤」全部设置
- 如果手机重启，需手动重新开启通知监听权限（部分 OPPO 机型）
- AI 接口调用会消耗流量，建议在 Wi-Fi 环境下使用

## 下一步

1. 配置 `API_BASE_URL`（电脑局域网 IP）
2. 编译 APK（推荐用 GitHub Actions）
3. 安装到手机并开启权限
4. 测试并调试

如有问题，检查 logcat 日志：`adb logcat | grep NotificationDigest`
