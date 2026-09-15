package dev.chaingenhash.firefly.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * User preferences that are not part of monitoring itself.
 *
 * The hysteresis band is deliberately absent: it is an internal constant, and exposing
 * it would let a user set a value that makes alerts either repeat or never re-arm.
 */
@Serializable
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val resumeOnBoot: Boolean = true,
)
