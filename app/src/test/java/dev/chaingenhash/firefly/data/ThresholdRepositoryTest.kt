package dev.chaingenhash.firefly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.MonitorState
import dev.chaingenhash.firefly.domain.Threshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers the decisions that live in the repository rather than in the codec or the
 * evaluator: the transactional evaluate-and-commit, the write-skip, and the arm-state
 * recomputation. These are the parts no other test reaches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThresholdRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: ThresholdRepository

    private val chargeAt80 = Threshold("charge80", 80, Direction.CHARGING_UP)
    private val drainAt20 = Threshold("drain20", 20, Direction.DISCHARGING_DOWN)

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("test.preferences_pb")
        }
        repository = ThresholdRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a new threshold below the current level starts armed`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)

        assertTrue("charge80" in repository.monitorState.first().armedIds)
    }

    @Test
    fun `a new threshold already past its level starts disarmed`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 90)

        assertFalse("charge80" in repository.monitorState.first().armedIds)
    }

    @Test
    fun `disabling a threshold drops its arm state`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)

        repository.setEnabled("charge80", enabled = false, currentLevel = 50)

        assertFalse("charge80" in repository.monitorState.first().armedIds)
    }

    @Test
    fun `deleting a threshold leaves no arm state behind`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)

        repository.delete("charge80")

        val state = repository.monitorState.first()
        assertTrue(repository.thresholds.first().isEmpty())
        assertFalse("charge80" in state.armedIds)
    }

    @Test
    fun `evaluateAndCommit returns nothing while monitoring is off`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)
        // monitoringEnabled defaults to false — the user has not switched it on.

        val fired = repository.evaluateAndCommit(BatteryState(80, plugged = true))

        assertEquals(emptyList<Threshold>(), fired)
    }

    @Test
    fun `evaluateAndCommit does not touch the stored reading while monitoring is off`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)
        repository.setMonitoringEnabled(false)

        repository.evaluateAndCommit(BatteryState(80, plugged = true))

        // This is the regression guard: a broadcast arriving just after the user
        // switched monitoring off must not repopulate the reading that was cleared,
        // or re-enabling later would alert for a crossing that happened while off.
        val state = repository.monitorState.first()
        assertEquals(null, state.lastLevel)
        assertEquals(null, state.lastPlugged)
    }

    @Test
    fun `a crossing fires and persists the new reading`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)
        repository.setMonitoringEnabled(true)
        repository.evaluateAndCommit(BatteryState(79, plugged = true))

        val fired = repository.evaluateAndCommit(BatteryState(80, plugged = true))

        assertEquals(listOf("charge80"), fired.map { it.id })
        val state = repository.monitorState.first()
        assertEquals(80, state.lastLevel)
        assertFalse("charge80" in state.armedIds)
    }

    @Test
    fun `an unchanged reading fires nothing and leaves the state untouched`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)
        repository.setMonitoringEnabled(true)
        repository.evaluateAndCommit(BatteryState(60, plugged = true))
        val before = repository.monitorState.first()

        val fired = repository.evaluateAndCommit(BatteryState(60, plugged = true))

        assertEquals(emptyList<Threshold>(), fired)
        assertEquals(before, repository.monitorState.first())
    }

    @Test
    fun `evaluateAndCommit preserves monitoringEnabled`() = runTest {
        repository.upsert(chargeAt80, currentLevel = 50)
        repository.setMonitoringEnabled(true)

        repository.evaluateAndCommit(BatteryState(70, plugged = true))

        assertTrue(repository.monitorState.first().monitoringEnabled)
    }

    @Test
    fun `switching monitoring off clears the reading but keeps arm state`() = runTest {
        repository.upsert(drainAt20, currentLevel = 50)
        repository.setMonitoringEnabled(true)
        repository.evaluateAndCommit(BatteryState(50, plugged = false))

        repository.setMonitoringEnabled(false)

        val state = repository.monitorState.first()
        assertEquals(null, state.lastLevel)
        assertEquals(null, state.lastPlugged)
        assertTrue("drain20" in state.armedIds)
    }

    @Test
    fun `an empty store reads as defaults`() = runTest {
        assertEquals(emptyList<Threshold>(), repository.thresholds.first())
        assertEquals(MonitorState(), repository.monitorState.first())
    }
}
