package com.example.samsungremotetvandroid.core.di

import com.example.samsungremotetvandroid.data.storage.EncryptedSensitivePairingStorage
import com.example.samsungremotetvandroid.data.storage.SensitivePairingStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensitivePairingStorageModule {
    @Binds
    @Singleton
    abstract fun bindSensitivePairingStorage(
        implementation: EncryptedSensitivePairingStorage
    ): SensitivePairingStorage
}
