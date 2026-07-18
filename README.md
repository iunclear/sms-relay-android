# SmsRelay

`com.iunclear.smsrelay` 是一个将新收到短信转发到用户配置 HTTP 地址的 Android 应用。

当前版本：`1.1.0`（`versionCode 3`）

## 使用

1. 在首页填写设备名称和 HTTP 推送地址。地址保存在本机 DataStore 中，不会写入代码。
2. 打开“启用短信转发”，并在系统弹窗中授予短信接收权限。
3. 使用“测试推送”确认服务端可接收请求。
4. 如需尽可能接收系统定向的验证码，选择“设为默认短信应用”。系统确认后，SmsRelay 只处理此后收到的新短信：先写入 Android 系统收件箱，再写入转发队列并推送。卸载应用后，已写入系统收件箱的短信会保留给后续默认短信应用使用。

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

普通监听模式仅使用 `RECEIVE_SMS` 和 `INTERNET`。启用默认短信模式后，Android 会基于 `ROLE_SMS` 授予短信读写、发送和 MMS/WAP 投递所需权限，以便应用承担默认短信应用的系统职责；代码不会查询、导入或转发既有历史短信。应用不使用前台服务，也不常驻后台。

默认短信模式提供基础文本短信发送和快捷回复入口，转发范围仍限于新到的文本短信。MMS 不会转发到 HTTP 地址，且本版本不提供 MMS 的查看或发送功能；需要 MMS 时请不要将 SmsRelay 设为默认短信应用。

## 构建

```sh
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。也可使用 R8 和资源压缩构建安装型优化包：

```sh
./gradlew assembleRelease
```

优化包位于 `app/build/outputs/apk/release/app-release.apk`，当前使用 Debug 签名便于安装调试；正式发布应在 CI 配置独立签名密钥。首页的“后台电池设置”会打开系统设置，可将应用设为不受限制的后台运行。推送到 `main` 后，GitHub Actions 会自动上传调试包与优化包，并创建或更新与版本号对应的 GitHub Release。
