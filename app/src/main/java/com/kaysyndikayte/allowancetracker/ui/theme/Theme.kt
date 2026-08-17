package com.kaysyndikayte.allowancetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * SYSTEM = follow the phone's setting (the default, until the user overrides it once).
 * LIGHT / DARK = explicit user choice, persisted permanently via ThemePreferences/DataStore.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The thing every screen's top bar reads to show the toggle icon and flip the mode. Exposed
 * through a CompositionLocal (see LocalThemeController below) so individual screens don't
 * need a new parameter threaded through their function signatures — they just read
 * LocalThemeController.current from inside the composable body.
 */
interface ThemeController {
    val mode: ThemeMode
    val isDark: Boolean
    fun toggle()
}

val LocalThemeController = compositionLocalOf<ThemeController> {
    error("No ThemeController provided — AllowanceTrackerTheme must wrap the app content")
}

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer
)

/**
 * Wrap the whole app in this once, in MainActivity. It:
 *  1. Resolves ThemeMode -> an actual light/dark boolean (SYSTEM defers to isSystemInDarkTheme()).
 *  2. Applies the matching MaterialTheme color scheme.
 *  3. Provides ThemeController down through LocalThemeController so every screen's top bar
 *     can show a toggle button without new parameters.
 *  4. Keeps the status bar / nav bar icon color in sync (dark icons on light bg, vice versa) —
 *     this replaces any separate status-bar-icon fix from before; that logic now lives here
 *     and reacts automatically whenever the theme changes.
 */
@Composable
fun AllowanceTrackerTheme(
    themeMode: ThemeMode,
    onToggle: (currentlyDark: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val controller = remember(themeMode, darkTheme, onToggle) {
        object : ThemeController {
            override val mode = themeMode
            override val isDark = darkTheme
            override fun toggle() = onToggle(darkTheme)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalThemeController provides controller) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}