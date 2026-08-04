package com.yigu.xiangqi.ui.gamelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.repository.GameRepository
import com.yigu.xiangqi.data.repository.GameSummary
import com.yigu.xiangqi.data.repository.ManualRepository
import com.yigu.xiangqi.data.repository.ProgressRepository
import com.yigu.xiangqi.domain.model.Manual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GameSortOption {
    DEFAULT, 
    RECENT_ACCESS, 
    MOST_ACCESS, 
    LEAST_ACCESS, 
    STUDY_COUNT_DESC, 
    STUDY_COUNT_ASC
}

data class GameListUiState(
    val loading: Boolean = true,
    val manual: Manual? = null,
    val games: List<GameSummary> = emptyList(),
    val searchQuery: String = "",
    val sortOption: GameSortOption = GameSortOption.DEFAULT,
)

@HiltViewModel
class GameListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manualRepository: ManualRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val manualId: String = savedStateHandle["manualId"] ?: ""
    private val _state = MutableStateFlow(GameListUiState())
    val state: StateFlow<GameListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val manual = manualRepository.getManual(manualId)
            _state.value = _state.value.copy(manual = manual)
            manualRepository.recordAccess(manualId)
        }
        viewModelScope.launch {
            gameRepository.getGamesByManual(manualId).collect { games ->
                _state.value = _state.value.copy(loading = false, games = games)
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setSortOption(option: GameSortOption) {
        _state.value = _state.value.copy(sortOption = option)
    }

    val filteredGames: StateFlow<List<GameSummary>> = _state.map { s ->
        var list = s.games
        if (s.searchQuery.isNotBlank()) {
            list = list.filter { it.title.contains(s.searchQuery, ignoreCase = true) }
        }
        when (s.sortOption) {
            GameSortOption.DEFAULT -> list
            GameSortOption.RECENT_ACCESS -> list.sortedWith(compareByDescending<GameSummary> { it.lastStudiedAt ?: 0L }.thenBy { it.id })
            GameSortOption.MOST_ACCESS -> list.sortedWith(compareByDescending<GameSummary> { it.viewCount }.thenBy { it.id })
            GameSortOption.LEAST_ACCESS -> list.sortedWith(compareBy<GameSummary> { it.viewCount }.thenBy { it.id })
            GameSortOption.STUDY_COUNT_DESC -> list.sortedWith(compareByDescending<GameSummary> { it.completionCount }.thenBy { it.id })
            GameSortOption.STUDY_COUNT_ASC -> list.sortedWith(compareBy<GameSummary> { it.completionCount }.thenBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
