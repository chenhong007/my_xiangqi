package com.yigu.xiangqi.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yigu.xiangqi.domain.model.PiecePosition
import com.yigu.xiangqi.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 棋盘 9 列 x 10 行
private const val COLS = 9
private const val ROWS = 10

// 红方棋子名
private val RED_PIECES = setOf("帅", "仕", "相", "马", "车", "炮", "兵")

/**
 * 棋盘 Canvas 组件，负责绘制棋盘线、棋子和高亮标记。
 *
 * @param pieces 当前棋盘上所有棋子
 * @param lastMoveFrom 上一步的起点（用于高亮）
 * @param lastMoveTo 上一步的终点（用于高亮）
 * @param flipped 是否翻转（黑方在下）
 * @param onCellTap 点击格点回调（col, row）
 * @param pieceDropEffectConfig 落子效果配置
 * @param highlightStyle 走棋高亮样式
 */
@Composable
fun BoardCanvas(
    pieces: List<PiecePosition>,
    lastMoveFrom: Pair<Int, Int>? = null,
    lastMoveTo: Pair<Int, Int>? = null,
    flipped: Boolean = false,
    selectedCell: Pair<Int, Int>? = null,
    validTargets: Set<Pair<Int, Int>> = emptySet(),
    hintMove: com.yigu.xiangqi.domain.model.Move? = null,
    onCellTap: ((Int, Int) -> Unit)? = null,
    pieceDropEffectConfig: PieceDropEffectConfig = PieceDropEffectConfig(),
    highlightStyle: MoveHighlightStyle = MoveHighlightStyle.TIANTIAN,
    modifier: Modifier = Modifier,
) {
    // 棋盘宽高比 = 9:10（格线间距相同）
    val aspectRatio = 9f / 10f
    
    // 追踪动画中的棋子
    val dropAnimations = remember {
        mutableMapOf<Pair<Int, Int>, Animatable<Float, *>>()
    }
    
    LaunchedEffect(lastMoveTo) {
        lastMoveTo?.let { (col, row) ->
            val key = col to row
            val animatable = dropAnimations.getOrPut(key) { Animatable(0f) }
            
            // 每次有新的落子到该位置时，重置动画状态
            animatable.snapTo(0f)
            
            try {
                animatable.animateTo(
                    1f,
                    animationSpec = tween(pieceDropEffectConfig.duration)
                )
            } finally {
                // 即使动画被取消（例如快速连续走棋导致 lastMoveTo 改变），
                // 也要确保棋子最终完全显示，避免棋子停留在不可见或半透明状态
                animatable.snapTo(1f)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .pointerInput(flipped, onCellTap) {
                if (onCellTap == null) return@pointerInput
                detectTapGestures { offset ->
                    val cellSize = size.width / (COLS + 1).toFloat()
                    val padding = cellSize / 2f
                    val col = ((offset.x - padding) / cellSize).roundToInt().coerceIn(0, 8)
                    val row = ((offset.y - padding) / cellSize).roundToInt().coerceIn(0, 9)
                    val (c, r) = if (flipped) 8 - col to 9 - row else col to row
                    onCellTap(c, r)
                }
            },
    ) {
        val cellSize = size.width / (COLS + 1)
        val padding = cellSize / 2

        translate(left = padding, top = padding) {
            drawBoardLines(cellSize)
            drawRiverText(cellSize)

            // 高亮
            if (highlightStyle != MoveHighlightStyle.NONE) {
                lastMoveFrom?.let { (c, r) ->
                    val (dc, dr) = if (flipped) 8 - c to 9 - r else c to r
                    drawFromMark(dc, dr, cellSize, highlightStyle)
                }
                lastMoveTo?.let { (c, r) ->
                    val (dc, dr) = if (flipped) 8 - c to 9 - r else c to r
                    drawToGlow(dc, dr, cellSize, highlightStyle)
                }
            }
            selectedCell?.let { (c, r) ->
                val (dc, dr) = if (flipped) 8 - c to 9 - r else c to r
                drawSelectedMark(dc, dr, cellSize)
            }
            for ((c, r) in validTargets) {
                val (dc, dr) = if (flipped) 8 - c to 9 - r else c to r
                drawValidDot(dc, dr, cellSize)
            }

            // 提示虚线和虚影
            hintMove?.let { move ->
                val (fc, fr) = if (flipped) 8 - move.from[0] to 9 - move.from[1] else move.from[0] to move.from[1]
                val (tc, tr) = if (flipped) 8 - move.to[0] to 9 - move.to[1] else move.to[0] to move.to[1]
                
                // 画虚线箭头
                drawHintArrow(fc, fr, tc, tr, cellSize)

                // 找到原位置的棋子
                val originalPiece = pieces.find { it.col == move.from[0] && it.row == move.from[1] }
                if (originalPiece != null) {
                    drawPieceWithAlpha(tc, tr, originalPiece.piece, cellSize, 0.4f)
                }
            }

            // 棋子
            for (p in pieces) {
                val (dc, dr) = if (flipped) 8 - p.col to 9 - p.row else p.col to p.row
                val dropKey = p.col to p.row
                val progress = dropAnimations[dropKey]?.value ?: 1f
                
                // 检查是否正在执行动画
                if (progress < 1f && pieceDropEffectConfig.type != PieceDropEffectType.NONE) {
                    // 绘制带动画的棋子
                    drawPieceWithAnimation(
                        dc, dr, p.piece, cellSize, progress, pieceDropEffectConfig
                    )
                } else {
                    // 正常绘制棋子
                    drawPiece(dc, dr, p.piece, cellSize)
                }
            }
        }
    }
}

private fun DrawScope.drawBoardLines(cell: Float) {
    val lineColor = Color(0xFF4A3728)
    val stroke = Stroke(width = 1.5f)
    val thickStroke = Stroke(width = 2.5f)

    // 外框
    drawRect(
        color = lineColor,
        topLeft = Offset.Zero,
        size = Size(cell * 8, cell * 9),
        style = thickStroke,
    )

    // 横线
    for (r in 1 until 9) {
        drawLine(lineColor, Offset(0f, r * cell), Offset(8 * cell, r * cell), strokeWidth = 1.5f)
    }
    // 竖线（上半部 + 下半部，中间河界断开）
    for (c in 1..7) {
        drawLine(lineColor, Offset(c * cell, 0f), Offset(c * cell, 4 * cell), strokeWidth = 1.5f)
        drawLine(lineColor, Offset(c * cell, 5 * cell), Offset(c * cell, 9 * cell), strokeWidth = 1.5f)
    }

    // 九宫斜线
    drawLine(lineColor, Offset(3 * cell, 0f), Offset(5 * cell, 2 * cell), strokeWidth = 1.5f)
    drawLine(lineColor, Offset(5 * cell, 0f), Offset(3 * cell, 2 * cell), strokeWidth = 1.5f)
    drawLine(lineColor, Offset(3 * cell, 7 * cell), Offset(5 * cell, 9 * cell), strokeWidth = 1.5f)
    drawLine(lineColor, Offset(5 * cell, 7 * cell), Offset(3 * cell, 9 * cell), strokeWidth = 1.5f)

    // 星位标记（炮/兵位）
    val starPositions = listOf(
        1 to 2, 7 to 2, // 黑炮
        0 to 3, 2 to 3, 4 to 3, 6 to 3, 8 to 3, // 黑卒
        1 to 7, 7 to 7, // 红炮
        0 to 6, 2 to 6, 4 to 6, 6 to 6, 8 to 6, // 红兵
    )
    for ((c, r) in starPositions) {
        drawStarMark(c, r, cell, lineColor)
    }
}

private fun DrawScope.drawStarMark(col: Int, row: Int, cell: Float, color: Color) {
    val cx = col * cell
    val cy = row * cell
    val len = cell * 0.12f
    val gap = cell * 0.06f

    val dirs = mutableListOf<Pair<Float, Float>>()
    if (col > 0) { dirs.add(-1f to -1f); dirs.add(-1f to 1f) }
    if (col < 8) { dirs.add(1f to -1f); dirs.add(1f to 1f) }

    for ((dx, dy) in dirs) {
        val sx = cx + dx * gap
        val sy = cy + dy * gap
        drawLine(color, Offset(sx, sy + dy * len), Offset(sx, sy), strokeWidth = 1f)
        drawLine(color, Offset(sx, sy), Offset(sx + dx * len, sy), strokeWidth = 1f)
    }
}

private fun DrawScope.drawRiverText(cell: Float) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = cell * 0.45f
            color = 0xFF8B6914.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val y = 4.5f * cell + paint.textSize * 0.35f
        drawText("楚  河", 2f * cell, y, paint)
        drawText("汉  界", 6f * cell, y, paint)
    }
}

