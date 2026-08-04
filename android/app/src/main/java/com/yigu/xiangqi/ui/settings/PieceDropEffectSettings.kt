package com.yigu.xiangqi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yigu.xiangqi.ui.board.MoveHighlightStyle
import com.yigu.xiangqi.ui.board.PieceDropEffectType
import kotlinx.coroutines.launch

/**
 * 落子效果设置对话框
 */
@Composable
fun PieceDropEffectSettingsDialog(
    currentEffect: PieceDropEffectType,
    onEffectSelected: (PieceDropEffectType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectDescriptions = mapOf(
        PieceDropEffectType.TIANTIAN_XIANGQI to "天天象棋风格 - 原始位置小圆心，落地位置光晕效果",
        PieceDropEffectType.FADE_IN_OUT to "简约风格 - 平缓的淡入淡出效果",
        PieceDropEffectType.BOUNCE to "弹跳风格 - 落地后有弹跳效果",
        PieceDropEffectType.PULSE to "脉冲风格 - 落地后有脉冲波纹",
        PieceDropEffectType.NONE to "无动画 - 直接显示棋子",
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择落子效果") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PieceDropEffectType.values()) { effect ->
                    val isSelected = effect == currentEffect
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEffectSelected(effect) }
                            .padding(horizontal = 8.dp),
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onEffectSelected(effect) },
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = effect.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = effectDescriptions[effect] ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        modifier = modifier,
    )
}

/**
 * 落子效果显示名称
 */
val PieceDropEffectType.displayName: String
    get() = when (this) {
        PieceDropEffectType.TIANTIAN_XIANGQI -> "天天象棋风格"
        PieceDropEffectType.FADE_IN_OUT -> "简约风格"
        PieceDropEffectType.BOUNCE -> "弹跳风格"
        PieceDropEffectType.PULSE -> "脉冲风格"
        PieceDropEffectType.NONE -> "无动画"
    }

/**
 * 走棋高亮样式显示名称
 */
val MoveHighlightStyle.displayName: String
    get() = when (this) {
        MoveHighlightStyle.TIANTIAN -> "天天象棋（圆环+光晕）"
        MoveHighlightStyle.RING -> "圆环"
        MoveHighlightStyle.CORNERS -> "四角标记"
        MoveHighlightStyle.RECT -> "矩形（旧版）"
        MoveHighlightStyle.NONE -> "不显示"
    }

/**
 * 落子效果设置面板（用于设置屏幕）
 */
@Composable
fun PieceDropEffectSettings(
    currentEffect: PieceDropEffectType,
    onEffectSelected: (PieceDropEffectType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        PieceDropEffectSettingsDialog(
            currentEffect = currentEffect,
            onEffectSelected = { effect ->
                onEffectSelected(effect)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "落子效果",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = currentEffect.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "选择 >",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
