package com.yigu.xiangqi.ui.board

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yigu.xiangqi.ui.settings.BoardSettingsDialog
import com.yigu.xiangqi.ui.theme.CorrectGreen
import com.yigu.xiangqi.ui.theme.PieceBlack
import com.yigu.xiangqi.ui.theme.PieceRed
import com.yigu.xiangqi.ui.theme.WrongRed
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(
    onBack: () -> Unit,
    onNextGame: (String) -> Unit,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (viewModel.state.value.autoPlaying) {
                    viewModel.toggleAutoPlay()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val game = state.game ?: return
    
    if (showSettings) {
        BoardSettingsDialog(
            currentEffect = state.pieceDropEffectConfig.type,
            onEffectSelected = { viewModel.setPieceDropEffect(it) },
            currentHighlight = state.highlightStyle,
            onHighlightSelected = { viewModel.setHighlightStyle(it) },
            currentSpeedMs = state.autoPlaySpeedMs,
            onSpeedSelected = { viewModel.setAutoPlaySpeed(it) },
            currentSoundType = state.soundType,
            onSoundSelected = { viewModel.setSoundType(it) },
            guessMode = state.guessMode,
            onGuessModeChanged = { viewModel.setGuessMode(it) },
            guessResponseDelayMs = state.guessResponseDelayMs,
            onGuessResponseDelayChanged = { viewModel.setGuessResponseDelay(it) },
            onDismiss = { showSettings = false },
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // 第一行：返回按钮 + 完整标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text(
                        game.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 第二行：操作按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (state.guessMode) {
                        IconButton(onClick = { viewModel.toggleShowMovesInGuessMode() }) {
                            Icon(
                                if (state.showMovesInGuessMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "显示/隐藏走法"
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                    IconButton(onClick = { viewModel.toggleFlip() }) {
                        Icon(Icons.Default.SwapVert, "翻转")
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "收藏",
                            tint = if (state.isFavorite) WrongRed else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 学习统计
            if (state.viewCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.School, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "第 ${state.viewCount} 次学习",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.completionCount > 0) {
                        Text(
                            "· 已完成 ${state.completionCount} 遍",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 分支面包屑
            if (!state.isOnMainLine) {
                BranchBreadcrumb(
                    path = state.branchPath,
                    onReturnToMain = { viewModel.returnToMainLine() },
                    onExitBranch = { viewModel.exitVariation() },
                )
            }

            // 猜招状态提示
            if (state.guessMode) {
                GuessStatusBar(
                    state = state,
                    onNextGame = if (state.stepIndex == state.totalSteps && state.nextGameId != null) {
                        { onNextGame(state.nextGameId!!) }
                    } else null,
                    onHint = { viewModel.showHint() }
                )
            } else if (state.stepIndex == state.totalSteps && state.nextGameId != null && state.isOnMainLine) {
                GameEndStatusBar(
                    onNextGame = { onNextGame(state.nextGameId!!) }
                )
            }

            // 棋盘
            BoardCanvas(
                pieces = state.pieces,
                lastMoveFrom = state.lastFrom,
                lastMoveTo = state.lastTo,
                flipped = state.flipped,
                selectedCell = state.selectedCell,
                hintMove = state.hintMove,
                onCellTap = if (state.guessMode) {
                    { col, row -> viewModel.onBoardTap(col, row) }
                } else null,
                pieceDropEffectConfig = state.pieceDropEffectConfig,
                highlightStyle = state.highlightStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

            // 注释
            AnimatedVisibility(visible = state.comment != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        text = state.comment ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // 变着入口
            if (state.availableVariations.isNotEmpty() && !state.guessMode) {
                VariationChips(
                    variations = state.availableVariations,
                    onSelect = { viewModel.enterVariation(it) },
                )
            }

            // 着法列表
            if (!state.guessMode || state.showMovesInGuessMode) {
                MoveListPanel(
                    moves = state.currentMoves,
                    currentStep = state.stepIndex,
                    comments = state.game?.comments ?: emptyMap(),
                    onStepClick = { viewModel.goToStep(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 导航栏
            NavigationBar(
                stepIndex = state.stepIndex,
                totalSteps = state.totalSteps,
                autoPlaying = state.autoPlaying,
                onFirst = { viewModel.goToStart() },
                onPrev = { viewModel.goBackward() },
                onNext = { viewModel.goForward() },
                onLast = { viewModel.goToEnd() },
                onAutoPlay = { viewModel.toggleAutoPlay() },
            )
        }
    }
}

@Composable
private fun BranchBreadcrumb(
    path: List<String>,
    onReturnToMain: () -> Unit,
    onExitBranch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            path.joinToString(" > "),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onExitBranch, contentPadding = PaddingValues(4.dp)) {
            Text("返回上级", fontSize = 12.sp)
        }
        TextButton(onClick = onReturnToMain, contentPadding = PaddingValues(4.dp)) {
            Text("回主线", fontSize = 12.sp)
        }
    }
}

@Composable
private fun GuessStatusBar(
    state: BoardUiState,
    onNextGame: (() -> Unit)? = null,
    onHint: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Quiz, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "猜招模式",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        
        if (state.isNextMoveRed != null && onNextGame == null) {
            Spacer(Modifier.width(12.dp))
            Text(
                if (state.isNextMoveRed) "红方走" else "黑方走",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isNextMoveRed) PieceRed else PieceBlack,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.width(16.dp))
        Text(
            "正确 ${state.guessCorrect}/${state.guessTotal}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.weight(1f))
        
        state.guessResult?.let { result ->
            Text(
                text = if (result == GuessResult.CORRECT) "正确!" else "走错了",
                color = if (result == GuessResult.CORRECT) CorrectGreen else WrongRed,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
        }
        
        if (onNextGame != null) {
            Button(
                onClick = onNextGame,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("下一局", fontSize = 12.sp)
            }
        } else if (onHint != null && !state.isComputerThinking && state.stepIndex < state.totalSteps && state.hintMove == null) {
            OutlinedButton(
                onClick = onHint,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("提示", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GameEndStatusBar(
    onNextGame: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            "当前棋局已完成",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = onNextGame,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("下一局", fontSize = 12.sp)
        }
    }
}

@Composable
private fun VariationChips(
    variations: List<com.yigu.xiangqi.domain.model.Variation>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "变着:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        variations.forEachIndexed { idx, v ->
            AssistChip(
                onClick = { onSelect(idx) },
                label = {
                    val firstNotation = v.moves.firstOrNull()?.notation ?: "..."
                    Text("$firstNotation (${v.moves.size}步)", fontSize = 12.sp)
                },
            )
        }
    }
}

@Composable
private fun MoveListPanel(
    moves: List<com.yigu.xiangqi.domain.model.Move>,
    currentStep: Int,
    comments: Map<String, String>,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentStep) {
        if (currentStep > 0) {
            scope.launch { listState.animateScrollToItem(maxOf(0, (currentStep - 1) / 2)) }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            val pairs = moves.chunked(2)
            itemsIndexed(pairs) { idx, pair ->
                val roundNum = idx + 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$roundNum.",
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 红方
                    val redMove = pair[0]
                    val redStep = redMove.step
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onStepClick(redStep) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            redMove.notation,
                            modifier = Modifier
                                .background(
                                    color = if (redStep == currentStep) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = if (redStep == currentStep) FontWeight.Bold else FontWeight.Normal,
                            color = if (redStep == currentStep) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (comments.containsKey(redStep.toString())) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    // 黑方
                    if (pair.size > 1) {
                        val blackMove = pair[1]
                        val blackStep = blackMove.step
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onStepClick(blackStep) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                blackMove.notation,
                                modifier = Modifier
                                    .background(
                                        color = if (blackStep == currentStep) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = if (blackStep == currentStep) FontWeight.Bold else FontWeight.Normal,
                            color = if (blackStep == currentStep) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            )
                            if (comments.containsKey(blackStep.toString())) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // 定位按钮
        val isAtCurrentStep by remember {
            derivedStateOf {
                val targetIndex = maxOf(0, (currentStep - 1) / 2)
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                visibleItems.any { it.index == targetIndex }
            }
        }

        if (!isAtCurrentStep) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(maxOf(0, (currentStep - 1) / 2)) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.MyLocation, "定位到当前步")
            }
        }
    }
}

@Composable
private fun NavigationBar(
    stepIndex: Int,
    totalSteps: Int,
    autoPlaying: Boolean,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onAutoPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 控制按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFirst, enabled = stepIndex > 0) {
                Icon(Icons.Default.SkipPrevious, "首步", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onPrev, enabled = stepIndex > 0) {
                Icon(Icons.Default.NavigateBefore, "上一步", modifier = Modifier.size(32.dp))
            }
            
            // 播放/暂停按钮，稍微大一点，突出显示
            FilledTonalIconButton(
                onClick = onAutoPlay,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (autoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (autoPlaying) "暂停" else "自动播放",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            IconButton(onClick = onNext, enabled = stepIndex < totalSteps) {
                Icon(Icons.Default.NavigateNext, "下一步", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onLast, enabled = stepIndex < totalSteps) {
                Icon(Icons.Default.SkipNext, "末步", modifier = Modifier.size(28.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 步数指示
        Text(
            "第 $stepIndex / $totalSteps 步",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
