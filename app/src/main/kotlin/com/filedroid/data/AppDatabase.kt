package com.filedroid.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ProtocolConverter {
    @TypeConverter fun fromProtocol(p: Protocol): String = p.name
    @TypeConverter fun toProtocol(s: String): Protocol = Protocol.valueOf(s)
}

@Database(
    entities = [ConnectionProfile::class, SshProfile::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(ProtocolConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao
    abstract fun sshProfileDao(): SshProfileDao
}
