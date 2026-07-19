package com.iunclear.smsrelay

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val RelayBackground = Color(0xFFF5F7F8)
private val RelayInk = Color(0xFF15232D)
private val RelayTeal = Color(0xFF007C77)
private val RelayTealContainer = Color(0xFFD8F1ED)
private val RelayAmber = Color(0xFF9A6700)
private val RelayError = Color(0xFFBA1A1A)
private val RelayErrorContainer = Color(0xFFFFDAD6)

private val RelayColorScheme = lightColorScheme(
    primary = RelayTeal,
    onPrimary = Color.White,
    primaryContainer = RelayTealContainer,
    onPrimaryContainer = Color(0xFF003C39),
    secondary = Color(0xFF425B69),
    onSecondary = Color.White,
    surface = Color(0xFFFFFFFF),
    onSurface = RelayInk,
    surfaceVariant = Color(0xFFE6ECEB),
    onSurfaceVariant = Color(0xFF4A5A60),
    outline = Color(0xFF73848A),
    error = RelayError,
    onError = Color.White,
    errorContainer = RelayErrorContainer,
    onErrorContainer = Color(0xFF410002),
    background = RelayBackground,
    onBackground = RelayInk
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(245, 247, 248)
        window.navigationBarColor = AndroidColor.rgb(245, 247, 248)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
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
    var deviceName by rememberSaveable { mutableStateOf(settings.deviceName) }
    var endpoint by rememberSaveable { mutableStateOf(settings.endpoint) }
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

    RelayTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Header(settings.enabled)
                RelayControlPanel(
                    enabled = settings.enabled,
                    endpointConfigured = endpoint.isNotBlank(),
                    onEnabledChanged = { enabled ->
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
                SectionHeading("接收模式")
                DefaultSmsPanel(
                    isDefaultSmsApp = isDefaultSmsApp,
                    onSetDefault = {
                        context.defaultSmsRoleIntent()?.let(defaultSmsRole::launch)
                    }
                )
                SectionHeading("推送配置")
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名称") },
                    supportingText = { Text("用于区分推送来源") },
                    singleLine = true,
                    shape = RelayShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("推送地址") },
                    placeholder = { Text("https://example.com/sms") },
                    supportingText = { Text("仅保存到本机") },
                    singleLine = true,
                    shape = RelayShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            preferences.save(settings.enabled, deviceName, endpoint)
                            repository.sendTest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("发送测试推送") }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("后台电池设置") }

                SectionHeading(
                    title = "最近发送记录",
                    action = {
                        TextButton(
                            enabled = messages.isNotEmpty(),
                            onClick = { scope.launch { repository.clearHistory() } }
                        ) { Text("清除记录") }
                    }
                )
                if (messages.isEmpty()) {
                    EmptyHistory()
                } else {
                    messages.forEach { message -> MessageRow(message) }
                }
            }
        }
    }
}

@Composable
private fun Header(enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SmsRelay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("短信转发", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusBadge(if (enabled) "已启用" else "已暂停", if (enabled) RelayTeal else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RelayControlPanel(
    enabled: Boolean,
    endpointConfigured: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("短信转发", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "正在监听新短信" else "当前不会发送推送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChanged)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    if (endpointConfigured) "推送地址已配置" else "待配置推送地址",
                    if (endpointConfigured) RelayTeal else RelayAmber
                )
            }
        }
    }
}

@Composable
private fun DefaultSmsPanel(isDefaultSmsApp: Boolean, onSetDefault: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShape,
        color = if (isDefaultSmsApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("默认短信应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusBadge(
                    if (isDefaultSmsApp) "已启用" else "普通模式",
                    if (isDefaultSmsApp) RelayTeal else RelayAmber
                )
            }
            Text(
                if (isDefaultSmsApp) "新短信会先写入系统收件箱，再进行转发。"
                else "部分验证码可能由系统定向交给所属应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isDefaultSmsApp) {
                TextButton(onClick = onSetDefault, modifier = Modifier.align(Alignment.End)) {
                    Text("设为默认短信应用")
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
}

@Composable
private fun EmptyHistory() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            "暂无发送记录",
            modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
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
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RelayShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = message.sender,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(statusText, statusColor)
            }
            Text(message.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(message.receivedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.status == DeliveryStatus.FAILED && !message.lastError.isNullOrBlank()) {
                Text(
                    message.lastError,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        shape = RelayShape,
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SendMessageApp(initialRecipient: String, initialContent: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recipient by remember { mutableStateOf(initialRecipient) }
    var content by remember { mutableStateOf(initialContent) }
    var status by remember { mutableStateOf<String?>(null) }

    RelayTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("发送短信", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("收件人号码") },
                    singleLine = true,
                    shape = RelayShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("短信内容") },
                    shape = RelayShape,
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape
                ) { Text("发送短信") }
                status?.let { StatusBadge(it, if (it == "已发送") RelayTeal else RelayError) }
            }
        }
    }
}

@Composable
private fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RelayColorScheme, content = content)
}

private val RelayShape = RoundedCornerShape(8.dp)

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
