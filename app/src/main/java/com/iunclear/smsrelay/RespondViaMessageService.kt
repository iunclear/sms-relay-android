package com.iunclear.smsrelay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = intent?.data?.schemeSpecificPart.orEmpty()
        val content = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (recipient.isNotBlank() && content.isNotBlank()) {
                    SmsSender(applicationContext).send(recipient, content)
                }
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
