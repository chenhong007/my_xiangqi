package com.yigu.xiangqi.ui.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.repository.GameRepository
import com.yigu.xiangqi.data.repository.ProgressRepository
import com.yigu.xiangqi.domain.engine.GameEngine
import com.yigu.xiangqi.domain.model.*
import com.yigu.xiangqi.domain.sound.SoundManager
import com.yigu.xiangqi.domain.sound.SoundType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoardUiState(
    val loading: Boolean = true,
    val game: Game? = null,
    val pieces: List<PiecePosition> = emptyList(),
    val currentMoves: List<Move> = emptyList(),
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentMove: Move? = null,
    val lastFrom: Pair<Int, Int>? = null,
    val lastTo: Pair<Int, Int>? = null,
    val comment: String? = null,
    val branchPath: List<String> = listOf("主线"),
    val availableVariations: List<Variation> = emptyList(),
    val isOnMainLine: Boolean = true,
    val flipped: Boolean = false,
    // 猜招模式
    val guessMode: Boolean = false,
    val showMovesInGuessMode: Boolean = false,
    val guessResponseDelayMs: Long = 500L,
    val isComputerThinking: Boolean = false,
    val isNextMoveRed: Boolean? = null,
    val selectedCell: Pair<Int, Int>? = null,
    val validTargets: Set<Pair<Int, Int>> = emptySet(),
    val guessCorrect: Int = 0,
    val guessTotal: Int = 0,
    val guessResult: GuessResult? = null,
    val hintMove: Move? = null,
    // 收藏
    val isFavorite: Boolean = false,
    // 自动播放
    val autoPlaying: Boolean = false,
    val autoPlaySpeedMs: Long = 2000L,
    // 落子效果
    val pieceDropEffectConfig: PieceDropEffectConfig = PieceDropEffectConfig(),
    // 走棋高亮样式
    val highlightStyle: MoveHighlightStyle = MoveHighlightStyle.TIANTIAN,
    // 音效
    val soundType: SoundType = SoundType.WOOD,
    // 学习统计
    val viewCount: Int = 0,
    val completionCount: Int = 0,
    val nextGameId: String? = null,
)

enum class GuessResult { CORRECT, WRONG }

