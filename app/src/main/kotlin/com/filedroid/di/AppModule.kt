package com.filedroid.di

import android.content.Context
import androidx.room.Room
import com.filedroid.data.AppDatabase
import com.filedroid.data.ConnectionProfileDao
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

    @Provides @Singleton
    fun provideCredentialStore(@ApplicationContext ctx: Context): CredentialStore =
        CredentialStoreImpl(ctx)

    @Provides @Singleton
    fun providePermissionManager(): PermissionManager = PermissionManagerImpl()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "filedroid.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideConnectionProfileDao(db: AppDatabase): ConnectionProfileDao =
        db.connectionProfileDao()

    @Provides @Singleton
    fun provideSshProfileDao(db: AppDatabase): SshProfileDao =
        db.sshProfileDao()
}
