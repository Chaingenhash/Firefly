package dev.chaingenhash.firefly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chaingenhash.firefly.domain.AppSettings
import dev.chaingenhash.firefly.domain.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val KEY_SETTINGS = stringPreferencesKey("settings")

/** Reads and writes [AppSettings]. Shares the store with [ThresholdRepository]. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.fireflyDataStore)

    val settings: Flow<AppSettings> =
        store.data.map { SettingsCodec.decode(it[KEY_SETTINGS]) }

    suspend fun setTheme(theme: ThemeChoice) = update { it.copy(theme = theme) }

    suspend fun setResumeOnBoot(resume: Boolean) = update { it.copy(resumeOnBoot = resume) }

    private suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = SettingsCodec.decode(prefs[KEY_SETTINGS])
            prefs[KEY_SETTINGS] = SettingsCodec.encode(transform(current))
        }
    }
}
