package com.tigerworkshop.homepanel

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tigerworkshop.homepanel.night.NightController
import kotlinx.coroutines.launch
import com.tigerworkshop.homepanel.ui.HomePanelTheme
import com.tigerworkshop.homepanel.ui.PanelScreen
import com.tigerworkshop.homepanel.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSleep: Runnable? = null
    private lateinit var vm: PanelViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display when launched by the morning alarm.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        vm = ViewModelProvider(this)[PanelViewModel::class.java]

        // Keep the screen awake only when enabled; otherwise the system timeout applies.
        applyKeepAwake(vm.config.value.keepAwake)
        lifecycleScope.launch {
            vm.config.collect { applyKeepAwake(it.keepAwake) }
        }

        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            HomePanelTheme {
                var showSettings by remember { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(vm, onClose = { showSettings = false })
                } else {
                    PanelScreen(vm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        // If the panel is opened during the night window, glance briefly then sleep.
        if (!this::vm.isInitialized) return
        val cfg = vm.config.value
        cancelPendingSleep()
        if (cfg.nightEnabled && cfg.resleepSeconds > 0 &&
            NightController.isNightNow(cfg) && NightController.isDeviceAdminActive(this)
        ) {
            pendingSleep = Runnable { NightController.sleepNow(this) }.also {
                handler.postDelayed(it, cfg.resleepSeconds * 1000L)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cancelPendingSleep()
    }

    private fun cancelPendingSleep() {
        pendingSleep?.let { handler.removeCallbacks(it) }
        pendingSleep = null
    }

    private fun applyKeepAwake(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
