# SmsRelay

`com.iunclear.smsrelay` 是一个将新收到短信转发到用户配置 HTTP 地址的 Android 应用。

当前版本：`1.0.0`（`versionCode 1`）

## 使用

1. 在首页填写设备名称和 HTTP 推送地址。地址保存在本机 DataStore 中，不会写入代码。
2. 打开“启用短信转发”，并在系统弹窗中授予短信接收权限。
3. 使用“测试推送”确认服务端可接收请求。

每条新短信会先写入 Room 数据库，再立即使用 HTTP `POST` 推送。网络或服务端临时错误会由 WorkManager 按指数退避重试；收到 4xx 或无效地址会显示为发送失败。多段短信会在接收时合并，发送者、接收时间和正文的 SHA-256 用作唯一 ID，避免重复转发。

推送 JSON：

```json
{
  "messageId": "sha256",
  "deviceName": "My Android",
  "sender": "+8613800000000",
  "content": "短信正文",
  "receivedAt": "2026-07-18T00:00:00Z"
}
```

应用仅声明 `RECEIVE_SMS` 和 `INTERNET` 两项权限；不读取历史短信，不使用前台服务，也不常驻后台。

## 构建

```sh
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。推送到 `main` 后，GitHub Actions 会自动编译并上传名为 `SmsRelay-v1.0.0-debug-apk` 的构建产物。
