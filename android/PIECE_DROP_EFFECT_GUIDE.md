# 落子效果系统

## 功能概述

本次改进为象棋应用添加了可配置的落子效果系统，支持多种设计风格的落子动画效果。用户可以根据个人喜好选择不同的效果风格。

## 支持的效果类型

### 1. 天天象棋风格 (TIANTIAN_XIANGQI) - 推荐
- **特点**：参考天天象棋的设计
  - 原始位置：小圆心（表示落子的起点）
  - 落地阶段：棋子逐渐从小圆心展开成完整的棋子
  - 落地后：产生光晕脉冲效果（逐渐衰减）
- **时长**：400ms（默认）
- **效果**：柔和、自然，视觉反馈清晰

### 2. 简约风格 (FADE_IN_OUT)
- **特点**：简洁的淡入淡出效果
  - 棋子从透明逐渐到完全不透明
  - 动画柔和，减少视觉干扰
- **适用**：喜欢简洁设计的用户

### 3. 弹跳风格 (BOUNCE)
- **特点**：具有物理感的弹跳效果
  - 棋子落地时有压缩感
  - 落地后产生弹跳动作
- **适用**：希望获得更强视觉反馈的用户

### 4. 脉冲风格 (PULSE)
- **特点**：科技感的波纹脉冲效果
  - 棋子落地时伴随波纹扩散
  - 多层次脉冲波纹
- **适用**：喜欢现代设计风格的用户

### 5. 无动画 (NONE)
- **特点**：直接显示棋子，不带任何动画
- **适用**：想要最小化动画的用户

## 系统架构

### 核心文件结构

```
ui/board/
├── PieceDropEffect.kt          # 效果定义和渲染器
├── BoardCanvas.kt              # 棋盘画布（集成动画系统）
└── BoardScreen.kt              # 棋盘屏幕（UI集成）

ui/settings/
└── PieceDropEffectSettings.kt  # 效果设置对话框

domain/preferences/
└── UIPreferencesManager.kt     # 用户偏好存储

di/
└── AppModule.kt                # 依赖注入配置
```

### 关键类

#### PieceDropEffectConfig
```kotlin
data class PieceDropEffectConfig(
    val type: PieceDropEffectType = PieceDropEffectType.TIANTIAN_XIANGQI,
    val duration: Int = 400,              // 动画时长（毫秒）
    val originScale: Float = 0.3f,        // 原始圆心大小比例
    val dropDuration: Float = 0.7f,       // 落地阶段占比
)
```

#### PieceDropEffectRenderer
负责所有效果的绘制，使用扩展函数：
```kotlin
DrawScope.drawPieceDropEffect(
    col, row, cellSize, progress, config, isRed
)
```

## 使用方式

### 1. 在棋盘中启用动画
```kotlin
BoardCanvas(
    pieces = state.pieces,
    pieceDropEffectConfig = state.pieceDropEffectConfig,
    // 其他参数...
)
```

### 2. 用户选择效果
点击棋盘顶部的**设置图标** → 选择喜欢的效果风格

### 3. 效果会自动保存
选中的效果会通过 DataStore 持久化，下次打开应用时自动应用

## 技术实现细节

### 动画流程

1. **初始化**：当检测到新的 `lastMoveTo` 时，为该位置创建 `Animatable<Float>` 动画
2. **执行**：使用 `tween` 动画在指定时长内从 0 到 1
3. **渲染**：在每一帧中根据进度值调用相应的效果渲染函数
4. **清理**：动画完成后，棋子正常显示

### 进度值使用

- **0.0 - 0.7**（默认）：落地阶段
  - 原始小圆心逐渐消失
  - 棋子逐渐出现
  
- **0.7 - 1.0**（默认）：落地后阶段
  - 光晕/波纹效果
  - 各种后效应

## 定制配置

### 修改动画时长
编辑 `PieceDropEffectConfig`：
```kotlin
PieceDropEffectConfig(
    type = PieceDropEffectType.TIANTIAN_XIANGQI,
    duration = 600,  // 改为600ms
)
```

### 修改效果参数
在 `PieceDropEffectRenderer` 中调整各效果的参数：
- `originScale`：原始圆心大小
- `dropDuration`：落地阶段占比
- 光晕/波纹的半径和透明度

## 未来扩展

1. **自定义效果组合**：允许用户创建自定义效果组合
2. **音效同步**：与落子音效同步，增强反馈
3. **性能优化**：针对低端设备的简化版本
4. **主题集成**：与应用主题颜色系统集成

## 注意事项

- 效果动画只在新的落子时触发（`lastMoveTo` 改变时）
- 选择"无动画"时，动画系统完全绕过，无性能开销
- 所有坐标和大小已经相对于棋盘进行了适当缩放
- 效果的透明度处理确保了与棋盘的良好融合
