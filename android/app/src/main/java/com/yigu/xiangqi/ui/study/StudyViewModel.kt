package com.yigu.xiangqi.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yigu.xiangqi.data.local.entity.UserProgressEntity
import com.yigu.xiangqi.data.repository.GameRepository
import com.yigu.xiangqi.data.repository.GameSummary
import com.yigu.xiangqi.data.repository.ManualRepository
import com.yigu.xiangqi.data.repository.ProgressRepository
import com.yigu.xiangqi.domain.model.Manual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 预设学习路径 */
data class StudyPlan(
    val level: String,
    val description: String,
    val manualIds: List<String>,
)

val STUDY_PLANS = listOf(
    StudyPlan("入门", "短残局练习，培养基本杀法", listOf("百变象棋谱", "百局象棋谱")),
    StudyPlan("初级", "经典残局研究", listOf("渊深海阔", "心武残编")),
    StudyPlan("中级", "精妙残局与全局", listOf("适情雅趣", "韬略元机")),
    StudyPlan("进阶", "古典全局名谱", listOf("橘中秘", "梅花谱")),
    StudyPlan("高级", "深度全局研究", listOf("金鹏十八变", "梅花泉", "梅花心谱")),
)

data class StudyUiState(
    val plans: List<StudyPlan> = STUDY_PLANS,
    val recentProgress: List<UserProgressEntity> = emptyList(),
    val dailyGameId: String? = null,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            progressRepository.getRecentProgress().collect { recent ->
                _state.value = _state.value.copy(recentProgress = recent.take(10))
            }
        }
        viewModelScope.launch {
            val random = gameRepository.getRandomUnstudied()
            _state.value = _state.value.copy(dailyGameId = random?.id)
        }
    }
}
