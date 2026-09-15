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

/** Restarts monitoring after a reboot, but only if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (ThresholdRepository(appContext).monitorState.first().monitoringEnabled) {
                    BatteryMonitorService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
