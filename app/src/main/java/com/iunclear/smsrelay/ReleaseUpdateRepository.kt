package com.iunclear.smsrelay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Loading : UpdateCheckState
    data object Latest : UpdateCheckState
    data class Available(val release: ReleaseUpdate) : UpdateCheckState
    data class Failed(val reason: String) : UpdateCheckState
}

data class ReleaseUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?
)

class ReleaseUpdateRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateCheckState = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SmsRelay-Android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckState.Failed("GitHub 返回 HTTP ${response.code}")
                }
                val payload = JSONObject(response.body?.string().orEmpty())
                val version = payload.optString("tag_name").removePrefix("v")
                if (version.isBlank()) {
                    return@withContext UpdateCheckState.Failed("未找到发布版本")
                }
                if (!isVersionNewer(version, currentVersion)) {
                    return@withContext UpdateCheckState.Latest
                }
                val assets = payload.optJSONArray("assets")
                var apkUrl: String? = null
                for (index in 0 until (assets?.length() ?: 0)) {
                    val asset = assets?.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith("-optimized.apk")) {
                        apkUrl = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
                UpdateCheckState.Available(
                    ReleaseUpdate(
                        version = version,
                        releaseUrl = payload.optString("html_url"),
                        apkUrl = apkUrl
                    )
                )
            }
        } catch (exception: Exception) {
            UpdateCheckState.Failed(exception.message ?: "无法连接 GitHub")
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/iunclear/sms-relay-android/releases/latest"

        internal fun isVersionNewer(latest: String, current: String): Boolean {
            val latestParts = latest.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (latestPart != currentPart) return latestPart > currentPart
            }
            return false
        }
    }
}
