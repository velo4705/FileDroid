package com.filedroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY label ASC")
    fun observeAll(): Flow<List<ConnectionProfile>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: Long): ConnectionProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfile): Long

    @Delete
    suspend fun delete(profile: ConnectionProfile)
}
