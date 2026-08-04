package com.yigu.xiangqi.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yigu.xiangqi.data.local.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

data class ManualCompletionCount(
    @ColumnInfo(name = "manualId") val manualId: String,
    @ColumnInfo(name = "count") val count: Int,
)

@Dao
interface ProgressDao {

    @Query("SELECT * FROM user_progress WHERE gameId = :gameId")
    suspend fun getByGameId(gameId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE gameId = :gameId")
    fun observeByGameId(gameId: String): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress ORDER BY lastStudiedAt DESC")
    fun getRecent(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE status = 'IN_PROGRESS' ORDER BY lastStudiedAt DESC LIMIT 1")
    suspend fun getLastInProgress(): UserProgressEntity?

    @Query("SELECT COUNT(*) FROM user_progress WHERE completionCount > 0")
    fun countCompleted(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT DATE(lastStudiedAt / 1000, 'unixepoch', 'localtime')) FROM user_progress")
    fun countStudyDays(): Flow<Int>

    @Query("SELECT SUM(guessCorrect) FROM user_progress")
    fun totalGuessCorrect(): Flow<Int?>

    @Query("SELECT SUM(guessTotal) FROM user_progress")
    fun totalGuessTotal(): Flow<Int?>

    @Query("""
        SELECT COUNT(*) FROM user_progress p 
        INNER JOIN games g ON p.gameId = g.id 
        WHERE g.manualId = :manualId AND p.completionCount > 0
    """)
    fun countCompletedByManual(manualId: String): Flow<Int>

    @Query("""
        SELECT g.manualId, COUNT(*) as count 
        FROM user_progress p 
        INNER JOIN games g ON p.gameId = g.id 
        WHERE p.completionCount > 0 
        GROUP BY g.manualId
    """)
    fun countCompletedGroupByManual(): Flow<List<ManualCompletionCount>>

    /** 按古谱分组统计已学习（viewCount > 0）的局数 */
    @Query("""
        SELECT g.manualId, COUNT(*) as count 
        FROM user_progress p 
        INNER JOIN games g ON p.gameId = g.id 
        WHERE p.viewCount > 0 
        GROUP BY g.manualId
    """)
    fun countStudiedGroupByManual(): Flow<List<ManualCompletionCount>>

    /** 全局已学习局数 */
    @Query("SELECT COUNT(*) FROM user_progress WHERE viewCount > 0")
    fun countStudied(): Flow<Int>

    /** 确保记录存在，已存在则忽略 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: UserProgressEntity)

    /** 更新进度（不影响 viewCount / completionCount / firstStudiedAt） */
    @Query("""
        UPDATE user_progress SET 
            status = :status, 
            currentStep = :currentStep,
            currentBranch = :currentBranch,
            guessCorrect = :guessCorrect,
            guessTotal = :guessTotal,
            lastStudiedAt = :lastStudiedAt,
            completedAt = :completedAt
        WHERE gameId = :gameId
    """)
    suspend fun updateProgress(
        gameId: String,
        status: String,
        currentStep: Int,
        currentBranch: String?,
        guessCorrect: Int,
        guessTotal: Int,
        lastStudiedAt: Long,
        completedAt: Long?,
    )

    /** 进入棋谱页时调用：viewCount +1，首次学习时间仅在空时写入，并更新最后访问时间 */
    @Query("""
        UPDATE user_progress SET 
            viewCount = viewCount + 1, 
            firstStudiedAt = COALESCE(firstStudiedAt, :now),
            lastStudiedAt = :now
        WHERE gameId = :gameId
    """)
    suspend fun incrementViewCount(gameId: String, now: Long = System.currentTimeMillis())

    /** 完成全谱时调用：completionCount +1 */
    @Query("UPDATE user_progress SET completionCount = completionCount + 1 WHERE gameId = :gameId")
    suspend fun incrementCompletionCount(gameId: String)
}
