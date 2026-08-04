package com.yigu.xiangqi.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yigu.xiangqi.domain.preferences.DisplayMode
import com.yigu.xiangqi.domain.sound.SoundType
import com.yigu.xiangqi.ui.board.MoveHighlightStyle
import com.yigu.xiangqi.ui.board.PieceDropEffectType
import com.yigu.xiangqi.ui.settings.displayName

private fun formatSpeed(ms: Long): String = when (ms) {
    500L -> "0.5秒/步"
    1000L -> "1秒/步"
    1500L -> "1.5秒/步"
    2000L -> "2秒/步"
    3000L -> "3秒/步"
    5000L -> "5秒/步"
    else -> "${ms / 1000.0}秒/步"
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSoundPicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showDelayPicker by remember { mutableStateOf(false) }
    var showHighlightPicker by remember { mutableStateOf(false) }
    var showEffectPicker by remember { mutableStateOf(false) }
    var showDisplayModePicker by remember { mutableStateOf(false) }

    if (showDisplayModePicker) {
        DisplayModePickerDialog(
            current = state.displayMode,
            onSelect = { viewModel.setDisplayMode(it); showDisplayModePicker = false },
            onDismiss = { showDisplayModePicker = false },
        )
    }

    if (showSoundPicker) {
        SoundPickerDialog(
            current = state.soundType,
            onSelect = { viewModel.setSoundType(it); showSoundPicker = false },
            onDismiss = { showSoundPicker = false },
        )
    }
    if (showSpeedPicker) {
        SpeedPickerDialog(
            currentMs = state.autoPlaySpeedMs,
            onSelect = { viewModel.setAutoPlaySpeed(it); showSpeedPicker = false },
            onDismiss = { showSpeedPicker = false },
        )
    }
    if (showDelayPicker) {
        DelayPickerDialog(
            currentMs = state.guessResponseDelayMs,
            onSelect = { viewModel.setGuessResponseDelay(it); showDelayPicker = false },
            onDismiss = { showDelayPicker = false },
        )
    }
    if (showHighlightPicker) {
        HighlightPickerDialog(
            current = state.highlightStyle,
            onSelect = { viewModel.setHighlightStyle(it); showHighlightPicker = false },
            onDismiss = { showHighlightPicker = false },
        )
    }
    if (showEffectPicker) {
        EffectPickerDialog(
            current = state.pieceDropEffect,
            onSelect = { viewModel.setPieceDropEffect(it); showEffectPicker = false },
            onDismiss = { showEffectPicker = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "学习统计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem(icon = Icons.Default.CheckCircle, value = "${state.completedGames}", label = "已完成")
                    StatItem(icon = Icons.Default.CalendarMonth, value = "${state.studyDays}", label = "学习天数")
                    StatItem(
                        icon = Icons.Default.Quiz,
                        value = if (state.guessTotal > 0) {
                            "${state.guessCorrect * 100 / state.guessTotal}%"
                        } else "—",
                        label = "猜招正确率",
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Card(Modifier.fillMaxWidth()) {
            SettingsClickableItem(
                icon = Icons.Default.Palette,
                title = "显示模式",
                value = state.displayMode.displayName,
                onClick = { showDisplayModePicker = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("猜招模式") },
                trailingContent = {
                    Switch(
                        checked = state.guessMode,
                        onCheckedChange = { viewModel.setGuessMode(it) }
                    )
                },
                leadingContent = { Icon(Icons.Default.Quiz, null) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsClickableItem(
                icon = Icons.Default.Timer,
                title = "猜招电脑响应延迟",
                value = "${state.guessResponseDelayMs}ms",
                onClick = { showDelayPicker = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsClickableItem(
                icon = Icons.Default.Speed,
                title = "自动播放速度",
                value = formatSpeed(state.autoPlaySpeedMs),
                onClick = { showSpeedPicker = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsClickableItem(
                icon = Icons.Default.Highlight,
                title = "走棋标记样式",
                value = state.highlightStyle.displayName,
                onClick = { showHighlightPicker = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsClickableItem(
                icon = Icons.Default.Animation,
                title = "落子效果",
                value = state.pieceDropEffect.displayName,
                onClick = { showEffectPicker = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsClickableItem(
                icon = Icons.Default.VolumeUp,
                title = "落子音效",
                value = state.soundType.label,
                onClick = { showSoundPicker = true },
            )
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            SettingsItem(Icons.Default.Info, "关于弈古", "v1.0.0")
        }

        Spacer(Modifier.weight(1f))

        Text(
            "弈古 v1.0.0 · 学习象棋经典古谱",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = { Icon(icon, null) },
    )
}

@Composable
private fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp))
            }
        },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SoundPickerDialog(
    current: SoundType,
    onSelect: (SoundType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择落子音效") },
        text = {
            Column {
                SoundType.entries.forEach { type ->
                    PickerRow(
                        label = type.label,
                        selected = type == current,
                        onClick = { onSelect(type) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun SpeedPickerDialog(
    currentMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(500L, 1000L, 1500L, 2000L, 3000L, 5000L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动播放速度") },
        text = {
            Column {
                options.forEach { ms ->
                    PickerRow(
                        label = formatSpeed(ms),
                        selected = ms == currentMs,
                        onClick = { onSelect(ms) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DelayPickerDialog(
    currentMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(currentMs.toFloat()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("猜招电脑响应延迟") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${sliderValue.toLong()} ms",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 100f..4000f,
                    steps = 38, // 100ms per step
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.1秒", style = MaterialTheme.typography.labelSmall)
                    Text("4秒", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(sliderValue.toLong()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun HighlightPickerDialog(
    current: MoveHighlightStyle,
    onSelect: (MoveHighlightStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("走棋标记样式") },
        text = {
            Column {
                MoveHighlightStyle.entries.forEach { style ->
                    PickerRow(
                        label = style.displayName,
                        selected = style == current,
                        onClick = { onSelect(style) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun EffectPickerDialog(
    current: PieceDropEffectType,
    onSelect: (PieceDropEffectType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("落子效果") },
        text = {
            Column {
                PieceDropEffectType.entries.forEach { effect ->
                    PickerRow(
                        label = effect.displayName,
                        selected = effect == current,
                        onClick = { onSelect(effect) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DisplayModePickerDialog(
    current: DisplayMode,
    onSelect: (DisplayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择显示模式") },
        text = {
            Column {
                DisplayMode.entries.forEach { mode ->
                    PickerRow(
                        label = mode.displayName,
                        selected = mode == current,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
