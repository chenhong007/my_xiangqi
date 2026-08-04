package com.yigu.xiangqi.ui.recognition

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.repository.GameRepository
import com.yigu.xiangqi.data.repository.GameSummary
import com.yigu.xiangqi.domain.model.PiecePosition
import com.yigu.xiangqi.domain.recognition.BoardRecognitionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RecognitionState {
    object Idle : RecognitionState()
    data class Recognizing(val imageUri: Uri) : RecognitionState()
    data class Searching(val imageUri: Uri, val board: List<PiecePosition>) : RecognitionState()
    data class Result(
        val imageUri: Uri,
        val board: List<PiecePosition>,
        val matchGame: GameSummary?
    ) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

@HiltViewModel
class PhotoRecognitionViewModel @Inject constructor(
    private val recognitionService: BoardRecognitionService,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    fun processImage(uri: Uri) {
        viewModelScope.launch {
            _state.value = RecognitionState.Recognizing(uri)
            try {
                val board = recognitionService.recognizeBoard(uri)
                _state.value = RecognitionState.Searching(uri, board)
                
                val matchGame = gameRepository.findSimilarGame(board)
                _state.value = RecognitionState.Result(uri, board, matchGame)
            } catch (e: Exception) {
                _state.value = RecognitionState.Error(e.message ?: "识别失败")
            }
        }
    }

    fun reset() {
        _state.value = RecognitionState.Idle
    }
}
