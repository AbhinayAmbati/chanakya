package com.chanakya.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The light colour scheme. This is the primary scheme for Chanakya, built around a
 * white background with ice-blue surfaces and a bright-mint accent.
 */
private val LightColors = lightColorScheme(
    primary = BrightMint,
    onPrimary = White,
    primaryContainer = MintGlow,
    onPrimaryContainer = InkSlate,
    secondary = SkyBlue,
    onSecondary = White,
    secondaryContainer = Ice,
    onSecondaryContainer = InkSlate,
    tertiary = CategoryAnalytics,
    onTertiary = White,
    background = White,
    onBackground = InkSlate,
    surface = Ice,
    onSurface = InkSlate,
    surfaceVariant = Ice,
    onSurfaceVariant = SoftSlate,
    outline = StatusAllowed,
    error = StatusHighActivity,
    onError = White,
)

/**
 * The dark colour scheme. The palette is light-first, so this is a considered
 * inversion rather than a direct translation.
 */
private val DarkColors = darkColorScheme(
    primary = BrightMint,
    onPrimary = InkSlate,
    primaryContainer = Color(0xFF0F2A24),
    onPrimaryContainer = MintGlow,
    secondary = SkyBlue,
    onSecondary = InkSlate,
    secondaryContainer = Color(0xFF152635),
    onSecondaryContainer = Ice,
    tertiary = CategoryAnalytics,
    onTertiary = White,
    background = Color(0xFF0B1218),
    onBackground = Ice,
    surface = Color(0xFF121B24),
    onSurface = Ice,
    surfaceVariant = Color(0xFF121B24),
    onSurfaceVariant = SoftSlate,
    outline = SoftSlate,
    error = StatusHighActivity,
    onError = White,
)

/**
 * The root theme wrapper. Wrap the whole app (and every preview) in this so that
 * colours, typography and shapes are consistent everywhere.
 *
 * @param darkTheme whether to use the dark scheme; defaults to the system setting.
 */
@Composable
fun ChanakyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ChanakyaTypography,
        shapes = ChanakyaShapes,
        content = content,
    )
}
