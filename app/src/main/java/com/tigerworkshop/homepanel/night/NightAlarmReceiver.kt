package com.tigerworkshop.homepanel.night

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tigerworkshop.homepanel.data.Settings

/** Fires at night-start (sleep) and night-end (wake); reschedules the next day. */
class NightAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NightController.ACTION_SLEEP -> NightController.sleepNow(context)
            NightController.ACTION_WAKE -> NightController.wakeNow(context)
        }
        // Re-arm both alarms for the next occurrence.
        NightController.schedule(context, Settings(context).value)
    }
}
