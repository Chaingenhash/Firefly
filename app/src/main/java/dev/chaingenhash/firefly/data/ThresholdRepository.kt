package dev.chaingenhash.firefly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.MonitorState
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.domain.ThresholdEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY_THRESHOLDS = stringPreferencesKey("thresholds")
private val KEY_MONITOR_STATE = stringPreferencesKey("monitor_state")

/**
 * Stores thresholds and monitor state.
 *
 * The [store] is a constructor parameter rather than a property delegate so the
 * transactional logic below can be exercised by a plain JVM test over a temp file.
 * Production code uses the [Context] constructor, which resolves the single
 * app-scoped store.
 */
class ThresholdRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.fireflyDataStore)

    val thresholds: Flow<List<Threshold>> =
        store.data.map { ThresholdCodec.decodeThresholds(it[KEY_THRESHOLDS]) }

    val monitorState: Flow<MonitorState> =
        store.data.map { ThresholdCodec.decodeMonitorState(it[KEY_MONITOR_STATE]) }

    /**
     * Adds or replaces [threshold]. Arm state is recomputed from [currentLevel], so an
     * edited level or direction cannot leave a threshold stuck disarmed.
     */
    suspend fun upsert(threshold: Threshold, currentLevel: Int) {
        store.edit { prefs ->
            val current = ThresholdCodec.decodeThresholds(prefs[KEY_THRESHOLDS])
            val updated = current.filterNot { it.id == threshold.id } + threshold
            prefs[KEY_THRESHOLDS] = ThresholdCodec.encodeThresholds(updated)

            val state = ThresholdCodec.decodeMonitorState(prefs[KEY_MONITOR_STATE])
            val armed = if (threshold.enabled &&
                ThresholdEvaluator.isArmedOnCreate(threshold, currentLevel)
            ) {
                state.armedIds + threshold.id
            } else {
                state.armedIds - threshold.id
            }
            prefs[KEY_MONITOR_STATE] =
                ThresholdCodec.encodeMonitorState(state.copy(armedIds = armed))
        }
    }

    suspend fun delete(id: String) {
        store.edit { prefs ->
            val remaining = ThresholdCodec.decodeThresholds(prefs[KEY_THRESHOLDS])
                .filterNot { it.id == id }
            prefs[KEY_THRESHOLDS] = ThresholdCodec.encodeThresholds(remaining)

            val state = ThresholdCodec.decodeMonitorState(prefs[KEY_MONITOR_STATE])
            prefs[KEY_MONITOR_STATE] =
                ThresholdCodec.encodeMonitorState(state.copy(armedIds = state.armedIds - id))
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean, currentLevel: Int) {
        val threshold = thresholds.first().firstOrNull { it.id == id } ?: return
        upsert(threshold.copy(enabled = enabled), currentLevel)
    }

    /**
     * Evaluates [current] against the stored thresholds and arm state inside one
     * transaction, persists the resulting state, and returns the thresholds that fired.
     *
     * Reading, evaluating and writing in a single [DataStore.edit] is what stops a
     * concurrent UI write from being clobbered: without it, an evaluation that began
     * before the user switched monitoring off would write `monitoringEnabled = true`
     * back over their change.
     *
     * Returns nothing while monitoring is off. The service unregisters its receiver in
     * `onDestroy`, so a broadcast can still arrive just after the user switches
     * monitoring off; without this guard that broadcast would write `lastLevel` back
     * over the nulls [setMonitoringEnabled] just wrote, and re-enabling later could
     * alert for a crossing that happened while monitoring was off.
     */
    suspend fun evaluateAndCommit(current: BatteryState): List<Threshold> {
        var fired: List<Threshold> = emptyList()

        store.edit { prefs ->
            val state = ThresholdCodec.decodeMonitorState(prefs[KEY_MONITOR_STATE])
            if (!state.monitoringEnabled) return@edit

            val thresholds = ThresholdCodec.decodeThresholds(prefs[KEY_THRESHOLDS])
            val evaluation = ThresholdEvaluator.evaluate(state, current, thresholds)
            fired = evaluation.fired

            // ACTION_BATTERY_CHANGED also fires on temperature and voltage changes, far
            // more often than the level moves. Skipping the unchanged write keeps this
            // from rewriting the preferences file many times a minute.
            if (evaluation.state != state) {
                prefs[KEY_MONITOR_STATE] = ThresholdCodec.encodeMonitorState(evaluation.state)
            }
        }

        return fired
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { prefs ->
            val state = ThresholdCodec.decodeMonitorState(prefs[KEY_MONITOR_STATE])
            val cleared = if (enabled) {
                state.copy(monitoringEnabled = true)
            } else {
                // Forget the last reading, so switching monitoring back on is a cold
                // start and cannot alert for a crossing that happened while it was off.
                state.copy(monitoringEnabled = false, lastLevel = null, lastPlugged = null)
            }
            prefs[KEY_MONITOR_STATE] = ThresholdCodec.encodeMonitorState(cleared)
        }
    }
}
