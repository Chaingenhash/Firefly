package dev.chaingenhash.firefly.data

import dev.chaingenhash.firefly.domain.MonitorState
import dev.chaingenhash.firefly.domain.Threshold
import kotlinx.serialization.json.Json

/**
 * Serializes the stored state. Decoding never throws: a store written by an older build,
 * or corrupted on disk, falls back to defaults rather than crashing the service on boot.
 */
object ThresholdCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeThresholds(thresholds: List<Threshold>): String =
        json.encodeToString(thresholds)

    fun decodeThresholds(raw: String?): List<Threshold> =
        if (raw == null) emptyList() else runCatching {
            json.decodeFromString<List<Threshold>>(raw)
        }.getOrDefault(emptyList())

    fun encodeMonitorState(state: MonitorState): String =
        json.encodeToString(state)

    fun decodeMonitorState(raw: String?): MonitorState =
        if (raw == null) MonitorState() else runCatching {
            json.decodeFromString<MonitorState>(raw)
        }.getOrDefault(MonitorState())
}
