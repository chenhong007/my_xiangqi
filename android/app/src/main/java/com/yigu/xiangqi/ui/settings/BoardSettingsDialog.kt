package com.yigu.xiangqi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yigu.xiangqi.domain.sound.SoundType
import com.yigu.xiangqi.ui.board.MoveHighlightStyle
import com.yigu.xiangqi.ui.board.PieceDropEffectType

data class SpeedOption(val label: String, val ms: Long)

private val SPEED_OPTIONS = listOf(
    SpeedOption("0.5秒", 500),
    SpeedOption("1秒", 1000),
    SpeedOption("1.5秒", 1500),
    SpeedOption("2秒", 2000),
    SpeedOption("3秒", 3000),
    SpeedOption("5秒", 5000),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSettingsDialog(
    currentEffect: PieceDropEffectType,
    onEffectSelected: (PieceDropEffectType) -> Unit,
    currentHighlight: MoveHighlightStyle,
    onHighlightSelected: (MoveHighlightStyle) -> Unit,
    currentSpeedMs: Long,
    onSpeedSelected: (Long) -> Unit,
    currentSoundType: SoundType,
    onSoundSelected: (SoundType) -> Unit,
    guessMode: Boolean,
    onGuessModeChanged: (Boolean) -> Unit,
    guessResponseDelayMs: Long,
    onGuessResponseDelayChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var delaySliderValue by remember { mutableFloatStateOf(guessResponseDelayMs.toFloat()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp) // 底部留白
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "棋谱设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // ── 猜招模式 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("猜招模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("开启后自己走棋，电脑自动应招", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = guessMode,
                            onCheckedChange = onGuessModeChanged
                        )
                    }

                    if (guessMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("电脑响应延迟", style = MaterialTheme.typography.bodyMedium)
                            Text("${delaySliderValue.toLong()} ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = delaySliderValue,
                            onValueChange = { delaySliderValue = it },
                            onValueChangeFinished = { onGuessResponseDelayChanged(delaySliderValue.toLong()) },
                            valueRange = 100f..4000f,
                            steps = 38, // 100ms per step
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.1秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 自动播放速度 ──
            SettingSection(title = "自动播放速度") {
                HorizontalOptionList(
                    options = SPEED_OPTIONS,
                    selectedOption = SPEED_OPTIONS.find { it.ms == currentSpeedMs } ?: SPEED_OPTIONS[3],
                    onOptionSelected = { onSpeedSelected(it.ms) },
                    labelSelector = { it.label }
                )
            }

            // ── 走棋标记样式 ──
            SettingSection(title = "走棋标记样式") {
                HorizontalOptionList(
                    options = MoveHighlightStyle.entries.toList(),
                    selectedOption = currentHighlight,
                    onOptionSelected = onHighlightSelected,
                    labelSelector = { it.displayName }
                )
            }

            // ── 落子效果 ──
            SettingSection(title = "落子效果") {
                HorizontalOptionList(
                    options = PieceDropEffectType.entries.toList(),
                    selectedOption = currentEffect,
                    onOptionSelected = onEffectSelected,
                    labelSelector = { it.displayName }
                )
            }

            // ── 落子音效 ──
            SettingSection(title = "落子音效") {
                HorizontalOptionList(
                    options = SoundType.entries.toList(),
                    selectedOption = currentSoundType,
                    onOptionSelected = onSoundSelected,
                    labelSelector = { it.label }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun <T> HorizontalOptionList(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelSelector: (T) -> String,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(options) { option ->
            val selected = option == selectedOption
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onOptionSelected(option) }
            ) {
                Text(
                    text = labelSelector(option),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
