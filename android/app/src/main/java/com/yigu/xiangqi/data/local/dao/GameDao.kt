package com.yigu.xiangqi.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import com.yigu.xiangqi.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games WHERE manualId = :manualId ORDER BY sortOrder ASC, id ASC")
    fun getByManual(manualId: String): Flow<List<GameEntity>>

    @Query("""
        SELECT g.id, g.manualId, g.title, g.round, g.result,
               g.hasVariations, g.hasComments, g.moveCount,
               COALESCE(p.viewCount, 0)       AS viewCount,
               COALESCE(p.completionCount, 0) AS completionCount,
               p.status                        AS studyStatus,
               p.lastStudiedAt                 AS lastStudiedAt
        FROM games g
        LEFT JOIN user_progress p ON g.id = p.gameId
        WHERE g.manualId = :manualId
        ORDER BY g.sortOrder ASC, g.id ASC
    """)
    fun getByManualWithProgress(manualId: String): Flow<List<GameWithProgressTuple>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<GameEntity>>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Query("""
        SELECT * FROM games 
        WHERE id NOT IN (SELECT gameId FROM user_progress WHERE completionCount > 0)
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getRandomUnstudied(): GameEntity?

    @Query("""
        SELECT * FROM games 
        WHERE manualId = :manualId AND (sortOrder > :sortOrder OR (sortOrder = :sortOrder AND id > :id))
        ORDER BY sortOrder ASC, id ASC LIMIT 1
    """)
    suspend fun getNextGame(manualId: String, sortOrder: Int, id: String): GameEntity?

    @Query("SELECT id, manualId, initBoardJson FROM games")
    suspend fun getAllGameBoards(): List<GameBoardTuple>

    @androidx.room.Upsert
    suspend fun insertAll(games: List<GameEntity>)
}

data class GameBoardTuple(
    val id: String,
    val manualId: String,
    val initBoardJson: String
)

data class GameWithProgressTuple(
    val id: String,
    val manualId: String,
    val title: String,
    val round: String,
    val result: String,
    val hasVariations: Boolean,
    val hasComments: Boolean,
    val moveCount: Int,
    val viewCount: Int,
    val completionCount: Int,
    val studyStatus: String?,
    val lastStudiedAt: Long?,
)
