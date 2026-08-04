package com.yigu.xiangqi.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.repository.ProgressRepository
import com.yigu.xiangqi.domain.preferences.DisplayMode
import com.yigu.xiangqi.domain.preferences.UIPreferencesManager
import com.yigu.xiangqi.domain.sound.SoundManager
import com.yigu.xiangqi.domain.sound.SoundType
import com.yigu.xiangqi.ui.board.MoveHighlightStyle
import com.yigu.xiangqi.ui.board.PieceDropEffectType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val completedGames: Int = 0,
    val studyDays: Int = 0,
    val guessCorrect: Int = 0,
    val guessTotal: Int = 0,
    val soundType: SoundType = SoundType.WOOD,
    val autoPlaySpeedMs: Long = UIPreferencesManager.DEFAULT_AUTO_PLAY_SPEED_MS,
    val highlightStyle: MoveHighlightStyle = MoveHighlightStyle.TIANTIAN,
    val pieceDropEffect: PieceDropEffectType = PieceDropEffectType.TIANTIAN_XIANGQI,
    val guessMode: Boolean = false,
    val guessResponseDelayMs: Long = UIPreferencesManager.DEFAULT_GUESS_RESPONSE_DELAY_MS,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val soundManager: SoundManager,
    private val uiPreferencesManager: UIPreferencesManager,
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<ProfileUiState> = combine(
        progressRepository.countCompleted(),
        progressRepository.countStudyDays(),
        progressRepository.totalGuessCorrect(),
        progressRepository.totalGuessTotal(),
        soundManager.currentType,
        uiPreferencesManager.autoPlaySpeedMs,
        uiPreferencesManager.highlightStyle,
        uiPreferencesManager.pieceDropEffect,
        uiPreferencesManager.guessMode,
        uiPreferencesManager.guessResponseDelayMs,
        uiPreferencesManager.displayMode,
    ) { values ->
        ProfileUiState(
            completedGames = values[0] as Int,
            studyDays = values[1] as Int,
            guessCorrect = values[2] as Int,
            guessTotal = values[3] as Int,
            soundType = values[4] as SoundType,
            autoPlaySpeedMs = values[5] as Long,
            highlightStyle = values[6] as MoveHighlightStyle,
            pieceDropEffect = values[7] as PieceDropEffectType,
            guessMode = values[8] as Boolean,
            guessResponseDelayMs = values[9] as Long,
            displayMode = values[10] as DisplayMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch { uiPreferencesManager.setDisplayMode(mode) }
    }

    fun setSoundType(type: SoundType) {
        soundManager.setSoundType(type)
    }

    fun setAutoPlaySpeed(speedMs: Long) {
        viewModelScope.launch { uiPreferencesManager.setAutoPlaySpeed(speedMs) }
    }

    fun setHighlightStyle(style: MoveHighlightStyle) {
        viewModelScope.launch { uiPreferencesManager.setHighlightStyle(style) }
    }

    fun setPieceDropEffect(effect: PieceDropEffectType) {
        viewModelScope.launch { uiPreferencesManager.setPieceDropEffect(effect) }
    }

    fun setGuessMode(enabled: Boolean) {
        viewModelScope.launch { uiPreferencesManager.setGuessMode(enabled) }
    }

    fun setGuessResponseDelay(delayMs: Long) {
        viewModelScope.launch { uiPreferencesManager.setGuessResponseDelay(delayMs) }
    }
}
