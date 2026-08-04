package com.yigu.xiangqi.ui.favorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onGameClick: (String) -> Unit,
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf("收藏", "记录", "笔记")

    Column(Modifier.fillMaxSize()) {
        Text(
            "收藏",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        TabRow(selectedTabIndex = state.selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(title) },
                )
            }
        }

        when (state.selectedTab) {
            0 -> FavoriteList(state.favorites, onGameClick)
            1 -> HistoryList(state.history, onGameClick)
            2 -> NotesList(state.notes, onGameClick)
        }
    }
}

@Composable
private fun FavoriteList(
    favorites: List<com.yigu.xiangqi.data.local.entity.FavoriteEntity>,
    onGameClick: (String) -> Unit,
) {
    if (favorites.isEmpty()) {
        EmptyState("暂无收藏", "在打谱页点击收藏按钮添加")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(favorites) { fav ->
            ListItem(
                headlineContent = { Text("棋局 #${fav.gameId}") },
                supportingContent = { Text(formatDate(fav.createdAt)) },
                leadingContent = { Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onGameClick(fav.gameId) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun HistoryList(
    history: List<com.yigu.xiangqi.data.local.entity.UserProgressEntity>,
    onGameClick: (String) -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState("暂无学习记录", "开始学习后记录会出现在这里")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(history) { progress ->
            ListItem(
                headlineContent = { Text("棋局 #${progress.gameId}") },
                supportingContent = {
                    Text("第${progress.currentStep}步 · ${formatDate(progress.lastStudiedAt)}")
                },
                leadingContent = {
                    Icon(
                        when (progress.status) {
                            "COMPLETED" -> Icons.Default.CheckCircle
                            else -> Icons.Default.PlayCircle
                        },
                        null,
                        tint = when (progress.status) {
                            "COMPLETED" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingContent = {
                    if (progress.guessTotal > 0) {
                        Text("${progress.guessCorrect}/${progress.guessTotal}")
                    }
                },
                modifier = Modifier.clickable { onGameClick(progress.gameId) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun NotesList(
    notes: List<com.yigu.xiangqi.data.local.entity.UserNoteEntity>,
    onGameClick: (String) -> Unit,
) {
    if (notes.isEmpty()) {
        EmptyState("暂无笔记", "在打谱时可对任意步骤添加笔记")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(notes) { note ->
            ListItem(
                headlineContent = { Text(note.content, maxLines = 2) },
                supportingContent = { Text("棋局 #${note.gameId} · 第${note.stepIndex}步") },
                leadingContent = { Icon(Icons.Default.StickyNote2, null) },
                modifier = Modifier.clickable { onGameClick(note.gameId) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Inbox,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
