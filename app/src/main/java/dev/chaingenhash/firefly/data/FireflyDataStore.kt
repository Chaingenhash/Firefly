package dev.chaingenhash.firefly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The app's single Preferences store. Declared here rather than inside a repository so
 * that several repositories can share one store — a property delegate may only be
 * declared once per file, and two delegates would mean two stores over the same name.
 */
internal val Context.fireflyDataStore: DataStore<Preferences> by preferencesDataStore(name = "firefly")
