package com.iunclear.smsrelay

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

private sealed interface SendResult {
    data object Success : SendResult
    data class Retry(val reason: String) : SendResult
    data class Failure(val reason: String) : SendResult
}

class MessageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = MessageDatabase.get(appContext).messages()
    private val preferences = AppPreferences(appContext)
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    val recent: Flow<List<RelayMessage>> = dao.recent()

    suspend fun clearHistory() = dao.clear()

    suspend fun receive(sender: String, content: String, receivedAt: Long) {
        val settings = preferences.settings.first()
        if (!settings.enabled) return

        val message = RelayMessage(
            messageId = messageId(sender, receivedAt, content),
            sender = sender,
            content = content,
            receivedAt = receivedAt
        )
        if (dao.insert(message) == -1L) return
        deliverNow(message, settings)
    }

    suspend fun sendTest() {
        val now = System.currentTimeMillis()
        val message = RelayMessage(
            messageId = messageId("SmsRelay Test", now, "测试推送"),
            sender = "SmsRelay Test",
            content = "测试推送",
            receivedAt = now
        )
        if (dao.insert(message) != -1L) {
            // A test verifies the configured endpoint even when SMS forwarding itself is switched off.
            deliverNow(message, preferences.settings.first().copy(enabled = true))
        }
    }

    suspend fun retry(messageId: String, attempt: Int): Boolean {
        val message = dao.get(messageId) ?: return true
        val settings = preferences.settings.first()
        when (val result = send(message, settings)) {
            SendResult.Success -> {
                dao.updateStatus(messageId, DeliveryStatus.SENT)
                return true
            }
            is SendResult.Failure -> {
                dao.updateStatus(messageId, DeliveryStatus.FAILED, result.reason)
                return true
            }
            is SendResult.Retry -> {
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    dao.updateStatus(messageId, DeliveryStatus.FAILED, result.reason)
                    return true
                }
                dao.updateStatus(messageId, DeliveryStatus.RETRYING, result.reason)
                return false
            }
        }
    }

    private suspend fun deliverNow(message: RelayMessage, settings: RelaySettings) {
        when (val result = send(message, settings)) {
            SendResult.Success -> dao.updateStatus(message.messageId, DeliveryStatus.SENT)
            is SendResult.Failure -> dao.updateStatus(message.messageId, DeliveryStatus.FAILED, result.reason)
            is SendResult.Retry -> {
                dao.updateStatus(message.messageId, DeliveryStatus.RETRYING, result.reason)
                enqueueRetry(message.messageId)
            }
        }
    }

    private suspend fun send(message: RelayMessage, settings: RelaySettings): SendResult = withContext(Dispatchers.IO) {
        if (!settings.enabled) return@withContext SendResult.Retry("转发已关闭")
        val url = settings.endpoint.toHttpUrlOrNull()
            ?: return@withContext SendResult.Failure("请在首页输入有效的 HTTP 推送地址")
        val payload = JSONObject()
            .put("messageId", message.messageId)
            .put("deviceName", settings.deviceName)
            .put("sender", message.sender)
            .put("content", message.content)
            .put("receivedAt", Instant.ofEpochMilli(message.receivedAt).toString())
        if (isWeComRobotWebhook(url)) {
            payload
                .put("msgtype", "text")
                .put("text", JSONObject().put("content", weComText(message, settings)))
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful && response.code in 400..499 ->
                        SendResult.Failure("服务器返回 HTTP ${response.code}")
                    !response.isSuccessful -> SendResult.Retry("服务器返回 HTTP ${response.code}")
                    hasBusinessFailure(responseBody) ->
                        SendResult.Failure(weComError(responseBody))
                    else -> SendResult.Success
                }
            }
        } catch (exception: Exception) {
            SendResult.Retry(exception.message ?: "网络请求失败")
        }
    }

    private fun enqueueRetry(messageId: String) {
        val request = OneTimeWorkRequestBuilder<RetryWorker>()
            .setInputData(workDataOf(RetryWorker.MESSAGE_ID to messageId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "relay-$messageId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun messageId(sender: String, receivedAt: Long, content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$receivedAt|$content".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isWeComRobotWebhook(url: okhttp3.HttpUrl): Boolean =
        url.host == WE_COM_HOST && url.encodedPath == WE_COM_WEBHOOK_PATH

    private fun weComText(message: RelayMessage, settings: RelaySettings): String = buildString {
        append("SmsRelay")
        append("\n设备：").append(settings.deviceName)
        append("\n发送者：").append(message.sender)
        append("\n内容：").append(message.content)
        append("\n时间：").append(Instant.ofEpochMilli(message.receivedAt))
    }.take(2_000)

    private fun hasBusinessFailure(responseBody: String): Boolean = try {
        JSONObject(responseBody).optInt("errcode", 0) != 0
    } catch (_: Exception) {
        false
    }

    private fun weComError(responseBody: String): String = try {
        val response = JSONObject(responseBody)
        "推送服务返回 ${response.optInt("errcode")}: ${response.optString("errmsg", "请求失败")}".take(240)
    } catch (_: Exception) {
        "推送服务拒绝请求"
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 8
        private const val WE_COM_HOST = "qyapi.weixin.qq.com"
        private const val WE_COM_WEBHOOK_PATH = "/cgi-bin/webhook/send"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
