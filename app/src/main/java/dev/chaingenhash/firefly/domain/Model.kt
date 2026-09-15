package dev.chaingenhash.firefly.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Direction { CHARGING_UP, DISCHARGING_DOWN }

@Serializable
data class Threshold(
    val id: String,
    val level: Int,
    val direction: Direction,
    val enabled: Boolean = true,
    val label: String? = null,
)

/** A battery reading. [plugged] mirrors `EXTRA_PLUGGED != 0`, not `EXTRA_STATUS`. */
data class BatteryState(
    val level: Int,
    val plugged: Boolean,
)

@Serializable
data class MonitorState(
    val lastLevel: Int? = null,
    val lastPlugged: Boolean? = null,
    val armedIds: Set<String> = emptySet(),
    val monitoringEnabled: Boolean = false,
)

data class Evaluation(
    val fired: List<Threshold>,
    val state: MonitorState,
)

/**
 * Converts the raw extras of `ACTION_BATTERY_CHANGED` into a [BatteryState].
 * Returns null when the broadcast carries no usable reading.
 */
fun batteryStateFrom(level: Int, scale: Int, plugged: Int): BatteryState? {
    if (level < 0 || scale <= 0) return null
    return BatteryState(level = level * 100 / scale, plugged = plugged != 0)
}
