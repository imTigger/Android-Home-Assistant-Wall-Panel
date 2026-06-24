package com.tigerworkshop.homepanel.night

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tigerworkshop.homepanel.data.Settings

/** Re-arm night alarms after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NightController.schedule(context, Settings(context).value)
        }
    }
}
