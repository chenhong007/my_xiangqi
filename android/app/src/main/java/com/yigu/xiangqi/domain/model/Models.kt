package com.yigu.xiangqi.domain.model

import com.google.gson.annotations.SerializedName

/** 棋盘上一枚棋子的位置 */
data class PiecePosition(
    val piece: String,
    val col: Int,
    val row: Int,
)

/** 一步着法 */
data class Move(
    val step: Int,
    val notation: String,
    val from: List<Int>,
    val to: List<Int>,
)

/** 变着分支 */
data class Variation(
    @SerializedName("branch_after") val branchAfter: Int,
    @SerializedName("parent_branch") val parentBranch: Int,
    val moves: List<Move>,
)

/** 一局完整棋谱 */
data class Game(
    val id: String,
    val manualId: String,
    val title: String,
    val round: String,
    val result: String,
    val redPlayer: String,
    val blackPlayer: String,
    val initBoard: List<PiecePosition>,
    val moves: List<Move>,
    val variations: List<Variation>,
    val comments: Map<String, String>,
    val sortOrder: Int = 0,
)

/** 一部古谱 */
data class Manual(
    val id: String,
    val name: String,
    val type: String,
    val totalGames: Int,
    val addTime: Long = 0L,
    val lastAccessTime: Long = 0L,
    val viewCount: Int = 0,
    val isPinned: Boolean = false,
)

/** 学习状态 */
enum class StudyStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED
}

/** 学习进度 */
data class UserProgress(
    val gameId: String,
    val status: StudyStatus,
    val currentStep: Int,
    val currentBranch: String?,
    val guessCorrect: Int,
    val guessTotal: Int,
    val lastStudiedAt: Long,
    val completedAt: Long?,
)
