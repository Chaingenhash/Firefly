package dev.chaingenhash.firefly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.chaingenhash.firefly.domain.ThemeChoice

/**
 * Firefly's palette: deep night surfaces with a warm lime-amber accent — the colour a
 * firefly actually glows, and a natural fit for a charge indicator.
 *
 * The accent differs between themes on purpose. [GlowDark] is bright enough to carry a
 * glow against a night background, but only reaches 3.4:1 on white; [GlowLight] is the
 * darkened version that clears 4.5:1 for text and for white-on-accent buttons.
 */
private val Night = Color(0xFF0F172A)
private val NightSurface = Color(0xFF1E293B)
private val NightSurfaceHigh = Color(0xFF272F42)
private val NightOutline = Color(0xFF475569)
private val GlowDark = Color(0xFFC6F24E)
private val GlowLight = Color(0xFF5C7A12)
private val Mist = Color(0xFFF8FAFC)
private val MistMuted = Color(0xFFCBD5E1)
private val Slate = Color(0xFF64748B)
private val DangerDark = Color(0xFFEF4444)
private val DangerLight = Color(0xFFDC2626)

private val FireflyDark = darkColorScheme(
    primary = GlowDark,
    onPrimary = Night,
    primaryContainer = NightSurfaceHigh,
    onPrimaryContainer = GlowDark,
    secondary = MistMuted,
    onSecondary = Night,
    background = Night,
    onBackground = Mist,
    surface = Night,
    onSurface = Mist,
    surfaceVariant = NightSurface,
    onSurfaceVariant = MistMuted,
    surfaceContainer = NightSurface,
    surfaceContainerLow = Color(0xFF172032),
    surfaceContainerLowest = Color(0xFF0B1120),
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF313A4F),
    secondaryContainer = Color(0xFF34421C),
    onSecondaryContainer = GlowDark,
    tertiary = GlowDark,
    onTertiary = Night,
    tertiaryContainer = NightSurfaceHigh,
    onTertiaryContainer = GlowDark,
    outline = NightOutline,
    outlineVariant = NightSurfaceHigh,
    error = DangerDark,
    onError = Night,
    errorContainer = Color(0xFF5B1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = GlowDark,
    inverseSurface = Mist,
    inverseOnSurface = Night,
    scrim = Color(0xFF000000),
)

private val FireflyLight = lightColorScheme(
    primary = GlowLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF5C8),
    onPrimaryContainer = Color(0xFF2C3A08),
    secondary = Slate,
    onSecondary = Color.White,
    background = Mist,
    onBackground = Night,
    surface = Color.White,
    onSurface = Night,
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerLow = Color(0xFFF6F9FC),
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color(0xFFE7EDF3),
    surfaceContainerHighest = Color(0xFFDDE4EC),
    secondaryContainer = Color(0xFFE4EFC8),
    onSecondaryContainer = Color(0xFF2C3A08),
    tertiary = GlowLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF5C8),
    onTertiaryContainer = Color(0xFF2C3A08),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF7A1414),
    surfaceTint = GlowLight,
    inverseSurface = Night,
    inverseOnSurface = Mist,
    scrim = Color(0xFF000000),
)

/**
 * The accent used for the glow itself rather than for text. Kept out of the colour
 * scheme because it is a graphic colour, not a semantic role.
 */
val glowAccent: Color
    @Composable get() = if (LocalDarkTheme.current) GlowDark else Color(0xFF7A9A1F)

/** Whether the Firefly palette is currently rendering dark, honouring an explicit choice. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun FireflyTheme(
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) FireflyDark else FireflyLight,
            content = content,
        )
    }
}
