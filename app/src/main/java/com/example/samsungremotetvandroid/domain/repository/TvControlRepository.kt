package com.example.samsungremotetvandroid.domain.repository

import com.example.samsungremotetvandroid.domain.model.ConnectionState
import com.example.samsungremotetvandroid.domain.model.QuickLaunchShortcut
import com.example.samsungremotetvandroid.domain.model.RemoteKey
import com.example.samsungremotetvandroid.domain.model.SamsungTv
import kotlinx.coroutines.flow.StateFlow

interface TvControlRepository {
    val savedTvs: StateFlow<List<SamsungTv>>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect(tvId: String)
    suspend fun disconnect()
    suspend fun sendRemoteKey(key: RemoteKey)
    suspend fun launchQuickLaunchApp(tvId: String, shortcut: QuickLaunchShortcut)
    suspend fun forgetPairing(tvId: String)
    suspend fun removeDevice(tvId: String)
    suspend fun renameTv(tvId: String, newName: String)
}
