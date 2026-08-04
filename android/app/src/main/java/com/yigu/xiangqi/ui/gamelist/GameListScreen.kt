package com.yigu.xiangqi.ui.gamelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.yigu.xiangqi.data.repository.GameSummary
import com.yigu.xiangqi.ui.gamelist.GameSortOption
import com.yigu.xiangqi.ui.theme.CorrectGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    onGameClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GameListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val filtered by viewModel.filteredGames.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.manual?.name ?: "棋谱列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortOptions = listOf(
                                GameSortOption.DEFAULT to "默认排序",
                                GameSortOption.RECENT_ACCESS to "最近访问",
                                GameSortOption.MOST_ACCESS to "访问最多",
                                GameSortOption.LEAST_ACCESS to "访问最少",
                                GameSortOption.STUDY_COUNT_DESC to "学习最多",
                                GameSortOption.STUDY_COUNT_ASC to "学习最少"
                            )
                            sortOptions.forEach { (option, label) ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(label)
                                            if (state.sortOption == option) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // 古谱信息头
            state.manual?.let { m ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Column {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${m.type} · ${m.totalGames} 局", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 搜索框
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索棋局...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val scope = rememberCoroutineScope()
                
                // 计算学习进度的目标定位索引
                val targetIndex = remember(filtered) {
                    val firstUncompleted = filtered.indexOfFirst { it.completionCount == 0 }
                    when {
                        filtered.isEmpty() -> 0
                        firstUncompleted == -1 -> filtered.size - 1 // 全部已完成，定位到最后一局
                        firstUncompleted == 0 -> 0 // 全未完成，定位到第一局
                        else -> firstUncompleted - 1 // 定位到已完成和未完成的交界处
                    }
                }

                // 自动定位到学习和非学习的中间线 (只在初次加载时执行一次)
                var hasAutoScrolled by remember { mutableStateOf(false) }
                LaunchedEffect(filtered) {
                    if (!hasAutoScrolled && filtered.isNotEmpty()) {
                        listState.animateScrollToItem(targetIndex)
                        hasAutoScrolled = true
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 80.dp), // 为悬浮按钮留出空间
                    ) {
                        items(filtered) { game ->
                            GameRow(game = game, onClick = { onGameClick(game.id) })
                        }
                    }

                    // 悬浮定位按钮
                    if (filtered.isNotEmpty()) {
                        val isAtTarget by remember {
                            derivedStateOf {
                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                // 只要目标项或者它的下一项在可视范围内，就认为已经定位到了
                                visibleItems.any { it.index == targetIndex || it.index == targetIndex + 1 }
                            }
                        }

                        if (!isAtTarget) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch { 
                                        listState.animateScrollToItem(targetIndex) 
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Default.MyLocation, "定位到学习进度")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GameSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(game.title) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(game.result, style = MaterialTheme.typography.labelSmall)
                Text("${game.moveCount}步", style = MaterialTheme.typography.labelSmall)
                if (game.hasVariations) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(14.dp))
                }
                if (game.hasComments) {
                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(14.dp))
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudyBadge(game)
                Icon(Icons.Default.ChevronRight, null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun StudyBadge(game: GameSummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            game.completionCount > 0 -> {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已学完",
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                    tint = CorrectGreen,
                )
                Text(
                    "${game.completionCount}遍",
                    style = MaterialTheme.typography.labelSmall,
                    color = CorrectGreen,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            game.studyStatus == "IN_PROGRESS" -> {
                Text(
                    "学习中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}
