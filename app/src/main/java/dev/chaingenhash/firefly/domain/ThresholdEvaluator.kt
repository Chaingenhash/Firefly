package dev.chaingenhash.firefly.domain

/**
 * Decides which thresholds fire on a battery reading.
 *
 * Each enabled threshold is either armed (eligible to fire) or disarmed. Firing disarms a
 * threshold; it re-arms only once the level retreats past [HYSTERESIS] points, or when the
 * plugged state flips and a new charge or discharge cycle begins. Without this, a battery
 * resting on the threshold level would alert on every wobble.
 */
object ThresholdEvaluator {

    /** Points the level must retreat before a fired threshold can fire again. */
    const val HYSTERESIS = 3

    fun evaluate(
        state: MonitorState,
        current: BatteryState,
        thresholds: List<Threshold>,
    ): Evaluation {
        val plugFlipped = state.lastPlugged != null && state.lastPlugged != current.plugged
        val previous = state.lastLevel

        val fired = mutableListOf<Threshold>()
        val armedIds = mutableSetOf<String>()

        thresholds.filter { it.enabled }.forEach { threshold ->
            val armed = plugFlipped || threshold.id in state.armedIds
            when {
                armed && previous != null && crossed(threshold, previous, current) ->
                    fired += threshold                          // fires, and stays disarmed
                armed -> armedIds += threshold.id
                retreated(threshold, current.level) -> armedIds += threshold.id
            }
        }

        return Evaluation(
            fired = fired,
            state = state.copy(
                lastLevel = current.level,
                lastPlugged = current.plugged,
                armedIds = armedIds,
            ),
        )
    }

    /** Whether a newly created or edited threshold starts out eligible to fire. */
    fun isArmedOnCreate(threshold: Threshold, level: Int): Boolean =
        when (threshold.direction) {
            Direction.CHARGING_UP -> level < threshold.level
            Direction.DISCHARGING_DOWN -> level > threshold.level
        }

    private fun crossed(threshold: Threshold, previous: Int, current: BatteryState): Boolean =
        when (threshold.direction) {
            Direction.CHARGING_UP ->
                current.plugged && previous < threshold.level && current.level >= threshold.level

            Direction.DISCHARGING_DOWN ->
                !current.plugged && previous > threshold.level && current.level <= threshold.level
        }

    private fun retreated(threshold: Threshold, level: Int): Boolean =
        when (threshold.direction) {
            Direction.CHARGING_UP -> level <= threshold.level - HYSTERESIS
            Direction.DISCHARGING_DOWN -> level >= threshold.level + HYSTERESIS
        }
}
