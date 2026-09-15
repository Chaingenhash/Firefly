package dev.chaingenhash.firefly.notify

import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    @Test
    fun `a labelled threshold uses its own label`() {
        val threshold = Threshold("a", 80, Direction.CHARGING_UP, label = "Unplug the laptop")

        assertEquals("Unplug the laptop", alertTitle(threshold, 80))
    }

    @Test
    fun `an unlabelled charging threshold gets a generated title`() {
        val threshold = Threshold("a", 80, Direction.CHARGING_UP)

        assertEquals("Battery reached 81% — time to unplug", alertTitle(threshold, 81))
    }

    @Test
    fun `an unlabelled discharging threshold gets a generated title`() {
        val threshold = Threshold("b", 20, Direction.DISCHARGING_DOWN)

        assertEquals("Battery down to 19% — time to charge", alertTitle(threshold, 19))
    }

    @Test
    fun `status text shows level and charging state`() {
        assertEquals("82% · Charging", statusText(BatteryState(82, plugged = true)))
        assertEquals("43% · On battery", statusText(BatteryState(43, plugged = false)))
    }

    @Test
    fun `status text before the first reading says so`() {
        assertEquals("Waiting for battery reading…", statusText(null))
    }
}
