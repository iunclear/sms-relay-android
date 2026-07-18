package com.iunclear.smsrelay

import android.content.Context
import android.telephony.SmsManager

class SmsSender(private val context: Context) {
    @Suppress("DEPRECATION")
    fun send(recipient: String, content: String) {
        require(recipient.isNotBlank()) { "请输入收件人号码" }
        require(content.isNotBlank()) { "请输入短信内容" }

        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage(content)
        if (parts.size == 1) {
            manager.sendTextMessage(recipient, null, content, null, null)
        } else {
            manager.sendMultipartTextMessage(recipient, null, parts, null, null)
        }
        SystemSmsStore(context).storeSent(recipient, content)
    }
}
