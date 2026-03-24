package com.example.samsungremotetvandroid.presentation.navigation

import androidx.annotation.StringRes
import com.example.samsungremotetvandroid.R

sealed class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val iconLabel: String
) {
    data object Discovery : AppDestination("discovery", R.string.nav_discovery, "D")
    data object Remote : AppDestination("remote", R.string.nav_remote, "R")
    data object Settings : AppDestination("settings", R.string.nav_settings, "S")

    companion object {
        val topLevel = listOf(Discovery, Remote, Settings)
    }
}
