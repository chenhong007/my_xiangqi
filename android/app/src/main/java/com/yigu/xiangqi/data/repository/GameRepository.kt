package com.yigu.xiangqi.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yigu.xiangqi.data.local.dao.GameDao
import com.yigu.xiangqi.data.local.dao.GameWithProgressTuple
import com.yigu.xiangqi.data.local.entity.GameEntity
import com.yigu.xiangqi.domain.model.Game
import com.yigu.xiangqi.domain.model.Move
import com.yigu.xiangqi.domain.model.PiecePosition
import com.yigu.xiangqi.domain.model.Variation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val gson: Gson,
) {
    fun getGamesByManual(manualId: String): Flow<List<GameSummary>> =
        gameDao.getByManualWithProgress(manualId).map { list ->
            list.map { it.toSummary() }
        }

    suspend fun getGame(id: String): Game? {
        val entity = gameDao.getById(id) ?: return null
        return entity.toDomain(gson)
    }

    fun search(query: String): Flow<List<GameSummary>> =
        gameDao.search(query).map { list -> list.map { it.toSummary() } }

    suspend fun getRandomUnstudied(): Game? =
        gameDao.getRandomUnstudied()?.toDomain(gson)

    suspend fun getNextGame(manualId: String, sortOrder: Int, id: String): Game? =
        gameDao.getNextGame(manualId, sortOrder, id)?.toDomain(gson)

    suspend fun getGameEntity(id: String): GameEntity? = gameDao.getById(id)

    suspend fun findSimilarGame(targetBoard: List<PiecePosition>): GameSummary? {
        val allBoards = gameDao.getAllGameBoards()
        val boardType = object : TypeToken<List<PiecePosition>>() {}.type
        
        var bestMatchId: String? = null
        var maxScore = -1

        for (board in allBoards) {
            val pieces: List<PiecePosition> = gson.fromJson(board.initBoardJson, boardType) ?: emptyList()
            val score = calculateSimilarity(targetBoard, pieces)
            if (score > maxScore) {
                maxScore = score
                bestMatchId = board.id
            }
        }

        if (bestMatchId != null && maxScore > 0) {
            val entity = gameDao.getById(bestMatchId)
            return entity?.toSummary()
        }
        return null
    }

    private fun calculateSimilarity(board1: List<PiecePosition>, board2: List<PiecePosition>): Int {
        var score = 0
        val map1 = board1.associateBy { "${it.col}_${it.row}" }
        for (p2 in board2) {
            val p1 = map1["${p2.col}_${p2.row}"]
            if (p1 != null && p1.piece == p2.piece) {
                score++
            }
        }
        return score
    }
}

/** 列表页用的精简数据，不解析大 JSON 字段 */
data class GameSummary(
    val id: String,
    val manualId: String,
    val title: String,
    val round: String,
    val result: String,
    val hasVariations: Boolean,
    val hasComments: Boolean,
    val moveCount: Int,
    val viewCount: Int = 0,
    val completionCount: Int = 0,
    val studyStatus: String? = null,
    val lastStudiedAt: Long? = null,
)

private fun GameWithProgressTuple.toSummary() = GameSummary(
    id = id,
    manualId = manualId,
    title = title,
    round = round,
    result = result,
    hasVariations = hasVariations,
    hasComments = hasComments,
    moveCount = moveCount,
    viewCount = viewCount,
    completionCount = completionCount,
    studyStatus = studyStatus,
    lastStudiedAt = lastStudiedAt,
)

private fun GameEntity.toSummary() = GameSummary(
    id = id,
    manualId = manualId,
    title = title,
    round = round,
    result = result,
    hasVariations = hasVariations,
    hasComments = hasComments,
    moveCount = moveCount,
)

private fun GameEntity.toDomain(gson: Gson): Game {
    val boardType = object : TypeToken<List<PiecePosition>>() {}.type
    val movesType = object : TypeToken<List<Move>>() {}.type
    val varsType = object : TypeToken<List<Variation>>() {}.type
    val commentsType = object : TypeToken<Map<String, String>>() {}.type

    return Game(
        id = id,
        manualId = manualId,
        title = title,
        round = round,
        result = result,
        redPlayer = redPlayer,
        blackPlayer = blackPlayer,
        initBoard = gson.fromJson(initBoardJson, boardType) ?: emptyList(),
        moves = gson.fromJson(movesJson, movesType) ?: emptyList(),
        variations = gson.fromJson(variationsJson, varsType) ?: emptyList(),
        comments = gson.fromJson(commentsJson, commentsType) ?: emptyMap(),
        sortOrder = sortOrder,
    )
}
