package dev.chaingenhash.firefly.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.chaingenhash.firefly.data.ThresholdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Firefly"

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
                }

                when {
                    enabled == null ->
                        Log.w(TAG, "Timed out reading monitor state after boot; not resuming")

                    !enabled ->
                        Log.w(TAG, "Monitoring was off before reboot; skipping resume")

                    else -> {
                        // A boot-time foreground start can land outside the platform's
                        // exemption window; failing to resume monitoring beats crashing.
                        runCatching { BatteryMonitorService.start(appContext) }
                            .onSuccess { Log.i(TAG, "Resumed battery monitoring after boot") }
                            .onFailure {
                                Log.w(TAG, "Failed to start BatteryMonitorService after boot", it)
                            }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
