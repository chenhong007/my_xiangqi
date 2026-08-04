package com.yigu.xiangqi.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.yigu.xiangqi.data.local.AppDatabase
import com.yigu.xiangqi.data.local.dao.*
import com.yigu.xiangqi.domain.preferences.UIPreferencesManager
import com.yigu.xiangqi.domain.sound.SoundManager
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
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "yigu.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
            
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE manuals ADD COLUMN addTime INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE manuals ADD COLUMN lastAccessTime INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE manuals ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE manuals ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        }
    }
    
    private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE games ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        }
    }
    
    @Provides
    @Singleton
    fun provideUIPreferencesManager(@ApplicationContext context: Context): UIPreferencesManager =
        UIPreferencesManager(context)

    @Provides fun provideManualDao(db: AppDatabase): ManualDao = db.manualDao()
    @Provides fun provideGameDao(db: AppDatabase): GameDao = db.gameDao()
    @Provides fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideStudySessionDao(db: AppDatabase): StudySessionDao = db.studySessionDao()

    @Provides
    @Singleton
    fun provideBoardRecognitionService(
        gameDao: GameDao,
        gson: Gson
    ): com.yigu.xiangqi.domain.recognition.BoardRecognitionService =
        com.yigu.xiangqi.data.recognition.MockBoardRecognitionService(gameDao, gson)
}
