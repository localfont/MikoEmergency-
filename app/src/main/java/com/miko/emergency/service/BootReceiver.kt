package com.miko.emergency.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miko.emergency.utils.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceManager.getInstance(context)
            if (prefs.autoStartOnBoot) {
                MeshForegroundService.start(context)
            }
        }
    }
}
