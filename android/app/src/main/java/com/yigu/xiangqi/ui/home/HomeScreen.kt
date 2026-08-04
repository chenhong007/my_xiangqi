package com.yigu.xiangqi.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onManualClick: (String) -> Unit,
    onGameClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // 顶部标题
        item {
            Text(
                "弈古",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        // 学习数据卡片
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("已学", "${state.completedCount}/${state.shelfTotalGames} 局", Modifier.weight(1f))
                StatCard("学习", "${state.studyDays} 天", Modifier.weight(1f))
            }
        }

        // 继续学习
        state.continueGame?.let { item ->
            item {
                ContinueCard(
                    item = item,
                    onClick = { onGameClick(item.gameId) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // 每日推荐
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("每日一局", fontWeight = FontWeight.Bold)
                            Text("点击开始今天的学习", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // 古谱书架标题及控制区
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isSearching) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            placeholder = { Text("搜索古谱...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.toggleSearch() }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消搜索")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                "古谱书架",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "共${state.displayedManuals.size}古谱",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 搜索按钮
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }

                            // 过滤菜单
                            var showFilterMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "筛选")
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    val filterOptions = listOf(
                                        null to "全部",
                                        "全局" to "全局",
                                        "残局" to "残局"
                                    )
                                    filterOptions.forEach { (tag, label) ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(label)
                                                    if (state.selectedTag == tag) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.setTag(tag)
                                                showFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 排序菜单
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
                                        SortOption.DEFAULT to "默认排序",
                                        SortOption.RECENT_ACCESS to "最近访问",
                                        SortOption.MOST_ACCESS to "访问最多",
                                        SortOption.ADD_TIME to "添加时间",
                                    )
                                    sortOptions.forEach { (option, label) ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(label)
                                                    if (state.sortOption == option) {
                                                        Icon(
                                                            if (state.sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
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
                            
                            // 视图切换
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    if (state.isListView) Icons.Default.GridView else Icons.Default.ViewList,
                                    contentDescription = "切换视图"
                                )
                            }
                        }
                    }
                }
            }
        }

        // 书架内容
        if (state.isListView) {
            items(state.displayedManuals) { item ->
                ManualListCard(
                    item = item,
                    onClick = { onManualClick(item.manual.id) },
                    onPinClick = { viewModel.togglePin(item.manual.id, item.manual.isPinned) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            val chunkedManuals = state.displayedManuals.chunked(2)
            items(chunkedManuals) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (item in rowItems) {
                        ManualGridCard(
                            item = item,
                            onClick = { onManualClick(item.manual.id) },
                            onPinClick = { viewModel.togglePin(item.manual.id, item.manual.isPinned) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("继续学习", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(item.gameTitle, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.manualName} · 第${item.currentStep}/${item.totalSteps}步",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ManualListCard(
    item: ManualWithProgress, 
    onClick: () -> Unit, 
    onPinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标区域
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MenuBook, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // 内容区域
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.manual.name, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = if (item.manual.type.contains("残局")) "残局" else "全局",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                val progress = if (item.manual.totalGames > 0) {
                    item.completedGames.toFloat() / item.manual.totalGames
                } else 0f
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "已学 ${item.completedGames} / ${item.manual.totalGames} 局",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            
            // 置顶按钮
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (item.manual.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = if (item.manual.isPinned) "取消置顶" else "置顶",
                    tint = if (item.manual.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ManualGridCard(
    item: ManualWithProgress, 
    onClick: () -> Unit, 
    onPinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick, 
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 图标区域
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MenuBook, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // 标题
                Text(
                    text = item.manual.name, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(Modifier.height(8.dp))
                
                // 标签和局数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = if (item.manual.type.contains("残局")) "残局" else "全局",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Text(
                        text = "共 ${item.manual.totalGames} 局",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // 进度条（始终显示以保持高度一致）
                val progress = if (item.manual.totalGames > 0) {
                    item.completedGames.toFloat() / item.manual.totalGames
                } else 0f
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已学 ${item.completedGames} 局",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
            
            // 置顶按钮
            IconButton(
                onClick = onPinClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (item.manual.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = if (item.manual.isPinned) "取消置顶" else "置顶",
                    tint = if (item.manual.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