/**
 * 起始位置标记 — 棋子离开的格子
 */
private fun DrawScope.drawFromMark(col: Int, row: Int, cell: Float, style: MoveHighlightStyle) {
    val cx = col * cell
    val cy = row * cell
    val radius = cell * 0.43f

    when (style) {
        MoveHighlightStyle.TIANTIAN -> {
            // 天天象棋：半透明填充圆 + 外圈金色环
            drawCircle(
                color = FromRingInnerColor,
                radius = radius * 0.6f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = FromRingColor,
                radius = radius * 0.75f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f),
            )
        }
        MoveHighlightStyle.RING -> {
            drawCircle(
                color = FromRingColor,
                radius = radius * 0.8f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )
        }
        MoveHighlightStyle.CORNERS -> {
            drawCornerBrackets(cx, cy, cell, FromRingColor)
        }
        MoveHighlightStyle.RECT -> {
            drawRect(
                color = HighlightYellow,
                topLeft = Offset(cx - cell / 2, cy - cell / 2),
                size = Size(cell, cell),
            )
        }
        MoveHighlightStyle.NONE -> {}
    }
}

/**
 * 落地位置标记 — 棋子到达的格子（光晕 / 眩晕效果）
 */
private fun DrawScope.drawToGlow(col: Int, row: Int, cell: Float, style: MoveHighlightStyle) {
    val cx = col * cell
    val cy = row * cell
    val radius = cell * 0.43f

    when (style) {
        MoveHighlightStyle.TIANTIAN -> {
            // 天天象棋风格：多层渐变光晕
            drawCircle(
                color = ToGlowRed.copy(alpha = 0.12f),
                radius = radius * 1.6f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = ToGlowRed.copy(alpha = 0.20f),
                radius = radius * 1.3f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = ToRingColor,
                radius = radius + 3f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f),
            )
        }
        MoveHighlightStyle.RING -> {
            drawCircle(
                color = ToRingColor,
                radius = radius + 3f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f),
            )
        }
        MoveHighlightStyle.CORNERS -> {
            drawCornerBrackets(cx, cy, cell, ToRingColor)
        }
        MoveHighlightStyle.RECT -> {
            drawRect(
                color = HighlightBlue,
                topLeft = Offset(cx - cell / 2, cy - cell / 2),
                size = Size(cell, cell),
            )
        }
        MoveHighlightStyle.NONE -> {}
    }
}

