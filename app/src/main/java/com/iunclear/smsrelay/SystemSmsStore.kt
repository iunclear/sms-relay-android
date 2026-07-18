package com.iunclear.smsrelay

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony

class SystemSmsStore(private val context: Context) {
    fun storeIncoming(sender: String, content: String, receivedAt: Long): Boolean = try {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, content)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.DATE_SENT, receivedAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
    } catch (_: SecurityException) {
        false
    }

    fun storeSent(recipient: String, content: String, sentAt: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, recipient)
            put(Telephony.Sms.BODY, content)
            put(Telephony.Sms.DATE, sentAt)
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }
}
