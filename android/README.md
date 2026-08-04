# 弈古 — 象棋古谱学习 App

将 17 部经典象棋古谱、2357 局棋局装进口袋。逐步打谱、分支研究、互动猜招，系统化学习中国象棋。

## 构建

1. 用 Android Studio (Ladybug 或更高) 打开 `android/` 目录
2. 等待 Gradle 同步完成
3. 连接设备或启动模拟器，点击 Run

> 首次构建时 Android Studio 会自动下载 Gradle Wrapper。如需手动初始化：
> ```
> cd android
> gradle wrapper --gradle-version 8.7
> ```

## 技术栈

- Kotlin 2.0 + Jetpack Compose
- MVVM + Clean Architecture
- Hilt 依赖注入
- Room 本地数据库
- Navigation Compose
- Material Design 3

## 项目结构

```
app/src/main/
├── assets/manuals/      ← 17 个古谱 JSON 数据文件
├── java/com/yigu/xiangqi/
│   ├── data/            ← 数据层：Room 实体/DAO/Repository/导入器
│   ├── di/              ← Hilt 依赖注入模块
│   ├── domain/          ← 领域层：棋盘模型/着法引擎
│   └── ui/              ← 表现层：Compose 页面/ViewModel
│       ├── board/       ← 棋盘打谱页（核心）
│       ├── home/        ← 首页 + 书架
│       ├── gamelist/    ← 古谱目录
│       ├── study/       ← 学习计划
│       ├── favorite/    ← 收藏/记录/笔记
│       ├── profile/     ← 个人中心/设置
│       ├── theme/       ← 主题配色
│       └── navigation/  ← 路由定义
└── res/
```
