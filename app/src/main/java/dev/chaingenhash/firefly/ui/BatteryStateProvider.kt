package dev.chaingenhash.firefly.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.batteryStateFrom
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the current battery state synchronously from the sticky broadcast, without
 * registering a receiver. Used where a level is needed before the flow has emitted.
 */
fun currentBatteryState(context: Context): BatteryState? =
    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.readBatteryState()

/** Emits the current battery reading, then every change, for as long as it is collected. */
fun batteryStateFlow(context: Context): Flow<BatteryState> = callbackFlow {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.readBatteryState()?.let { trySend(it) }
        }
    }

    // The battery broadcast is sticky, so this returns the current reading immediately.
    currentBatteryState(context)?.let { trySend(it) }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

    awaitClose { context.unregisterReceiver(receiver) }
}

private fun Intent.readBatteryState(): BatteryState? = batteryStateFrom(
    level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
    scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
    plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
)
