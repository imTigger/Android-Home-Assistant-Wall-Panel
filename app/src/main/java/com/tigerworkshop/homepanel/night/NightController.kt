package com.tigerworkshop.homepanel.night

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.tigerworkshop.homepanel.MainActivity
import com.tigerworkshop.homepanel.data.Config
import java.util.Calendar

/**
 * Owns the day/night schedule. At night-start it force-locks the screen (true
 * screen-off via device admin); at night-end it wakes the screen and shows the
 * panel. Transitions are driven by exact alarms so they fire even when the app
 * is idle / the screen is off.
 */
object NightController {

    const val ACTION_SLEEP = "com.tigerworkshop.homepanel.SLEEP"
    const val ACTION_WAKE = "com.tigerworkshop.homepanel.WAKE"
    private const val REQ_SLEEP = 1001
    private const val REQ_WAKE = 1002
    private const val TAG = "NightController"

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(PanelDeviceAdminReceiver.componentName(context))
    }

    /** True if [minutesNow] falls inside the night window (handles midnight wrap). */
    fun isNight(cfg: Config, minutesNow: Int): Boolean {
        if (!cfg.nightEnabled) return false
        val start = cfg.nightStartMinutes
        val end = cfg.nightEndMinutes
        return if (start <= end) minutesNow in start until end
        else minutesNow >= start || minutesNow < end
    }

    fun isNightNow(cfg: Config): Boolean = isNight(cfg, currentMinutes())

    private fun currentMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** (Re)schedule the next sleep and wake alarms. */
    fun schedule(context: Context, cfg: Config) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, am)
        if (!cfg.nightEnabled) return
        setDailyAlarm(context, am, REQ_SLEEP, ACTION_SLEEP, cfg.nightStartMinutes)
        setDailyAlarm(context, am, REQ_WAKE, ACTION_WAKE, cfg.nightEndMinutes)
    }

    fun cancel(context: Context, am: AlarmManager? = null) {
        val mgr = am ?: context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr.cancel(pendingIntent(context, REQ_SLEEP, ACTION_SLEEP))
        mgr.cancel(pendingIntent(context, REQ_WAKE, ACTION_WAKE))
    }

    private fun setDailyAlarm(context: Context, am: AlarmManager, req: Int, action: String, minutes: Int) {
        val triggerAt = nextOccurrence(minutes)
        val pi = pendingIntent(context, req, action)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (se: SecurityException) {
            // Fall back to inexact if exact alarms are not permitted.
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Log.w(TAG, "exact alarm denied, using inexact", se)
        }
    }

    private fun nextOccurrence(minutes: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun pendingIntent(context: Context, req: Int, action: String) =
        android.app.PendingIntent.getBroadcast(
            context, req,
            Intent(context, NightAlarmReceiver::class.java).setAction(action),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    /** Turn the screen off now (requires device admin). */
    fun sleepNow(context: Context) {
        if (!isDeviceAdminActive(context)) {
            Log.w(TAG, "sleepNow ignored: device admin not active")
            return
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            dpm.lockNow()
        } catch (e: SecurityException) {
            Log.w(TAG, "lockNow failed", e)
        }
    }

    /** Wake the screen and bring the panel to the front. */
    @Suppress("DEPRECATION")
    fun wakeNow(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "homepanel:wake",
        )
        try {
            wl.acquire(10_000L)
        } catch (_: Exception) {
        }
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.w(TAG, "wake launch failed", e)
        }
    }
}
