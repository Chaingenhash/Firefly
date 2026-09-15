package dev.chaingenhash.firefly.data

import dev.chaingenhash.firefly.domain.AppSettings
import kotlinx.serialization.json.Json

/** Serializes [AppSettings]. Decoding never throws; a bad store falls back to defaults. */
object SettingsCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AppSettings): String = json.encodeToString(settings)

    fun decode(raw: String?): AppSettings =
        if (raw == null) AppSettings() else runCatching {
            json.decodeFromString<AppSettings>(raw)
        }.getOrDefault(AppSettings())
}
