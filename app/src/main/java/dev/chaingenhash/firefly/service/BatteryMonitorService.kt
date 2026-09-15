package dev.chaingenhash.firefly.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.chaingenhash.firefly.data.ThresholdRepository
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.batteryStateFrom
import dev.chaingenhash.firefly.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "Firefly"

/**
 * Listens to `ACTION_BATTERY_CHANGED` for as long as monitoring is on.
 *
 * The receiver is registered in code rather than in the manifest: since API 26 the system
 * refuses manifest-declared receivers for this action.
 */
class BatteryMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private lateinit var repository: ThresholdRepository

    /** Last reading posted to the status notification, to avoid redundant re-posts. */
    @Volatile
    private var shown: BatteryState? = null

    /** Whether [receiver] is currently registered. Touched only on the main thread. */
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = batteryStateFrom(
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            ) ?: return

            scope.launch { mutex.withLock { handle(state) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ThresholdRepository(this)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifications.STATUS_NOTIFICATION_ID,
            Notifications.buildStatus(this, shown),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        // A redelivered start must not double-register. Tracking registration beats
        // catching the failure: the first start always "fails" to unregister, and
        // logging that stack trace every time buries the failures worth seeing.
        unregisterReceiverIfNeeded()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        return START_STICKY
    }

    private suspend fun handle(state: BatteryState) {
        // Evaluating and persisting in one transaction keeps a concurrent UI write —
        // switching monitoring off, adding a threshold — from being overwritten. The
        // cost is that alerts are posted after the commit rather than before, so a
        // process death in that window loses an alert rather than repeating one. The
        // overwrite races happen on ordinary taps; this window needs a crash between
        // two adjacent statements.
        val fired = repository.evaluateAndCommit(state)

        if (fired.isNotEmpty()) {
            Log.d(TAG, "Fired: ${fired.map { it.id to it.level }}")
        }
        fired.forEach { Notifications.alert(applicationContext, it, state.level) }

        // ACTION_BATTERY_CHANGED fires far more often than the level changes.
        if (state != shown) {
            shown = state
            NotificationManagerCompat.from(applicationContext).notify(
                Notifications.STATUS_NOTIFICATION_ID,
                Notifications.buildStatus(applicationContext, state),
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiverIfNeeded()
        scope.cancel()
        super.onDestroy()
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BatteryMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }
}
