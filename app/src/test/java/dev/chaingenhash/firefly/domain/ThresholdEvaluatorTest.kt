package dev.chaingenhash.firefly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEvaluatorTest {

    private val chargeAt80 = Threshold("charge80", 80, Direction.CHARGING_UP)
    private val drainAt20 = Threshold("drain20", 20, Direction.DISCHARGING_DOWN)

    private fun state(level: Int, plugged: Boolean, armed: Set<String>) =
        MonitorState(lastLevel = level, lastPlugged = plugged, armedIds = armed)

    @Test
    fun `charging up across the level fires once`() {
        val result = ThresholdEvaluator.evaluate(
            state = state(79, plugged = true, armed = setOf("charge80")),
            current = BatteryState(80, plugged = true),
            thresholds = listOf(chargeAt80),
        )

        assertEquals(listOf(chargeAt80), result.fired)
        assertFalse("charge80" in result.state.armedIds)
    }

    @Test
    fun `discharging down across the level fires once`() {
        val result = ThresholdEvaluator.evaluate(
            state = state(21, plugged = false, armed = setOf("drain20")),
            current = BatteryState(20, plugged = false),
            thresholds = listOf(drainAt20),
        )

        assertEquals(listOf(drainAt20), result.fired)
        assertFalse("drain20" in result.state.armedIds)
    }

    @Test
    fun `hovering at the level fires only once`() {
        var monitor = state(79, plugged = true, armed = setOf("charge80"))
        val levels = listOf(80, 79, 80, 79, 80)
        var fireCount = 0

        levels.forEach { level ->
            val result = ThresholdEvaluator.evaluate(
                state = monitor,
                current = BatteryState(level, plugged = true),
                thresholds = listOf(chargeAt80),
            )
            fireCount += result.fired.size
            monitor = result.state
        }

        assertEquals(1, fireCount)
    }

    @Test
    fun `retreating past the hysteresis band re-arms and the next crossing fires`() {
        var monitor = state(80, plugged = true, armed = emptySet())

        // 80 -> 77 is a 3-point retreat, which re-arms.
        monitor = ThresholdEvaluator.evaluate(
            monitor, BatteryState(77, plugged = true), listOf(chargeAt80),
        ).state
        assertTrue("charge80" in monitor.armedIds)

        val result = ThresholdEvaluator.evaluate(
            monitor, BatteryState(81, plugged = true), listOf(chargeAt80),
        )
        assertEquals(listOf(chargeAt80), result.fired)
    }

    @Test
    fun `a two point retreat does not re-arm`() {
        val monitor = state(80, plugged = true, armed = emptySet())

        val result = ThresholdEvaluator.evaluate(
            monitor, BatteryState(78, plugged = true), listOf(chargeAt80),
        )

        assertFalse("charge80" in result.state.armedIds)
    }

    @Test
    fun `unplugging re-arms a charging threshold`() {
        val monitor = state(85, plugged = true, armed = emptySet())

        val result = ThresholdEvaluator.evaluate(
            monitor, BatteryState(85, plugged = false), listOf(chargeAt80),
        )

        assertTrue("charge80" in result.state.armedIds)
        assertTrue(result.fired.isEmpty())
    }

    @Test
    fun `a charging threshold does not fire while unplugged`() {
        val result = ThresholdEvaluator.evaluate(
            state(79, plugged = false, armed = setOf("charge80")),
            BatteryState(80, plugged = false),
            listOf(chargeAt80),
        )

        assertTrue(result.fired.isEmpty())
    }

    @Test
    fun `a discharging threshold does not fire while plugged in`() {
        val result = ThresholdEvaluator.evaluate(
            state(21, plugged = true, armed = setOf("drain20")),
            BatteryState(20, plugged = true),
            listOf(drainAt20),
        )

        assertTrue(result.fired.isEmpty())
    }

    @Test
    fun `cold start fires nothing and records the reading`() {
        val result = ThresholdEvaluator.evaluate(
            state = MonitorState(armedIds = setOf("charge80", "drain20")),
            current = BatteryState(15, plugged = false),
            thresholds = listOf(chargeAt80, drainAt20),
        )

        assertTrue(result.fired.isEmpty())
        assertEquals(15, result.state.lastLevel)
        assertEquals(false, result.state.lastPlugged)
    }

    @Test
    fun `a disabled threshold never fires and keeps no arm state`() {
        val disabled = chargeAt80.copy(enabled = false)

        val result = ThresholdEvaluator.evaluate(
            state(79, plugged = true, armed = setOf("charge80")),
            BatteryState(80, plugged = true),
            listOf(disabled),
        )

        assertTrue(result.fired.isEmpty())
        assertFalse("charge80" in result.state.armedIds)
    }

    @Test
    fun `every threshold crossed in one jump fires`() {
        val fifty = Threshold("t50", 50, Direction.DISCHARGING_DOWN)
        val thirty = Threshold("t30", 30, Direction.DISCHARGING_DOWN)

        val result = ThresholdEvaluator.evaluate(
            state(100, plugged = false, armed = setOf("t50", "t30", "drain20")),
            BatteryState(15, plugged = false),
            listOf(fifty, thirty, drainAt20),
        )

        assertEquals(setOf("t50", "t30", "drain20"), result.fired.map { it.id }.toSet())
    }

    @Test
    fun `a new threshold above the current level starts armed`() {
        assertTrue(ThresholdEvaluator.isArmedOnCreate(chargeAt80, level = 50))
        assertFalse(ThresholdEvaluator.isArmedOnCreate(chargeAt80, level = 90))
    }

    @Test
    fun `a new discharging threshold below the current level starts armed`() {
        assertTrue(ThresholdEvaluator.isArmedOnCreate(drainAt20, level = 50))
        assertFalse(ThresholdEvaluator.isArmedOnCreate(drainAt20, level = 10))
    }

    @Test
    fun `battery state is scaled to a percentage`() {
        assertEquals(BatteryState(50, plugged = true), batteryStateFrom(50, 100, 1))
        assertEquals(BatteryState(50, plugged = false), batteryStateFrom(100, 200, 0))
    }

    @Test
    fun `an unusable battery broadcast yields null`() {
        assertEquals(null, batteryStateFrom(-1, 100, 0))
        assertEquals(null, batteryStateFrom(50, 0, 0))
    }
}
