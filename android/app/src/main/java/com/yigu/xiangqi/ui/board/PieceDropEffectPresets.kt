package com.yigu.xiangqi.ui.board

/**
 * 落子效果配置预设
 * 提供常用的效果配置组合
 */
object PieceDropEffectPresets {
    
    /**
     * 天天象棋风格 - 推荐
     * 特点：原始位置小圆心，落地位置光晕效果
     * 时长：400ms
     * 推荐场景：标准游戏体验
     */
    val TIANTIAN_XIANGQI = PieceDropEffectConfig(
        type = PieceDropEffectType.TIANTIAN_XIANGQI,
        duration = 400,
        originScale = 0.3f,
        dropDuration = 0.7f,
    )
    
    /**
     * 天天象棋风格 - 快速版
     * 特点：动画更快，节奏更紧凑
     * 时长：250ms
     */
    val TIANTIAN_XIANGQI_FAST = PieceDropEffectConfig(
        type = PieceDropEffectType.TIANTIAN_XIANGQI,
        duration = 250,
        originScale = 0.35f,
        dropDuration = 0.65f,
    )
    
    /**
     * 天天象棋风格 - 舒缓版
     * 特点：动画更缓，视觉效果更柔和
     * 时长：600ms
     */
    val TIANTIAN_XIANGQI_SLOW = PieceDropEffectConfig(
        type = PieceDropEffectType.TIANTIAN_XIANGQI,
        duration = 600,
        originScale = 0.25f,
        dropDuration = 0.75f,
    )
    
    /**
     * 简约风格 - 标准
     * 特点：平缓的淡入淡出
     * 时长：300ms
     */
    val FADE_IN_OUT = PieceDropEffectConfig(
        type = PieceDropEffectType.FADE_IN_OUT,
        duration = 300,
        originScale = 0.2f,
        dropDuration = 0.7f,
    )
    
    /**
     * 简约风格 - 快速
     * 特点：最小化动画干扰
     * 时长：150ms
     */
    val FADE_IN_OUT_FAST = PieceDropEffectConfig(
        type = PieceDropEffectType.FADE_IN_OUT,
        duration = 150,
        originScale = 0.2f,
        dropDuration = 0.7f,
    )
    
    /**
     * 弹跳风格 - 标准
     * 特点：物理感的弹跳效果
     * 时长：500ms
     */
    val BOUNCE = PieceDropEffectConfig(
        type = PieceDropEffectType.BOUNCE,
        duration = 500,
        originScale = 0.3f,
        dropDuration = 0.6f,
    )
    
    /**
     * 弹跳风格 - 夸张版
     * 特点：更明显的弹跳感
     * 时长：600ms
     */
    val BOUNCE_EXAGGERATED = PieceDropEffectConfig(
        type = PieceDropEffectType.BOUNCE,
        duration = 600,
        originScale = 0.4f,
        dropDuration = 0.55f,
    )
    
    /**
     * 脉冲风格 - 标准
     * 特点：科技感的波纹脉冲
     * 时长：500ms
     */
    val PULSE = PieceDropEffectConfig(
        type = PieceDropEffectType.PULSE,
        duration = 500,
        originScale = 0.3f,
        dropDuration = 0.6f,
    )
    
    /**
     * 脉冲风格 - 强劲版
     * 特点：更强的波纹感
     * 时长：700ms
     */
    val PULSE_STRONG = PieceDropEffectConfig(
        type = PieceDropEffectType.PULSE,
        duration = 700,
        originScale = 0.25f,
        dropDuration = 0.65f,
    )
    
    /**
     * 无动画
     * 特点：直接显示棋子
     * 时长：0ms
     */
    val NONE = PieceDropEffectConfig(
        type = PieceDropEffectType.NONE,
        duration = 0,
        originScale = 0f,
        dropDuration = 0f,
    )
    
    /**
     * 获取所有预设列表
     */
    fun getAllPresets() = listOf(
        "天天象棋（标准）" to TIANTIAN_XIANGQI,
        "天天象棋（快速）" to TIANTIAN_XIANGQI_FAST,
        "天天象棋（舒缓）" to TIANTIAN_XIANGQI_SLOW,
        "简约（标准）" to FADE_IN_OUT,
        "简约（快速）" to FADE_IN_OUT_FAST,
        "弹跳（标准）" to BOUNCE,
        "弹跳（夸张）" to BOUNCE_EXAGGERATED,
        "脉冲（标准）" to PULSE,
        "脉冲（强劲）" to PULSE_STRONG,
        "无动画" to NONE,
    )
}

/**
 * 建议的效果组合（按场景）
 */
object PieceDropEffectScenarios {
    
    /**
     * 游戏场景 - 标准象棋应用
     */
    val STANDARD_GAME = PieceDropEffectPresets.TIANTIAN_XIANGQI
    
    /**
     * 快速游戏 - 快速的棋局
     */
    val RAPID_GAME = PieceDropEffectPresets.FADE_IN_OUT_FAST
    
    /**
     * 学习场景 - 仔细研究棋局
     */
    val STUDY_MODE = PieceDropEffectPresets.TIANTIAN_XIANGQI_SLOW
    
    /**
     * 低配设备 - 最小化性能开销
     */
    val LOW_END_DEVICE = PieceDropEffectPresets.NONE
    
    /**
     * 娱乐场景 - 强调视觉效果
     */
    val ENTERTAINMENT = PieceDropEffectPresets.PULSE_STRONG
}

/**
 * 效果难度级别（从简单到复杂）
 */
enum class PieceDropEffectDifficulty {
    /**
     * 最简单 - 无动画，完全聚焦棋局
     */
    NONE,
    
    /**
     * 简单 - 简约风格，最小化视觉干扰
     */
    SIMPLE,
    
    /**
     * 中等 - 天天象棋风格，平衡美观性和清晰度
     */
    MEDIUM,
    
    /**
     * 复杂 - 弹跳或脉冲，更强的视觉反馈
     */
    COMPLEX;
    
    /**
     * 获取对应的效果配置
     */
    fun getConfig(): PieceDropEffectConfig = when (this) {
        NONE -> PieceDropEffectPresets.NONE
        SIMPLE -> PieceDropEffectPresets.FADE_IN_OUT
        MEDIUM -> PieceDropEffectPresets.TIANTIAN_XIANGQI
        COMPLEX -> PieceDropEffectPresets.PULSE_STRONG
    }
}