/**
 * 选中格标记（猜招模式）
 */
private fun DrawScope.drawSelectedMark(col: Int, row: Int, cell: Float) {
    val cx = col * cell
    val cy = row * cell
    val radius = cell * 0.43f
    drawCircle(
        color = Color(0x500D47A1),
        radius = radius * 1.1f,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = Color(0x800D47A1),
        radius = radius + 2f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f),
    )
}

/**
 * 四角 L 形标记（对焦框风格）
 */
private fun DrawScope.drawCornerBrackets(cx: Float, cy: Float, cell: Float, color: Color) {
    val half = cell * 0.46f
    val arm = cell * 0.18f
    val sw = 2.5f

    // 四个角的 L 形线段
    val corners = listOf(
        Pair(-1f, -1f), Pair(1f, -1f),
        Pair(-1f, 1f), Pair(1f, 1f),
    )
    for ((dx, dy) in corners) {
        val sx = cx + dx * half
        val sy = cy + dy * half
        drawLine(color, Offset(sx, sy), Offset(sx + dx * (-arm), sy), strokeWidth = sw)
        drawLine(color, Offset(sx, sy), Offset(sx, sy + dy * (-arm)), strokeWidth = sw)
    }
}

private fun DrawScope.drawValidDot(col: Int, row: Int, cell: Float) {
    drawCircle(
        color = Color(0x6000C853),
        radius = cell * 0.12f,
        center = Offset(col * cell, row * cell),
    )
}

