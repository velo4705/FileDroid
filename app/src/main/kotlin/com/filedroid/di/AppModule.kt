package com.filedroid.di

import android.content.Context
import com.filedroid.permission.PermissionManager
import com.filedroid.permission.PermissionManagerImpl
import com.filedroid.security.CredentialStore
import com.filedroid.security.CredentialStoreImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext ctx: Context): CredentialStore =
        CredentialStoreImpl(ctx)

    @Provides
    @Singleton
    fun providePermissionManager(): PermissionManager =
        PermissionManagerImpl()
}
