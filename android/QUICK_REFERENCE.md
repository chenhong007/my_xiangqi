# 落子效果系统 - 快速参考

## 📱 如何使用

### 用户操作
1. 打开棋盘
2. 点击顶部工具栏的**设置图标** ⚙️
3. 选择喜欢的效果风格
4. 关闭对话框
5. **完成！** 效果已自动保存

### 开发者集成
```kotlin
// 1. 在 BoardCanvas 中传递配置
BoardCanvas(
    pieces = state.pieces,
    pieceDropEffectConfig = state.pieceDropEffectConfig,
)

// 2. ViewModel 自动处理其余部分
// 效果会自动从 DataStore 恢复
```

---

## 🎨 效果对比

| 效果 | 时长 | 特点 | 推荐度 |
|------|------|------|--------|
| **天天象棋** | 400ms | 小圆心 → 光晕脉冲 | ⭐⭐⭐⭐⭐ |
| **简约** | 400ms | 平缓淡入淡出 | ⭐⭐⭐⭐ |
| **弹跳** | 400ms | 物理感弹跳 | ⭐⭐⭐ |
| **脉冲** | 400ms | 科技波纹 | ⭐⭐⭐ |
| **无动画** | 0ms | 直接显示 | ⭐⭐ |

---

## 🔧 核心参数

```kotlin
data class PieceDropEffectConfig(
    val type: PieceDropEffectType,      // 效果类型
    val duration: Int = 400,             // 总时长 (ms)
    val originScale: Float = 0.3f,       // 起始圆心大小 (0-1)
    val dropDuration: Float = 0.7f,      // 落地阶段占比 (0-1)
)
```

### 动画时间线
```
0ms          280ms        400ms
|------------|------------|
   落地阶段      后效应阶段
   (70%)         (30%)
```

---

## 📂 文件清单

### 新增核心文件
- `ui/board/PieceDropEffect.kt` - 5种效果实现
- `ui/settings/PieceDropEffectSettings.kt` - 设置UI
- `domain/preferences/UIPreferencesManager.kt` - 存储管理

### 修改文件
- `ui/board/BoardCanvas.kt` - 动画集成
- `ui/board/BoardScreen.kt` - UI按钮集成
- `ui/board/BoardViewModel.kt` - 状态管理
- `di/AppModule.kt` - 依赖注入

### 文档
- `PIECE_DROP_EFFECT_GUIDE.md` - 完整指南
- `PIECE_DROP_EFFECT_SUMMARY.md` - 改进总结

---

## 🎯 关键特性

✓ **参考天天象棋** - 业界最佳实践
✓ **多风格选择** - 5种不同设计
✓ **用户友好** - 简单的选择界面
✓ **自动保存** - DataStore持久化
✓ **实时预览** - 立即查看效果
✓ **零性能开销** - 可禁用动画
✓ **易于扩展** - 模块化架构

---

## 💡 常见问题

**Q: 效果太快或太慢？**
A: 修改 `duration` 参数，范围 200-800ms

**Q: 想要自定义效果？**
A: 在 `PieceDropEffectRenderer` 中添加新方法

**Q: 低端设备性能差？**
A: 选择"无动画"模式或"简约"模式

**Q: 如何禁用某个效果？**
A: 在设置中选择"无动画"

---

## 🚀 实现细节

### 天天象棋效果详解

```
阶段 1 (0% - 70%)：落地动画
├─ 小圆心：RED 100% → 0%（透明度）
├─ 棋子：0% → 100%（透明度，加速）
└─ 视觉：从点到完整棋子的展开

阶段 2 (70% - 100%)：光晕脉冲
├─ 光晕：半径 1.0× → 1.3× → 1.0×（脉冲）
├─ 光晕：30% → 0%（透明度）
└─ 视觉：柔和的光晕衰减效果
```

### 动画公式
```kotlin
// 光晕半径
radius + (0.3 * radius) * sin(progress * π)

// 光晕透明度
0.3 * (1 - progress)

// 棋子透明度
progress * progress  // 加速曲线
```

---

## 🎓 学习资源

- 完整 API 文档 → `PIECE_DROP_EFFECT_GUIDE.md`
- 实现参考 → `PieceDropEffect.kt`
- UI 参考 → `PieceDropEffectSettings.kt`

---

## 📝 版本信息

| 项目 | 版本 |
|------|------|
| 系统名 | 落子效果系统 v1.0 |
| 默认效果 | 天天象棋风格 |
| 支持效果 | 5种 |
| 最小 API | 28 |
| 存储方案 | DataStore |

---

**✨ 系统已就绪，enjoy your moves! ✨**
