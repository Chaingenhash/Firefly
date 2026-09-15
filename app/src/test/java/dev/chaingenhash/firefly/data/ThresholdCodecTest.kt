package dev.chaingenhash.firefly.data

import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.MonitorState
import dev.chaingenhash.firefly.domain.Threshold
import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdCodecTest {

    @Test
    fun `thresholds survive a round trip`() {
        val thresholds = listOf(
            Threshold("a", 80, Direction.CHARGING_UP, enabled = true, label = "Unplug"),
            Threshold("b", 20, Direction.DISCHARGING_DOWN, enabled = false, label = null),
        )

        val decoded = ThresholdCodec.decodeThresholds(
            ThresholdCodec.encodeThresholds(thresholds),
        )

        assertEquals(thresholds, decoded)
    }

    @Test
    fun `monitor state survives a round trip`() {
        val state = MonitorState(
            lastLevel = 64,
            lastPlugged = true,
            armedIds = setOf("a", "b"),
            monitoringEnabled = true,
        )

        val decoded = ThresholdCodec.decodeMonitorState(
            ThresholdCodec.encodeMonitorState(state),
        )

        assertEquals(state, decoded)
    }

    @Test
    fun `an empty store decodes to defaults`() {
        assertEquals(emptyList<Threshold>(), ThresholdCodec.decodeThresholds(null))
        assertEquals(MonitorState(), ThresholdCodec.decodeMonitorState(null))
    }

    @Test
    fun `corrupt stored data decodes to defaults instead of throwing`() {
        assertEquals(emptyList<Threshold>(), ThresholdCodec.decodeThresholds("not json"))
        assertEquals(MonitorState(), ThresholdCodec.decodeMonitorState("{{{"))
    }
}
