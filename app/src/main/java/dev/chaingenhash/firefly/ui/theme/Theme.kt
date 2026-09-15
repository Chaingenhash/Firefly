package dev.chaingenhash.firefly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    surfaceContainerHigh = NightSurfaceHigh,
    outline = NightOutline,
    outlineVariant = NightSurfaceHigh,
    error = DangerDark,
    onError = Night,
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
    surfaceContainerHigh = Color(0xFFE7EDF3),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = DangerLight,
    onError = Color.White,
)

/**
 * The accent used for the glow itself rather than for text. Kept out of the colour
 * scheme because it is a graphic colour, not a semantic role.
 */
val glowAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) GlowDark else Color(0xFF7A9A1F)

@Composable
fun FireflyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FireflyDark else FireflyLight,
        content = content,
    )
}
