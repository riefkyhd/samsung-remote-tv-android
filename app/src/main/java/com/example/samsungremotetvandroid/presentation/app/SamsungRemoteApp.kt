package com.example.samsungremotetvandroid.presentation.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.samsungremotetvandroid.presentation.discovery.DiscoveryScreen
import com.example.samsungremotetvandroid.presentation.navigation.AppDestination
import com.example.samsungremotetvandroid.presentation.remote.RemoteScreen
import com.example.samsungremotetvandroid.presentation.settings.SettingsScreen

@Composable
fun SamsungRemoteApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.topLevel.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(text = destination.iconLabel) },
                        label = { Text(text = stringResource(id = destination.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Discovery.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Discovery.route) {
                DiscoveryScreen(
                    onOpenRemote = {
                        navController.navigate(AppDestination.Remote.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(AppDestination.Remote.route) {
                RemoteScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
