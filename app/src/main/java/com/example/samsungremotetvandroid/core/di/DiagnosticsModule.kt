package com.example.samsungremotetvandroid.core.di

import com.example.samsungremotetvandroid.core.diagnostics.DiagnosticsTracker
import com.example.samsungremotetvandroid.core.diagnostics.InMemoryDiagnosticsTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticsTracker(
        implementation: InMemoryDiagnosticsTracker
    ): DiagnosticsTracker
}
