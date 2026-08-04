package com.yigu.xiangqi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yigu.xiangqi.data.local.dao.*
import com.yigu.xiangqi.data.local.entity.*

@Database(
    entities = [
        ManualEntity::class,
        GameEntity::class,
        UserProgressEntity::class,
        FavoriteEntity::class,
        UserNoteEntity::class,
        StudySessionEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun manualDao(): ManualDao
    abstract fun gameDao(): GameDao
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun noteDao(): NoteDao
    abstract fun studySessionDao(): StudySessionDao
}
