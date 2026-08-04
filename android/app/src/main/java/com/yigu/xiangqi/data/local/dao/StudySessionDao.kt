package com.yigu.xiangqi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yigu.xiangqi.data.local.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {

    @Insert
    suspend fun insert(session: StudySessionEntity): Long

    @Query("""
        UPDATE study_sessions SET
            endedAt = :endedAt,
            reachedStep = :reachedStep,
            totalSteps = :totalSteps,
            completed = :completed,
            guessCorrect = :guessCorrect,
            guessTotal = :guessTotal
        WHERE id = :sessionId
    """)
    suspend fun endSession(
        sessionId: Long,
        endedAt: Long,
        reachedStep: Int,
        totalSteps: Int,
        completed: Boolean,
        guessCorrect: Int,
        guessTotal: Int,
    )

    @Query("SELECT * FROM study_sessions WHERE gameId = :gameId ORDER BY startedAt DESC")
    fun getByGame(gameId: String): Flow<List<StudySessionEntity>>

    @Query("SELECT COUNT(*) FROM study_sessions WHERE gameId = :gameId")
    suspend fun countByGame(gameId: String): Int
}
