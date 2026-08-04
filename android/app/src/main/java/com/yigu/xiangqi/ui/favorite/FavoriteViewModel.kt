package com.yigu.xiangqi.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.local.entity.FavoriteEntity
import com.yigu.xiangqi.data.local.entity.UserNoteEntity
import com.yigu.xiangqi.data.local.entity.UserProgressEntity
import com.yigu.xiangqi.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class FavoriteUiState(
    val selectedTab: Int = 0,
    val favorites: List<FavoriteEntity> = emptyList(),
    val history: List<UserProgressEntity> = emptyList(),
    val notes: List<UserNoteEntity> = emptyList(),
)

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _tab = MutableStateFlow(0)

    val state: StateFlow<FavoriteUiState> = combine(
        _tab,
        progressRepository.getAllFavorites(),
        progressRepository.getRecentProgress(),
        progressRepository.getAllNotes(),
    ) { tab, favs, history, notes ->
        FavoriteUiState(
            selectedTab = tab,
            favorites = favs,
            history = history,
            notes = notes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavoriteUiState())

    fun selectTab(index: Int) { _tab.value = index }
}
