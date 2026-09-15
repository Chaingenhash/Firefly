package dev.chaingenhash.firefly.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.chaingenhash.firefly.data.ThresholdRepository
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.ThresholdEvaluator
import dev.chaingenhash.firefly.domain.batteryStateFrom
import dev.chaingenhash.firefly.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private var shown: BatteryState? = null

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
        runCatching { unregisterReceiver(receiver) }   // no-op unless already registered
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return START_STICKY
    }

    private suspend fun handle(state: BatteryState) {
        val thresholds = repository.thresholds.first()
        val monitorState = repository.monitorState.first()

        val evaluation = ThresholdEvaluator.evaluate(monitorState, state, thresholds)
        repository.saveMonitorState(evaluation.state)

        evaluation.fired.forEach { Notifications.alert(this, it, state.level) }

        // ACTION_BATTERY_CHANGED fires far more often than the level changes.
        if (state != shown) {
            shown = state
            NotificationManagerCompat.from(this).notify(
                Notifications.STATUS_NOTIFICATION_ID,
                Notifications.buildStatus(this, state),
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
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
