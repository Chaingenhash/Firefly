package dev.chaingenhash.firefly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.chaingenhash.firefly.domain.MonitorState
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.domain.ThresholdEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "firefly")

private val KEY_THRESHOLDS = stringPreferencesKey("thresholds")
private val KEY_MONITOR_STATE = stringPreferencesKey("monitor_state")

class ThresholdRepository(context: Context) {

    private val store = context.applicationContext.dataStore

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

    suspend fun saveMonitorState(state: MonitorState) {
        store.edit { it[KEY_MONITOR_STATE] = ThresholdCodec.encodeMonitorState(state) }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { prefs ->
            val state = ThresholdCodec.decodeMonitorState(prefs[KEY_MONITOR_STATE])
            prefs[KEY_MONITOR_STATE] = ThresholdCodec.encodeMonitorState(
                state.copy(monitoringEnabled = enabled),
            )
        }
    }
}