private fun DrawScope.drawPieceWithAnimation(
    col: Int,
    row: Int,
    name: String,
    cell: Float,
    progress: Float,
    config: PieceDropEffectConfig,
) {
    val cx = col * cell
    val cy = row * cell
    val radius = cell * 0.43f
    val isRed = name in RED_PIECES
    
    with(PieceDropEffectRenderer) {
        drawPieceDropEffect(col, row, name, cell, progress, config, isRed)
    }
}

private fun DrawScope.drawPiece(col: Int, row: Int, name: String, cell: Float) {
    drawPieceWithAlpha(col, row, name, cell, 1f)
}

private fun DrawScope.drawPieceWithAlpha(col: Int, row: Int, name: String, cell: Float, alpha: Float) {
    val cx = col * cell
    val cy = row * cell
    val radius = cell * 0.43f
    val isRed = name in RED_PIECES

    // 外圈阴影
    drawCircle(Color(0x30000000).copy(alpha = alpha * 0.3f), radius + 2f, Offset(cx + 1.5f, cy + 2f))

    // 棋子底色
    drawCircle(Color(0xFFF5E6C8).copy(alpha = alpha), radius, Offset(cx, cy))

    // 边框
    val strokeColor = (if (isRed) PieceRed else PieceBlack).copy(alpha = alpha)
    drawCircle(
        color = strokeColor,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2f),
    )
    drawCircle(
        color = strokeColor,
        radius = radius - cell * 0.06f,
        center = Offset(cx, cy),
        style = Stroke(width = 1f),
    )

    // 文字
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = cell * 0.5f
            color = if (isRed) 0xFFC41E1E.toInt() else 0xFF1A1A1A.toInt()
            this.alpha = (alpha * 255).toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        drawText(name, cx, cy + paint.textSize * 0.35f, paint)
    }
}

private fun DrawScope.drawHintArrow(fc: Int, fr: Int, tc: Int, tr: Int, cell: Float) {
    val startX = fc * cell
    val startY = fr * cell
    val endX = tc * cell
    val endY = tr * cell
    
    val dx = endX - startX
    val dy = endY - startY
    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    
    // 如果距离太近，不画箭头
    if (distance < cell * 0.5f) return
    
    // 留出棋子的空间
    val padding = cell * 0.5f
    val ratio = padding / distance
    
    val pStartX = startX + dx * ratio
    val pStartY = startY + dy * ratio
    val pEndX = endX - dx * ratio
    val pEndY = endY - dy * ratio
    
    val path = Path().apply {
        moveTo(pStartX, pStartY)
        lineTo(pEndX, pEndY)
    }
    
    drawPath(
        path = path,
        color = Color(0xFF00C853),
        style = Stroke(
            width = 6f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
        )
    )
    
    // 画箭头
    val arrowSize = 15f
    val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
    val arrowP1X = pEndX - arrowSize * kotlin.math.cos(angle - Math.PI / 6).toFloat()
    val arrowP1Y = pEndY - arrowSize * kotlin.math.sin(angle - Math.PI / 6).toFloat()
    val arrowP2X = pEndX - arrowSize * kotlin.math.cos(angle + Math.PI / 6).toFloat()
    val arrowP2Y = pEndY - arrowSize * kotlin.math.sin(angle + Math.PI / 6).toFloat()
    
    val arrowPath = Path().apply {
        moveTo(pEndX, pEndY)
        lineTo(arrowP1X, arrowP1Y)
        lineTo(arrowP2X, arrowP2Y)
        close()
    }
    
    drawPath(
        path = arrowPath,
        color = Color(0xFF00C853)
    )
}
