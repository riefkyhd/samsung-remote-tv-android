package com.example.samsungremotetvandroid.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.samsungremotetvandroid.presentation.discovery.DiscoveryScreen
import com.example.samsungremotetvandroid.presentation.remote.RemoteScreen
import com.example.samsungremotetvandroid.presentation.settings.SettingsScreen

@Composable
fun SamsungRemoteApp() {
    var activeScreen: AppScreen by rememberSaveable {
        mutableStateOf(AppScreen.Discovery)
    }

    when (activeScreen) {
        AppScreen.Discovery -> {
            DiscoveryScreen(
                onOpenRemote = {
                    activeScreen = AppScreen.Remote
                },
                onOpenSettings = {
                    activeScreen = AppScreen.Settings
                }
            )
        }

        AppScreen.Remote -> {
            RemoteScreen(
                onOpenDiscovery = {
                    activeScreen = AppScreen.Discovery
                },
                onOpenSettings = {
                    activeScreen = AppScreen.Settings
                }
            )
        }

        AppScreen.Settings -> {
            SettingsScreen(
                onBack = {
                    activeScreen = AppScreen.Discovery
                }
            )
        }
    }
}

private enum class AppScreen {
    Discovery,
    Remote,
    Settings
}
