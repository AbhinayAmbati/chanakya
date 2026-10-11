package com.chanakya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.navigation.ChanakyaApp
import com.chanakya.core.data.model.ThemeMode
import com.chanakya.core.designsystem.theme.ChanakyaTheme

/**
 * The single activity. It sets up edge-to-edge drawing, resolves the user's chosen
 * colour scheme, provides the dependency container to the composition, and hands off to
 * the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ChanakyaApplication).container

        setContent {
            val themeMode by container.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ChanakyaTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    ChanakyaApp()
                }
            }
        }
    }
}
