package com.iunclear.smsrelay

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipient = intent?.data?.schemeSpecificPart.orEmpty()
        val content = intent?.getStringExtra("sms_body").orEmpty()
        setContent {
            if (recipient.isBlank()) SmsRelayApp() else SendMessageApp(recipient, content)
        }
    }
}

@Composable
private fun SmsRelayApp() {
    val context = LocalContext.current
    val repository = remember { MessageRepository(context.applicationContext) }
    val preferences = remember { AppPreferences(context.applicationContext) }
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = RelaySettings())
    val messages by repository.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var endpoint by remember { mutableStateOf(settings.endpoint) }
    var isDefaultSmsApp by remember { mutableStateOf(context.isDefaultSmsApp()) }

    LaunchedEffect(settings.deviceName, settings.endpoint) {
        deviceName = settings.deviceName
        endpoint = settings.endpoint
    }

    val saveSettings = { enabled: Boolean ->
        scope.launch { preferences.save(enabled, deviceName, endpoint) }
    }
    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveSettings(true)
    }
    val defaultSmsRole = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSmsApp = context.isDefaultSmsApp()
        if (isDefaultSmsApp) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                saveSettings(true)
            } else {
                smsPermission.launch(Manifest.permission.RECEIVE_SMS)
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SmsRelay",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用短信转发", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                saveSettings(false)
                            } else if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECEIVE_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                saveSettings(true)
                            } else {
                                smsPermission.launch(Manifest.permission.RECEIVE_SMS)
                            }
                        }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("验证码接收模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isDefaultSmsApp) {
                            "默认短信应用已启用：新短信会写入系统收件箱后转发。"
                        } else {
                            "普通监听模式：部分验证码可能被系统定向交给其所属应用。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!isDefaultSmsApp) {
                        Button(
                            onClick = {
                                context.defaultSmsRoleIntent()?.let(defaultSmsRole::launch)
                            }
                        ) { Text("设为默认短信应用") }
                    }
                }
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("推送地址") },
                    placeholder = { Text("https://example.com/sms") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            preferences.save(settings.enabled, deviceName, endpoint)
                            repository.sendTest()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier.widthIn(min = 128.dp)
                ) { Text("测试推送") }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }
                ) { Text("后台电池设置") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("最近发送记录", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        enabled = messages.isNotEmpty(),
                        onClick = { scope.launch { repository.clearHistory() } }
                    ) { Text("清除") }
                }
                if (messages.isEmpty()) {
                    Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    messages.forEach { message ->
                        MessageRow(message)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: RelayMessage) {
    val statusText = when (message.status) {
        DeliveryStatus.SENT -> "发送成功"
        DeliveryStatus.RETRYING, DeliveryStatus.PENDING -> "等待重试"
        DeliveryStatus.FAILED -> "发送失败"
    }
    val statusColor = when (message.status) {
        DeliveryStatus.SENT -> MaterialTheme.colorScheme.primary
        DeliveryStatus.RETRYING, DeliveryStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        DeliveryStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = message.sender,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
        }
        Text(message.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(message.receivedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (message.status == DeliveryStatus.FAILED && !message.lastError.isNullOrBlank()) {
            Text(message.lastError, style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
    }
}

@Composable
private fun SendMessageApp(initialRecipient: String, initialContent: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recipient by remember { mutableStateOf(initialRecipient) }
    var content by remember { mutableStateOf(initialContent) }
    var status by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("发送短信", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("收件人号码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("短信内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    SmsSender(context.applicationContext).send(recipient, content)
                                }
                            }
                            status = result.fold(
                                onSuccess = { "已发送" },
                                onFailure = { it.message ?: "发送失败" }
                            )
                        }
                    }
                ) { Text("发送") }
                status?.let { Text(it) }
            }
        }
    }
}

private fun Context.isDefaultSmsApp(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
} else {
    Telephony.Sms.getDefaultSmsPackage(this) == packageName
}

private fun Context.defaultSmsRoleIntent(): Intent? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
        getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) && !it.isRoleHeld(RoleManager.ROLE_SMS) }
            ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }
    else -> Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
}
