package dev.chaingenhash.firefly.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.chaingenhash.firefly.data.ThresholdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Restarts monitoring after a reboot, but only if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // goAsync gives us roughly ten seconds; a cold boot under I/O contention
                // can make this read slow, and missing the window is better than being
                // killed mid-read.
                val enabled = withTimeoutOrNull(5_000) {
                    ThresholdRepository(appContext).monitorState.first().monitoringEnabled
                } ?: false

                if (enabled) {
                    // A boot-time foreground start can land outside the platform's
                    // exemption window; failing to resume monitoring beats crashing.
                    runCatching { BatteryMonitorService.start(appContext) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
