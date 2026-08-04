package com.yigu.xiangqi.domain.engine

import com.yigu.xiangqi.domain.model.Game
import com.yigu.xiangqi.domain.model.Move
import com.yigu.xiangqi.domain.model.PiecePosition
import com.yigu.xiangqi.domain.model.Variation

/**
 * 棋局引擎：管理棋盘状态、着法导航和变着分支。
 *
 * 核心状态是一个「面包屑」栈，记录从主线到当前变着的路径。
 * 每层包含着法列表和当前步数索引。
 */
class GameEngine(private val game: Game) {

    /** 分支路径中的一层 */
    data class BranchFrame(
        val moves: List<Move>,
        val branchLabel: String,
        val variationIndex: Int = -1,
    )

    // ── 棋盘状态 ──

    private val _initPieces = game.initBoard.toMutableList()

    /** 分支栈：index 0 = 主线 */
    private val _branchStack = mutableListOf(
        BranchFrame(game.moves, "主线")
    )

    /** 当前分支内的步数索引（0 = 初始局面，1 = 第 1 步后，...） */
    private var _stepIndex = 0

    // ── 公开状态 ──

    val branchStack: List<BranchFrame> get() = _branchStack.toList()
    val currentMoves: List<Move> get() = _branchStack.last().moves
    val stepIndex: Int get() = _stepIndex
    val totalSteps: Int get() = currentMoves.size
    val isAtStart: Boolean get() = _stepIndex == 0
    val isAtEnd: Boolean get() = _stepIndex >= totalSteps
    val isOnMainLine: Boolean get() = _branchStack.size == 1

    /** 当前步的着法（如果已经走了至少一步） */
    val currentMove: Move? get() = if (_stepIndex > 0) currentMoves.getOrNull(_stepIndex - 1) else null

    /** 当前步数处可选的变着分支 */
    val availableVariations: List<Variation>
        get() {
            if (!isOnMainLine) {
                // 变着内的嵌套变着暂不处理（数据中较少）
                return emptyList()
            }
            return game.variations.filter { it.parentBranch == 0 && it.branchAfter == _stepIndex }
        }

    /** 当前步的注释 */
    val currentComment: String?
        get() = game.comments[_stepIndex.toString()]

    // ── 计算当前棋盘 ──

    /** 从初始局面逐步重放到当前步数，返回棋盘棋子列表 */
    fun computeBoard(): List<PiecePosition> {
        val board = Array(10) { arrayOfNulls<String>(9) }

        // 摆初始棋子
        for (p in _initPieces) {
            board[p.row][p.col] = p.piece
        }

        // 重放所有分支帧的着法
        // 主线走到分叉点，然后切入变着
        val allMoves = buildReplaySequence()
        for (move in allMoves) {
            val fc = move.from[0]; val fr = move.from[1]
            val tc = move.to[0]; val tr = move.to[1]
            val piece = board[fr][fc]
            board[fr][fc] = null
            board[tr][tc] = piece
        }

        val result = mutableListOf<PiecePosition>()
        for (r in 0 until 10) {
            for (c in 0 until 9) {
                board[r][c]?.let { result.add(PiecePosition(it, c, r)) }
            }
        }
        return result
    }

    /**
     * 构建从初始局面到当前状态需要重放的着法序列。
     * 考虑分支栈：主线走到分叉点 + 各层变着走到各自位置。
     */
    private fun buildReplaySequence(): List<Move> {
        val result = mutableListOf<Move>()

        for (i in _branchStack.indices) {
            val frame = _branchStack[i]
            val stepsInThisFrame = if (i < _branchStack.size - 1) {
                // 非最后一层：走到下一层的分叉点
                val nextFrame = _branchStack[i + 1]
                val variation = if (i == 0 && nextFrame.variationIndex >= 0) {
                    game.variations.getOrNull(nextFrame.variationIndex)
                } else null
                variation?.branchAfter ?: 0
            } else {
                _stepIndex
            }
            result.addAll(frame.moves.take(stepsInThisFrame))
        }

        return result
    }

    // ── 导航操作 ──

    fun goToStart() { _stepIndex = 0 }

    fun goToEnd() { _stepIndex = totalSteps }

    fun goForward(): Boolean {
        if (_stepIndex < totalSteps) { _stepIndex++; return true }
        return false
    }

    fun goBackward(): Boolean {
        if (_stepIndex > 0) { _stepIndex--; return true }
        return false
    }

    fun goToStep(step: Int) {
        _stepIndex = step.coerceIn(0, totalSteps)
    }

    // ── 分支操作 ──

    /** 进入一个变着分支 */
    fun enterVariation(variationIndex: Int): Boolean {
        val variation = game.variations.getOrNull(variationIndex) ?: return false
        _branchStack.add(
            BranchFrame(
                moves = variation.moves,
                branchLabel = "第${variation.branchAfter}步变着${variationIndex + 1}",
                variationIndex = variationIndex,
            )
        )
        _stepIndex = 0
        return true
    }

    /** 返回上一级分支 */
    fun exitVariation(): Boolean {
        if (_branchStack.size <= 1) return false
        val removed = _branchStack.removeAt(_branchStack.size - 1)
        // 恢复到分叉点
        val variation = if (removed.variationIndex >= 0) {
            game.variations.getOrNull(removed.variationIndex)
        } else null
        _stepIndex = variation?.branchAfter ?: 0
        return true
    }

    /** 返回主线 */
    fun returnToMainLine() {
        while (_branchStack.size > 1) {
            _branchStack.removeAt(_branchStack.size - 1)
        }
        _stepIndex = 0
    }

    // ── 猜招模式 ──

    /** 检查用户走子是否匹配下一步 */
    fun checkGuess(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int): Boolean {
        val nextMove = currentMoves.getOrNull(_stepIndex) ?: return false
        return nextMove.from[0] == fromCol && nextMove.from[1] == fromRow
                && nextMove.to[0] == toCol && nextMove.to[1] == toRow
    }

    /** 获取下一步的正确着法（猜招提示用） */
    fun peekNextMove(): Move? = currentMoves.getOrNull(_stepIndex)
}
