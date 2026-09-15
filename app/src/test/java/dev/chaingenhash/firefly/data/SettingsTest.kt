package dev.chaingenhash.firefly.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.chaingenhash.firefly.domain.AppSettings
import dev.chaingenhash.firefly.domain.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("settings.preferences_pb")
        }
        repository = SettingsRepository(store)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `settings survive a round trip`() {
        val settings = AppSettings(theme = ThemeChoice.DARK, resumeOnBoot = false)

        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test
    fun `an empty store decodes to defaults`() {
        assertEquals(AppSettings(), SettingsCodec.decode(null))
    }

    @Test
    fun `corrupt stored settings decode to defaults instead of throwing`() {
        assertEquals(AppSettings(), SettingsCodec.decode("{{{"))
    }

    @Test
    fun `defaults follow the system theme and resume on boot`() = runTest {
        val settings = repository.settings.first()

        assertEquals(ThemeChoice.SYSTEM, settings.theme)
        assertTrue(settings.resumeOnBoot)
    }

    @Test
    fun `setting the theme leaves the boot preference alone`() = runTest {
        repository.setResumeOnBoot(false)

        repository.setTheme(ThemeChoice.LIGHT)

        val settings = repository.settings.first()
        assertEquals(ThemeChoice.LIGHT, settings.theme)
        assertEquals(false, settings.resumeOnBoot)
    }

    @Test
    fun `setting the boot preference leaves the theme alone`() = runTest {
        repository.setTheme(ThemeChoice.DARK)

        repository.setResumeOnBoot(false)

        val settings = repository.settings.first()
        assertEquals(ThemeChoice.DARK, settings.theme)
        assertEquals(false, settings.resumeOnBoot)
    }
}
