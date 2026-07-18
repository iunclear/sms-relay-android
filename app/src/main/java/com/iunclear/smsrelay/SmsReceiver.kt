package com.iunclear.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isDefaultSmsDelivery = intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        if (!isDefaultSmsDelivery && intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Android puts every PDU for this delivery in the same intent; joining them preserves multipart SMS.
        val sender = messages.first().originatingAddress ?: "未知号码"
        val content = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.minOf { it.timestampMillis }
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (isDefaultSmsDelivery) {
                    val stored = SystemSmsStore(context.applicationContext)
                        .storeIncoming(sender, content, receivedAt)
                    pendingResult.setResultCode(
                        if (stored) {
                            Telephony.Sms.Intents.RESULT_SMS_HANDLED
                        } else {
                            Telephony.Sms.Intents.RESULT_SMS_GENERIC_ERROR
                        }
                    )
                }
                MessageRepository(context.applicationContext).receive(sender, content, receivedAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
