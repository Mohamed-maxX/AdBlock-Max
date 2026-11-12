package com.max.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class VpnActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
