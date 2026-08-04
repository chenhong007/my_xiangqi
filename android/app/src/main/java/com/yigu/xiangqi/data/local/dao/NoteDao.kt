package com.yigu.xiangqi.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yigu.xiangqi.data.local.entity.UserNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM user_notes WHERE gameId = :gameId ORDER BY stepIndex")
    fun getByGame(gameId: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<UserNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: UserNoteEntity)

    @Delete
    suspend fun delete(note: UserNoteEntity)
}
