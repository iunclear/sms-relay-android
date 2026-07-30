package com.iunclear.smsrelay

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SmsFolder { INBOX, SENT }

data class SystemSmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val folder: SmsFolder,
    val isRead: Boolean
)

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

    suspend fun listMessages(folder: SmsFolder, limit: Int = MAX_LIST_SIZE): List<SystemSmsMessage> =
        withContext(Dispatchers.IO) {
            val messageType = when (folder) {
                SmsFolder.INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
                SmsFolder.SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
            }
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                "${Telephony.Sms.TYPE} = ?",
                arrayOf(messageType.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add(
                            SystemSmsMessage(
                                id = cursor.getLong(idIndex),
                                address = cursor.getString(addressIndex).orEmpty().ifBlank { "未知号码" },
                                body = cursor.getString(bodyIndex).orEmpty(),
                                date = cursor.getLong(dateIndex),
                                folder = folder,
                                isRead = cursor.getInt(readIndex) == 1
                            )
                        )
                    }
                }
            }.orEmpty()
        }

    suspend fun markRead(messageId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString())
        )
    }

    private companion object {
        const val MAX_LIST_SIZE = 200
        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
    }
}
