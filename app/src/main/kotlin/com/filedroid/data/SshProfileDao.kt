package com.filedroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SshProfileDao {
    @Query("SELECT * FROM ssh_profiles ORDER BY label ASC")
    fun observeAll(): Flow<List<SshProfile>>

    @Query("SELECT * FROM ssh_profiles WHERE id = :id")
    suspend fun getById(id: Long): SshProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SshProfile): Long

    @Delete
    suspend fun delete(profile: SshProfile)
}
