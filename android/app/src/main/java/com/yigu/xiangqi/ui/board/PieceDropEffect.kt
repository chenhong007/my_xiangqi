package com.yigu.xiangqi.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 走棋高亮样式 — 标记起始位置和落地位置
 */
enum class MoveHighlightStyle {
    TIANTIAN,   // 天天象棋：起始圆环 + 落地光晕
    RING,       // 圆环：起始和落地都用圆环
    CORNERS,    // 四角标记：L 形对角线
    RECT,       // 矩形（旧版）
    NONE,       // 不显示
}

/**
 * 落子效果配置
 * 支持多种设计风格的落子动画效果
 */
enum class PieceDropEffectType {
    // 天天象棋风格：原始位置小圆心，落地位置光晕效果
    TIANTIAN_XIANGQI,
    
    // 简约风格：淡入淡出
    FADE_IN_OUT,
    
    // 弹跳风格：落地后有弹跳效果
    BOUNCE,
    
    // 脉冲风格：落地后有脉冲波纹
    PULSE,
    
    // 无动画
    NONE
}

/**
 * 落子效果的参数
 * @property type 效果类型
 * @property duration 动画总时长（毫秒）
 * @property originScale 原始位置的棋子大小比例
 * @property dropDuration 落地动画时长占比（0-1）
 */
data class PieceDropEffectConfig(
    val type: PieceDropEffectType = PieceDropEffectType.TIANTIAN_XIANGQI,
    val duration: Int = 400,
    val originScale: Float = 0.3f,
    val dropDuration: Float = 0.7f,
)

/**
 * 落子效果绘制器
 */
object PieceDropEffectRenderer {
    
    /**
     * 绘制落子效果
     * @param col 棋子列
     * @param row 棋子行
     * @param cellSize 格子大小
     * @param progress 动画进度 (0-1)
     * @param config 效果配置
     * @param isRed 是否是红方棋子
     */
    fun DrawScope.drawPieceDropEffect(
        col: Int,
        row: Int,
        name: String,
        cellSize: Float,
        progress: Float,
        config: PieceDropEffectConfig,
        isRed: Boolean,
    ) {
        val cx = col * cellSize
        val cy = row * cellSize
        val radius = cellSize * 0.43f
        
        when (config.type) {
            PieceDropEffectType.TIANTIAN_XIANGQI -> {
                drawTianTianEffect(cx, cy, radius, name, progress, config, isRed)
            }
            PieceDropEffectType.FADE_IN_OUT -> {
                drawFadeEffect(cx, cy, radius, name, progress, config, isRed)
            }
            PieceDropEffectType.BOUNCE -> {
                drawBounceEffect(cx, cy, radius, name, progress, config, isRed)
            }
            PieceDropEffectType.PULSE -> {
                drawPulseEffect(cx, cy, radius, name, progress, config, isRed)
            }
            PieceDropEffectType.NONE -> {
                drawPiece(cx, cy, radius, name, isRed)
            }
        }
    }
    
