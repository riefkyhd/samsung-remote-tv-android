package com.example.samsungremotetvandroid.core.di

import com.example.samsungremotetvandroid.data.repository.InMemoryTvControlRepository
import com.example.samsungremotetvandroid.domain.repository.TvControlRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTvControlRepository(
        implementation: InMemoryTvControlRepository
    ): TvControlRepository
}
