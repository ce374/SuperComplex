package com.supercomplex.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val blockId: Long,
    val sourceType: String,        // "Block" | "Channel" | "User"
    val sourceSlug: String? = null, // channel slug or user slug
    val blockType: String,         // Image, Text, Link, Embed, Attachment
    val title: String? = null,
    val originalUrl: String,
    val localPath: String,
    val fileName: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = "PENDING", // PENDING, RUNNING, PAUSED, DONE, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val kind: String,              // "channel" | "user" | "block"
    val refId: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val blockId: Long,
    val title: String? = null,
    val href: String? = null,
    val typename: String? = null,
    val previewUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter fun fromStringList(v: List<String>?): String = v?.joinToString("|") ?: ""
    @TypeConverter fun toStringList(v: String?): List<String> = if (v.isNullOrBlank()) emptyList() else v.split("|")
}

@Database(
    entities = [DownloadEntity::class, HistoryEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun init(context: Context) {
            if (INSTANCE == null) synchronized(this) {
                if (INSTANCE == null) INSTANCE = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supercomplex.db"
                ).fallbackToDestructiveMigration().build()
            }
        }
        fun get(): AppDatabase = INSTANCE ?: error("AppDatabase not initialised")
    }
}
