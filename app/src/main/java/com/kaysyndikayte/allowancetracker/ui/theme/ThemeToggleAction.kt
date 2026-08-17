package com.kaysyndikayte.allowancetracker.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Drop this inside any TopAppBar's `actions = { ... }` block. It reads LocalThemeController,
 * so it works on every screen without changing that screen's function signature — just add
 * `ThemeToggleAction()` alongside the existing action icons.
 */
@Composable
fun ThemeToggleAction() {
    val controller = LocalThemeController.current
    IconButton(onClick = { controller.toggle() }) {
        Icon(
            imageVector = if (controller.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (controller.isDark) "Switch to light mode" else "Switch to dark mode"
        )
    }
}
