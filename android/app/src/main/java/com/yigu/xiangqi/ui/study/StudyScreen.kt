package com.yigu.xiangqi.ui.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onGameClick: (String) -> Unit,
    onPhotoRecognitionClick: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onPhotoRecognitionClick() },
                icon = { Icon(Icons.Default.CameraAlt, "拍照识谱") },
                text = { Text("拍照识谱") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
        item {
            Text(
                "学习",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        // 每日一局
        state.dailyGameId?.let { id ->
            item {
                Card(
                    onClick = { onGameClick(id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Today, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("每日一局", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("每天学习一局古谱棋局", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // 学习路径
        item {
            Text(
                "学习路径",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        items(state.plans) { plan ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(plan.level.first().toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plan.level, fontWeight = FontWeight.SemiBold)
                        Text(plan.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            plan.manualIds.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 最近学习
        if (state.recentProgress.isNotEmpty()) {
            item {
                Text(
                    "最近学习",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            items(state.recentProgress) { progress ->
                ListItem(
                    headlineContent = { Text("棋局 #${progress.gameId}") },
                    supportingContent = {
                        Text("第${progress.currentStep}步 · ${progress.status}")
                    },
                    leadingContent = {
                        Icon(
                            when (progress.status) {
                                "COMPLETED" -> Icons.Default.CheckCircle
                                "IN_PROGRESS" -> Icons.Default.PlayCircle
                                else -> Icons.Default.RadioButtonUnchecked
                            },
                            null,
                        )
                    },
                    modifier = Modifier.clickable { onGameClick(progress.gameId) },
                )
            }
        }
    }
    }
}