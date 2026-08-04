package com.yigu.xiangqi.data.repository

import com.yigu.xiangqi.data.local.dao.FavoriteDao
import com.yigu.xiangqi.data.local.dao.ManualCompletionCount
import com.yigu.xiangqi.data.local.dao.NoteDao
import com.yigu.xiangqi.data.local.dao.ProgressDao
import com.yigu.xiangqi.data.local.dao.StudySessionDao
import com.yigu.xiangqi.data.local.entity.FavoriteEntity
import com.yigu.xiangqi.data.local.entity.StudySessionEntity
import com.yigu.xiangqi.data.local.entity.UserNoteEntity
import com.yigu.xiangqi.data.local.entity.UserProgressEntity
import com.yigu.xiangqi.domain.model.StudyStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val sessionDao: StudySessionDao,
    private val favoriteDao: FavoriteDao,
    private val noteDao: NoteDao,
) {
    // ── 学习进度 ──

    suspend fun getProgress(gameId: String): UserProgressEntity? =
        progressDao.getByGameId(gameId)

    fun observeProgress(gameId: String): Flow<UserProgressEntity?> =
        progressDao.observeByGameId(gameId)

    suspend fun getLastInProgress(): UserProgressEntity? =
        progressDao.getLastInProgress()

    fun getRecentProgress(): Flow<List<UserProgressEntity>> =
        progressDao.getRecent()

    fun countCompleted(): Flow<Int> = progressDao.countCompleted()

    fun countCompletedByManual(manualId: String): Flow<Int> =
        progressDao.countCompletedByManual(manualId)

    fun countCompletedGroupByManual(): Flow<List<ManualCompletionCount>> =
        progressDao.countCompletedGroupByManual()

    fun countStudiedGroupByManual(): Flow<List<ManualCompletionCount>> =
        progressDao.countStudiedGroupByManual()

    fun countStudied(): Flow<Int> = progressDao.countStudied()

    fun countStudyDays(): Flow<Int> = progressDao.countStudyDays()

    fun totalGuessCorrect(): Flow<Int> = progressDao.totalGuessCorrect().map { it ?: 0 }

    fun totalGuessTotal(): Flow<Int> = progressDao.totalGuessTotal().map { it ?: 0 }

    /**
     * 保存当前进度快照，不影响 viewCount / completionCount / firstStudiedAt。
     */
    suspend fun saveProgress(
        gameId: String,
        status: StudyStatus,
        currentStep: Int = 0,
        currentBranch: String? = null,
        guessCorrect: Int = 0,
        guessTotal: Int = 0,
    ) {
        progressDao.insertIfAbsent(UserProgressEntity(gameId = gameId))
        val now = System.currentTimeMillis()
        progressDao.updateProgress(
            gameId = gameId,
            status = status.name,
            currentStep = currentStep,
            currentBranch = currentBranch,
            guessCorrect = guessCorrect,
            guessTotal = guessTotal,
            lastStudiedAt = now,
            completedAt = if (status == StudyStatus.COMPLETED) now else null,
        )
    }

    // ── 学习计数 ──

    /** 确保进度记录存在（不增加任何计数） */
    suspend fun ensureProgressExists(gameId: String) {
        progressDao.insertIfAbsent(UserProgressEntity(gameId = gameId))
    }

    /** 进入棋谱页时调用：viewCount +1，记录首次学习时间 */
    suspend fun recordGameOpened(gameId: String) {
        progressDao.insertIfAbsent(UserProgressEntity(gameId = gameId))
        progressDao.incrementViewCount(gameId)
    }

    /** 完成全谱时调用：completionCount +1 */
    suspend fun recordGameCompleted(gameId: String) {
        progressDao.incrementCompletionCount(gameId)
    }

    // ── 学习会话 ──

    suspend fun startSession(gameId: String, mode: String = "REVIEW"): Long =
        sessionDao.insert(StudySessionEntity(gameId = gameId, mode = mode))

    suspend fun endSession(
        sessionId: Long,
        reachedStep: Int,
        totalSteps: Int,
        completed: Boolean,
        guessCorrect: Int = 0,
        guessTotal: Int = 0,
    ) {
        sessionDao.endSession(
            sessionId = sessionId,
            endedAt = System.currentTimeMillis(),
            reachedStep = reachedStep,
            totalSteps = totalSteps,
            completed = completed,
            guessCorrect = guessCorrect,
            guessTotal = guessTotal,
        )
    }

    // ── 收藏 ──

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    fun isFavorite(gameId: String): Flow<Boolean> = favoriteDao.isFavorite(gameId)

    suspend fun toggleFavorite(gameId: String) {
        val existing = favoriteDao.getByGameId(gameId)
        if (existing != null) {
            favoriteDao.deleteByGameId(gameId)
        } else {
            favoriteDao.insert(FavoriteEntity(gameId = gameId))
        }
    }

    // ── 笔记 ──

    fun getNotesByGame(gameId: String): Flow<List<UserNoteEntity>> = noteDao.getByGame(gameId)

    fun getAllNotes(): Flow<List<UserNoteEntity>> = noteDao.getAll()

    suspend fun addNote(gameId: String, stepIndex: Int, branchPath: String?, content: String) {
        noteDao.insert(
            UserNoteEntity(
                gameId = gameId,
                stepIndex = stepIndex,
                branchPath = branchPath,
                content = content,
            )
        )
    }

    suspend fun deleteNote(note: UserNoteEntity) = noteDao.delete(note)
}