    /**
     * 天天象棋风格效果：原始位置小圆心，落地位置光晕
     */
    private fun DrawScope.drawTianTianEffect(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        progress: Float,
        config: PieceDropEffectConfig,
        isRed: Boolean,
    ) {
        val dropDuration = config.dropDuration
        
        if (progress < dropDuration) {
            // 落地阶段：从小圆心到完整棋子
            val dropProgress = progress / dropDuration
            
            // 原始小圆心（0%）逐渐消失
            val originAlpha = (1 - dropProgress) * 0.6f
            // 在深色模式下，黑色的圆心可能看不见，所以加一点白色或者使用带边框的圆
            val centerColor = if (isRed) Color(0xFFC41E1E) else Color(0xFF1A1A1A)
            drawCircle(
                color = centerColor.copy(alpha = originAlpha),
                radius = radius * config.originScale,
                center = Offset(cx, cy),
            )
            // 为黑色圆心加一个浅色外发光，确保在深色背景下可见
            if (!isRed) {
                drawCircle(
                    color = Color(0x40FFFFFF).copy(alpha = originAlpha),
                    radius = radius * config.originScale + 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }
            
            // 棋子逐渐出现
            val pieceAlpha = (dropProgress * dropProgress) // 加速效果
            drawPieceWithAlpha(cx, cy, radius, name, pieceAlpha, isRed)
        } else {
            // 落地后阶段：光晕效果
            val glowProgress = (progress - dropDuration) / (1 - dropDuration)
            val maxGlowRadius = radius * 1.3f
            val glowRadius = radius + (maxGlowRadius - radius) * (0.5f + 0.5f * sin(glowProgress * PI.toFloat()))
            val glowAlpha = 0.3f * (1 - glowProgress)
            
            drawCircle(
                color = if (isRed) Color(0xFFC41E1E).copy(alpha = glowAlpha)
                        else Color(0xFF1A1A1A).copy(alpha = glowAlpha),
                radius = glowRadius,
                center = Offset(cx, cy),
            )
            
            // 完整棋子
            drawPiece(cx, cy, radius, name, isRed)
        }
    }
    
    /**
     * 简约淡入淡出效果
     */
    private fun DrawScope.drawFadeEffect(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        progress: Float,
        config: PieceDropEffectConfig,
        isRed: Boolean,
    ) {
        val dropDuration = config.dropDuration
        val alpha = if (progress < dropDuration) {
            progress / dropDuration
        } else {
            1f
        }
        drawPieceWithAlpha(cx, cy, radius, name, alpha, isRed)
    }
    
    /**
     * 弹跳效果
     */
    private fun DrawScope.drawBounceEffect(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        progress: Float,
        config: PieceDropEffectConfig,
        isRed: Boolean,
    ) {
        val dropDuration = config.dropDuration
        
        if (progress < dropDuration) {
            // 下降阶段
            val dropProgress = progress / dropDuration
            val scale = 0.8f + (1f - dropProgress) * 0.2f
            drawPieceWithAlpha(cx, cy, radius * scale, name, dropProgress, isRed)
        } else {
            // 弹跳阶段
            val bounceProgress = (progress - dropDuration) / (1 - dropDuration)
            val bounceHeight = sin(bounceProgress * PI.toFloat()) * 0.15f
            val scalePulse = 1f + bounceHeight * 0.2f
            drawPieceWithAlpha(cx, cy, radius * scalePulse, name, 1f, isRed)
        }
    }
    
    /**
     * 脉冲波纹效果
     */
    private fun DrawScope.drawPulseEffect(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        progress: Float,
        config: PieceDropEffectConfig,
        isRed: Boolean,
    ) {
        val dropDuration = config.dropDuration
        
        if (progress < dropDuration) {
            // 落地阶段
            val dropProgress = progress / dropDuration
            val scale = 0.7f + dropProgress * 0.3f
            drawPieceWithAlpha(cx, cy, radius * scale, name, dropProgress * dropProgress, isRed)
        } else {
            // 脉冲波纹阶段
            val pulseProgress = (progress - dropDuration) / (1 - dropDuration)
            
            // 波纹
            for (i in 0..2) {
                val waveOffset = (pulseProgress + i * 0.15f) % 1f
                val waveRadius = radius + waveOffset * (radius * 0.5f)
                val waveAlpha = (1 - waveOffset) * 0.4f
                
                drawCircle(
                    color = if (isRed) Color(0xFFC41E1E).copy(alpha = waveAlpha)
                            else Color(0xFF1A1A1A).copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = Offset(cx, cy),
                )
            }
            
            // 完整棋子
            drawPiece(cx, cy, radius, name, isRed)
        }
    }
    
    /**
     * 绘制完整的棋子（用于展示）
     */
    private fun DrawScope.drawPiece(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        isRed: Boolean,
    ) {
        // 外圈阴影
        drawCircle(Color(0x30000000), radius + 2f, Offset(cx + 1.5f, cy + 2f))
        
        // 棋子底色
        drawCircle(Color(0xFFF5E6C8), radius, Offset(cx, cy))
        
        // 边框
        val borderColor = if (isRed) Color(0xFFC41E1E) else Color(0xFF1A1A1A)
        drawCircle(
            color = borderColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f),
        )
        drawCircle(
            color = borderColor,
            radius = radius - radius * 0.14f,
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )
        
        // 文字
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = radius * 1.16f
                color = borderColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            drawText(name, cx, cy + paint.textSize * 0.35f, paint)
        }
    }
    
    /**
     * 绘制带透明度的棋子（简化版）
     */
    private fun DrawScope.drawPieceWithAlpha(
        cx: Float,
        cy: Float,
        radius: Float,
        name: String,
        alpha: Float,
        isRed: Boolean,
    ) {
        val pieceColor = Color(0xFFF5E6C8).copy(alpha = alpha)
        drawCircle(pieceColor, radius, Offset(cx, cy))
        
        // 阴影
        drawCircle(Color(0x30000000).copy(alpha = alpha), radius + 2f, Offset(cx + 1.5f, cy + 2f))
        
        // 边框
        val strokeColor = (if (isRed) Color(0xFFC41E1E) else Color(0xFF1A1A1A)).copy(alpha = alpha)
        drawCircle(
            color = strokeColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f),
        )
        
        // 文字
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = radius * 1.16f
                color = strokeColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            drawText(name, cx, cy + paint.textSize * 0.35f, paint)
        }
    }
}
