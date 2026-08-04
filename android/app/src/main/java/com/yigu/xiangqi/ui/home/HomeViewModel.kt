package com.yigu.xiangqi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.importer.DataImporter
import com.yigu.xiangqi.data.local.entity.UserProgressEntity
import com.yigu.xiangqi.data.repository.GameRepository
import com.yigu.xiangqi.data.repository.ManualRepository
import com.yigu.xiangqi.data.repository.ProgressRepository
import com.yigu.xiangqi.domain.model.Manual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption {
    DEFAULT, RECENT_ACCESS, MOST_ACCESS, ADD_TIME
}

data class HomeUiState(
    val loading: Boolean = true,
    val manuals: List<ManualWithProgress> = emptyList(), // original all
    val displayedManuals: List<ManualWithProgress> = emptyList(), // filtered & sorted
    val continueGame: ContinueItem? = null,
    val completedCount: Int = 0,
    val studyDays: Int = 0,
    val dailyGameId: String? = null,
    val shelfTotalGames: Int = 0,
    val selectedTag: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val sortOption: SortOption = SortOption.DEFAULT,
    val sortDescending: Boolean = true,
    val isListView: Boolean = false,
)

data class ManualWithProgress(
    val manual: Manual,
    val completedGames: Int = 0,
    val studiedGames: Int = 0,
)

data class ContinueItem(
    val gameId: String,
    val gameTitle: String,
    val manualName: String,
    val currentStep: Int,
    val totalSteps: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dataImporter: DataImporter,
    private val manualRepository: ManualRepository,
    private val gameRepository: GameRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataImporter.importIfNeeded()
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                manualRepository.getAllManuals(),
                progressRepository.countCompletedGroupByManual(),
                progressRepository.countStudiedGroupByManual(),
            ) { manuals, completionCounts, studiedCounts ->
                val completedMap = completionCounts.associate { it.manualId to it.count }
                val studiedMap = studiedCounts.associate { it.manualId to it.count }
                manuals.map { m ->
                    ManualWithProgress(
                        manual = m,
                        completedGames = completedMap[m.id] ?: 0,
                        studiedGames = studiedMap[m.id] ?: 0,
                    )
                }
            }.collect { withProgress ->
                _state.value = _state.value.copy(
                    loading = false,
                    manuals = withProgress,
                )
                applyFilterAndSort()
            }
        }

        viewModelScope.launch {
            progressRepository.countStudyDays().collect { days ->
                _state.value = _state.value.copy(studyDays = days)
            }
        }

        viewModelScope.launch {
            val last = progressRepository.getLastInProgress()
            if (last != null) {
                val game = gameRepository.getGame(last.gameId)
                if (game != null) {
                    val manual = manualRepository.getManual(game.manualId)
                    _state.value = _state.value.copy(
                        continueGame = ContinueItem(
                            gameId = game.id,
                            gameTitle = game.title,
                            manualName = manual?.name ?: "",
                            currentStep = last.currentStep,
                            totalSteps = game.moves.size,
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            val random = gameRepository.getRandomUnstudied()
            _state.value = _state.value.copy(dailyGameId = random?.id)
        }
    }

    fun setTag(tag: String?) {
        _state.value = _state.value.copy(selectedTag = tag)
        applyFilterAndSort()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilterAndSort()
    }

    fun toggleSearch() {
        val isSearching = !_state.value.isSearching
        _state.value = _state.value.copy(
            isSearching = isSearching,
            searchQuery = if (isSearching) _state.value.searchQuery else ""
        )
        if (!isSearching) {
            applyFilterAndSort()
        }
    }

    fun togglePin(manualId: String, currentPinned: Boolean) {
        viewModelScope.launch {
            manualRepository.updatePinned(manualId, !currentPinned)
            // No need to manually update state here because the flow from getAllManuals() will emit new data
        }
    }

    fun setSortOption(option: SortOption) {
        if (_state.value.sortOption == option) {
            _state.value = _state.value.copy(sortDescending = !_state.value.sortDescending)
        } else {
            _state.value = _state.value.copy(sortOption = option, sortDescending = true)
        }
        applyFilterAndSort()
    }

    fun toggleViewMode() {
        _state.value = _state.value.copy(isListView = !_state.value.isListView)
    }

    private fun applyFilterAndSort() {
        val s = _state.value
        var list = s.manuals

        // 1. 过滤标签
        if (s.selectedTag != null) {
            list = list.filter { 
                if (s.selectedTag == "全局") !it.manual.type.contains("残局")
                else it.manual.type.contains(s.selectedTag) 
            }
        }

        // 2. 搜索过滤
        if (s.searchQuery.isNotBlank()) {
            list = list.filter { it.manual.name.contains(s.searchQuery, ignoreCase = true) }
        }

        // 3. 排序
        val comparator = when (s.sortOption) {
            SortOption.DEFAULT -> compareBy<ManualWithProgress> { it.manual.name }
            SortOption.RECENT_ACCESS -> compareBy { it.manual.lastAccessTime }
            SortOption.MOST_ACCESS -> compareBy { it.manual.viewCount }
            SortOption.ADD_TIME -> compareBy { it.manual.addTime }
        }

        list = if (s.sortDescending) {
            list.sortedWith(comparator.reversed())
        } else {
            list.sortedWith(comparator)
        }

        // 4. 置顶排序 (不受常规排序影响)
        list = list.sortedByDescending { it.manual.isPinned }

        val filteredTotalGames = list.sumOf { it.manual.totalGames }
        val filteredCompletedCount = list.sumOf { it.completedGames }

        _state.value = s.copy(
            displayedManuals = list,
            shelfTotalGames = filteredTotalGames,
            completedCount = filteredCompletedCount
        )
    }
}
