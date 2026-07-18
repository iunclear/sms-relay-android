package com.iunclear.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Declared to meet the Android default-SMS role contract. SMS relay remains text-SMS only.
    }
}
