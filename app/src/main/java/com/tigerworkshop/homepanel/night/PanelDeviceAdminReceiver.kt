package com.tigerworkshop.homepanel.night

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/** Device admin so the app may force-lock (turn the screen off) at night. */
class PanelDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, PanelDeviceAdminReceiver::class.java)
    }
}