@HiltViewModel
class BoardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val progressRepository: ProgressRepository,
    private val soundManager: SoundManager,
    private val uiPreferencesManager: com.yigu.xiangqi.domain.preferences.UIPreferencesManager,
) : ViewModel() {

    private var gameId: String = savedStateHandle["gameId"] ?: ""
    private var engine: GameEngine? = null
    private var sessionId: Long = 0L
    private var hasCountedCompletion = false

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    private var autoPlayJob: Job? = null

    init {
        loadGame()
        observeFavorite()
        observeGuessMode()
        observeGuessResponseDelay()
        observePieceDropEffect()
        observeHighlightStyle()
        observeAutoPlaySpeed()
        observeSoundType()
        recordOpen()
    }

    private fun observeGuessResponseDelay() {
        viewModelScope.launch {
            uiPreferencesManager.guessResponseDelayMs.collect { delayMs ->
                _state.value = _state.value.copy(guessResponseDelayMs = delayMs)
            }
        }
    }
    
    private fun observeGuessMode() {
        viewModelScope.launch {
            uiPreferencesManager.guessMode.collect { mode ->
                if (_state.value.guessMode != mode) {
                    _state.value = _state.value.copy(
                        guessMode = mode,
                        selectedCell = null,
                        validTargets = emptySet(),
                        guessCorrect = 0,
                        guessTotal = 0,
                        guessResult = null,
                        hintMove = null,
                        showMovesInGuessMode = false,
                    )
                    if (!mode) {
                        engine?.goToStart()
                    }
                    refreshUi()
                }
            }
        }
    }

    private fun observePieceDropEffect() {
        viewModelScope.launch {
            uiPreferencesManager.pieceDropEffect.collect { effect ->
                _state.value = _state.value.copy(
                    pieceDropEffectConfig = PieceDropEffectConfig(type = effect)
                )
            }
        }
    }
    
    fun setPieceDropEffect(effect: PieceDropEffectType) {
        viewModelScope.launch {
            uiPreferencesManager.setPieceDropEffect(effect)
        }
    }

    fun setGuessResponseDelay(delayMs: Long) {
        viewModelScope.launch {
            uiPreferencesManager.setGuessResponseDelay(delayMs)
        }
    }

    fun toggleShowMovesInGuessMode() {
        _state.value = _state.value.copy(showMovesInGuessMode = !_state.value.showMovesInGuessMode)
    }

    private fun observeHighlightStyle() {
        viewModelScope.launch {
            uiPreferencesManager.highlightStyle.collect { style ->
                _state.value = _state.value.copy(highlightStyle = style)
            }
        }
    }

    fun setHighlightStyle(style: MoveHighlightStyle) {
        viewModelScope.launch {
            uiPreferencesManager.setHighlightStyle(style)
        }
    }

    private fun observeAutoPlaySpeed() {
        viewModelScope.launch {
            uiPreferencesManager.autoPlaySpeedMs.collect { speedMs ->
                _state.value = _state.value.copy(autoPlaySpeedMs = speedMs)
            }
        }
    }

    fun setAutoPlaySpeed(speedMs: Long) {
        viewModelScope.launch {
            uiPreferencesManager.setAutoPlaySpeed(speedMs)
        }
    }

    private fun observeSoundType() {
        viewModelScope.launch {
            soundManager.currentType.collect { type ->
                _state.value = _state.value.copy(soundType = type)
            }
        }
    }

    fun setSoundType(type: SoundType) {
        soundManager.setSoundType(type)
    }

    private fun loadGame() {
        viewModelScope.launch {
            val game = gameRepository.getGame(gameId) ?: return@launch
            val eng = GameEngine(game)
            engine = eng

            // 获取下一局ID
            val nextGame = gameRepository.getNextGame(game.manualId, game.sortOrder, game.id)
            if (nextGame != null) {
                _state.value = _state.value.copy(nextGameId = nextGame.id)
            }

            // 恢复进度
            val progress = progressRepository.getProgress(gameId)
            if (progress != null && progress.status == StudyStatus.IN_PROGRESS.name) {
                eng.goToStep(progress.currentStep)
            }

            _state.value = _state.value.copy(showMovesInGuessMode = false)
            refreshUi(game = game)
        }
    }

    private fun recordOpen() {
        if (gameId.isBlank()) return
        viewModelScope.launch {
            progressRepository.recordGameOpened(gameId)
            sessionId = progressRepository.startSession(
                gameId = gameId,
                mode = if (_state.value.guessMode) "GUESS" else "REVIEW",
            )
            val progress = progressRepository.getProgress(gameId)
            if (progress != null) {
                _state.value = _state.value.copy(
                    viewCount = progress.viewCount,
                    completionCount = progress.completionCount,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val eng = engine ?: return
        val s = _state.value
        if (sessionId > 0) {
            kotlinx.coroutines.runBlocking {
                progressRepository.endSession(
                    sessionId = sessionId,
                    reachedStep = eng.stepIndex,
                    totalSteps = eng.totalSteps,
                    completed = hasCountedCompletion,
                    guessCorrect = s.guessCorrect,
                    guessTotal = s.guessTotal,
                )
            }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            progressRepository.isFavorite(gameId).collect { fav ->
                _state.value = _state.value.copy(isFavorite = fav)
            }
        }
    }

    private fun refreshUi(game: Game? = null, clearGuessResult: Boolean = true) {
        val eng = engine ?: return
        val g = game ?: _state.value.game ?: return
        val move = eng.currentMove
        
        val nextMove = eng.peekNextMove()
        val board = eng.computeBoard()
        val isNextRed = nextMove?.let { mv ->
            val p = board.find { it.col == mv.from[0] && it.row == mv.from[1] }
            val redPieces = setOf("帅", "仕", "相", "马", "车", "炮", "兵")
            p?.piece in redPieces
        }

        _state.value = _state.value.copy(
            loading = false,
            game = g,
            pieces = board,
            currentMoves = eng.currentMoves,
            stepIndex = eng.stepIndex,
            totalSteps = eng.totalSteps,
            currentMove = move,
            lastFrom = move?.from?.let { it[0] to it[1] },
            lastTo = move?.to?.let { it[0] to it[1] },
            comment = eng.currentComment,
            branchPath = eng.branchStack.map { it.branchLabel },
            availableVariations = eng.availableVariations,
            isOnMainLine = eng.isOnMainLine,
            isNextMoveRed = isNextRed,
            guessResult = if (clearGuessResult) null else _state.value.guessResult,
            hintMove = if (clearGuessResult) null else _state.value.hintMove,
        )
    }

    // ── 导航 ──

    fun goForward() { engine?.goForward(); refreshUi(); saveProgress(); soundManager.play() }
    fun goBackward() { engine?.goBackward(); refreshUi(); saveProgress(); soundManager.play() }
    fun goToStart() { engine?.goToStart(); refreshUi(); saveProgress() }
    fun goToEnd() { engine?.goToEnd(); refreshUi(); saveProgress() }
    fun goToStep(step: Int) { engine?.goToStep(step); refreshUi(); saveProgress(); soundManager.play() }

    fun toggleFlip() {
        _state.value = _state.value.copy(flipped = !_state.value.flipped)
    }

    fun toggleAutoPlay() {
        if (_state.value.autoPlaying) {
            stopAutoPlay()
        } else {
            startAutoPlay()
        }
    }

    private fun startAutoPlay() {
        _state.value = _state.value.copy(autoPlaying = true)
        autoPlayJob = viewModelScope.launch {
            val eng = engine ?: return@launch
            while (eng.goForward()) {
                refreshUi()
                saveProgress()
                soundManager.play()
                delay(_state.value.autoPlaySpeedMs)
            }
            _state.value = _state.value.copy(autoPlaying = false)
        }
    }

    private fun stopAutoPlay() {
        autoPlayJob?.cancel()
        _state.value = _state.value.copy(autoPlaying = false)
    }

    // ── 变着 ──

    fun enterVariation(index: Int) {
        engine?.enterVariation(index)
        refreshUi()
        soundManager.play()
    }

    fun exitVariation() {
        engine?.exitVariation()
        refreshUi()
    }

    fun returnToMainLine() {
        engine?.returnToMainLine()
        refreshUi()
    }

    // ── 猜招 ──

    fun showHint() {
        val eng = engine ?: return
        if (eng.isAtEnd) return
        val nextMove = eng.peekNextMove()
        _state.value = _state.value.copy(hintMove = nextMove)
    }

    fun setGuessMode(enabled: Boolean) {
        viewModelScope.launch {
            uiPreferencesManager.setGuessMode(enabled)
        }
    }

    fun onBoardTap(col: Int, row: Int) {
        if (!_state.value.guessMode) return
        if (_state.value.isComputerThinking) return
        
        // 任何点击操作都清除提示
        _state.value = _state.value.copy(hintMove = null)
        
        val eng = engine ?: return
        if (eng.isAtEnd) return

        val selected = _state.value.selectedCell
        if (selected != null) {
            // 第二次点击：尝试走子
            val (fc, fr) = selected
            
            // 点击同一个棋子，取消选中，不算猜错
            if (fc == col && fr == row) {
                _state.value = _state.value.copy(selectedCell = null)
                refreshUi(clearGuessResult = false)
                return
            }

            // 如果点击的是另一个棋子，允许重新选中而不是算作猜错目标
            val tappedPiece = _state.value.pieces.find { it.col == col && it.row == row }
            if (tappedPiece != null && !eng.checkGuess(fc, fr, col, row)) {
                // 如果猜错了但点的是另一个棋子，直接切换选中
                _state.value = _state.value.copy(
                    selectedCell = col to row,
                    guessResult = null,
                )
                refreshUi(clearGuessResult = false)
                return
            }

            if (eng.checkGuess(fc, fr, col, row)) {
                eng.goForward()
                _state.value = _state.value.copy(
                    guessCorrect = _state.value.guessCorrect + 1,
                    guessTotal = _state.value.guessTotal + 1,
                    guessResult = GuessResult.CORRECT,
                    selectedCell = null,
                    validTargets = emptySet(),
                    isComputerThinking = !eng.isAtEnd
                )
                soundManager.play()
                refreshUi(clearGuessResult = false)
                saveProgress()

                // 自动走对方应招，加入延迟
                if (!eng.isAtEnd) {
                    viewModelScope.launch {
                        delay(_state.value.guessResponseDelayMs)
                        eng.goForward()
                        _state.value = _state.value.copy(isComputerThinking = false)
                        soundManager.play()
                        refreshUi(clearGuessResult = false)
                        saveProgress()
                    }
                }
            } else {
                _state.value = _state.value.copy(
                    guessTotal = _state.value.guessTotal + 1,
                    guessResult = GuessResult.WRONG,
                    selectedCell = null,
                    validTargets = emptySet(),
                )
                refreshUi(clearGuessResult = false)
                saveProgress()
            }
        } else {
            // 第一次点击：选中棋子
            val pieces = _state.value.pieces
            val tapped = pieces.find { it.col == col && it.row == row }
            if (tapped != null) {
                _state.value = _state.value.copy(
                    selectedCell = col to row,
                    guessResult = null,
                )
            }
        }
    }

    // ── 收藏 ──

    fun toggleFavorite() {
        viewModelScope.launch {
            progressRepository.toggleFavorite(gameId)
        }
    }

    // ── 进度保存 ──

    private fun saveProgress() {
        val eng = engine ?: return
        viewModelScope.launch {
            val isAlreadyCompleted = _state.value.completionCount > 0

            val status = when {
                eng.isAtEnd -> StudyStatus.COMPLETED
                isAlreadyCompleted -> StudyStatus.COMPLETED
                hasCountedCompletion -> StudyStatus.COMPLETED
                eng.stepIndex > 0 -> StudyStatus.IN_PROGRESS
                else -> StudyStatus.NOT_STARTED
            }

            progressRepository.saveProgress(
                gameId = gameId,
                status = status,
                currentStep = eng.stepIndex,
                guessCorrect = _state.value.guessCorrect,
                guessTotal = _state.value.guessTotal,
            )

            if (eng.isAtEnd && !hasCountedCompletion) {
                hasCountedCompletion = true
                progressRepository.recordGameCompleted(gameId)
                val progress = progressRepository.getProgress(gameId)
                if (progress != null) {
                    _state.value = _state.value.copy(completionCount = progress.completionCount)
                }
            }
        }
    }
}
