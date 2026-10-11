package com.chanakya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.navigation.ChanakyaApp
import com.chanakya.core.designsystem.theme.ChanakyaTheme

/**
 * The single activity. It sets up edge-to-edge drawing, provides the dependency
 * container to the composition, and hands off to the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ChanakyaApplication).container

        setContent {
            ChanakyaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    ChanakyaApp()
                }
            }
        }
    }
}
