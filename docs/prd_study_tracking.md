# 弈古 - 学习追踪数据库方案 PRD

## 1. 背景与目标

**问题**：当前 `user_progress` 表使用 `REPLACE` 策略保存进度，每次写入会覆盖整条记录。
用户无法知道某局棋谱是否看过、打谱过几遍。

**目标**：
- 在棋局列表中直观标记每局的学习状态（未学/学过 N 遍）
- 在棋谱页面显示当前第几次学习、历史完成次数
- 保留每次学习的详细会话记录，供统计分析

## 2. 现有数据库结构

```
┌─────────────────────┐     ┌─────────────────────┐
│     manuals         │     │      games           │
├─────────────────────┤     ├─────────────────────┤
│ id       STRING  PK │◄────│ manualId  STRING  FK │
│ name     STRING     │     │ id        STRING  PK │
│ type     STRING     │     │ title     STRING     │
│ totalGames INT      │     │ ...棋谱数据字段       │
└─────────────────────┘     └──────────┬──────────┘
                                       │
            ┌──────────────────────────┤
            │                          │
┌───────────▼─────────┐   ┌───────────▼─────────┐   ┌─────────────────────┐
│   user_progress     │   │    favorites         │   │    user_notes       │
├─────────────────────┤   ├─────────────────────┤   ├─────────────────────┤
│ gameId   STRING  PK │   │ id      INT  PK     │   │ id      INT  PK    │
│ status   STRING     │   │ gameId  STRING  FK  │   │ gameId  STRING  FK │
│ currentStep  INT    │   │ folder  STRING?     │   │ stepIndex INT     │
│ currentBranch STR?  │   │ createdAt LONG      │   │ content STRING    │
│ guessCorrect INT    │   └─────────────────────┘   └─────────────────────┘
│ guessTotal   INT    │
│ lastStudiedAt LONG  │
│ completedAt   LONG? │
└─────────────────────┘
```

**现有问题**：
- `user_progress` 每次 `REPLACE` 丢失历史数据
- 没有"打开/查看次数"、"完成次数"的计数
- 没有首次学习时间记录
- 无法追溯每次学习的详细过程

## 3. 数据库变更设计

### 3.1 修改 `user_progress` 表（增加 3 个字段）

```sql
ALTER TABLE user_progress ADD COLUMN viewCount       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_progress ADD COLUMN completionCount  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_progress ADD COLUMN firstStudiedAt   INTEGER;  -- nullable, 毫秒时间戳
```

| 字段 | 类型 | 说明 |
|------|------|------|
| viewCount | INT | 打开此棋谱的次数，每次进入棋谱页 +1 |
| completionCount | INT | 走完全谱的次数，每次到达末步 +1 |
| firstStudiedAt | LONG? | 首次学习时间，一旦写入不再更新 |

**写入策略变更**：不再使用 `REPLACE`，改为 `INSERT OR IGNORE` + `UPDATE`，
避免覆盖计数字段。

### 3.2 新增 `study_sessions` 表（学习会话流水）

```sql
CREATE TABLE study_sessions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    gameId          TEXT    NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    startedAt       INTEGER NOT NULL,  -- 进入棋谱页的时间
    endedAt         INTEGER,           -- 退出棋谱页的时间
    reachedStep     INTEGER NOT NULL DEFAULT 0,  -- 本次到达的最远步数
    totalSteps      INTEGER NOT NULL DEFAULT 0,  -- 棋谱总步数
    completed       INTEGER NOT NULL DEFAULT 0,  -- 是否走完全谱 (0/1)
    mode            TEXT    NOT NULL DEFAULT 'REVIEW',  -- REVIEW / GUESS
    guessCorrect    INTEGER NOT NULL DEFAULT 0,
    guessTotal      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_sessions_gameId ON study_sessions(gameId);
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | LONG | 自增主键 |
| gameId | STRING | 关联棋局 |
| startedAt | LONG | 本次进入时间 |
| endedAt | LONG? | 本次退出时间 |
| reachedStep | INT | 本次学到的最远步数 |
| totalSteps | INT | 棋谱总步数 |
| completed | BOOL | 是否走完全谱 |
| mode | STRING | 学习模式：REVIEW(打谱) / GUESS(猜招) |
| guessCorrect | INT | 本次猜招正确数 |
| guessTotal | INT | 本次猜招总数 |

### 3.3 变更后完整 ER 图

```
┌─────────────────────┐     ┌─────────────────────┐
│     manuals         │     │      games           │
├─────────────────────┤     ├─────────────────────┤
│ id       STRING  PK │◄────│ manualId  STRING  FK │
│ name     STRING     │     │ id        STRING  PK │
│ type     STRING     │     │ ...棋谱数据字段       │
│ totalGames INT      │     └──────────┬──────────┘
└─────────────────────┘                │
                            ┌──────────┼──────────────────┐
                            │          │                   │
               ┌────────────▼──┐  ┌────▼──────────────┐  ┌▼───────────────────┐
               │ user_progress │  │ study_sessions    │  │ favorites / notes  │
               ├───────────────┤  ├───────────────────┤  │ (不变)             │
               │ gameId     PK │  │ id          PK    │  └────────────────────┘
               │ status        │  │ gameId      FK    │
               │ currentStep   │  │ startedAt         │
               │ guessCorrect  │  │ endedAt           │
               │ guessTotal    │  │ reachedStep       │
               │ lastStudiedAt │  │ totalSteps        │
               │ completedAt   │  │ completed         │
               │ viewCount  ★ │  │ mode              │
               │ completion ★ │  │ guessCorrect      │
               │ firstStudy ★ │  │ guessTotal        │
               └───────────────┘  └───────────────────┘
```

## 4. 数据写入流程

```
用户进入棋谱页
    ├── INSERT OR IGNORE user_progress (确保记录存在)
    ├── UPDATE viewCount = viewCount + 1
    ├── UPDATE firstStudiedAt = COALESCE(firstStudiedAt, now)
    └── INSERT study_sessions (startedAt = now)

用户每步前进/后退
    └── UPDATE user_progress SET currentStep, status, lastStudiedAt

用户走完全谱（首次到达末步）
    ├── UPDATE completionCount = completionCount + 1
    └── UPDATE study_sessions SET completed = 1

用户退出棋谱页
    └── UPDATE study_sessions SET endedAt, reachedStep, guessCorrect, guessTotal
```

**关键约束**：
- 同一次打开只计一次 viewCount（进入时 +1）
- 同一会话内反复到达末步只计一次 completionCount（ViewModel 内标记防重）
- firstStudiedAt 只在第一次设置，后续不覆盖

## 5. 查询设计

### 5.1 棋局列表（带学习状态）

```sql
SELECT g.id, g.title, g.result, g.moveCount, g.hasVariations, g.hasComments,
       COALESCE(p.viewCount, 0)       AS viewCount,
       COALESCE(p.completionCount, 0) AS completionCount,
       p.status
FROM games g
LEFT JOIN user_progress p ON g.id = p.gameId
WHERE g.manualId = :manualId
ORDER BY g.id
```

### 5.2 某古谱的学习统计

```sql
-- 已学过的棋局数（viewCount > 0）
SELECT COUNT(*) FROM user_progress p
INNER JOIN games g ON p.gameId = g.id
WHERE g.manualId = :manualId AND p.viewCount > 0

-- 已完成的棋局数（completionCount > 0）
SELECT COUNT(*) FROM user_progress p
INNER JOIN games g ON p.gameId = g.id
WHERE g.manualId = :manualId AND p.completionCount > 0
```

### 5.3 某棋局的学习历史

```sql
SELECT * FROM study_sessions
WHERE gameId = :gameId
ORDER BY startedAt DESC
```

## 6. UI 展示

### 6.1 棋局列表每行

```
┌──────────────────────────────────────────┐
│ 順手炮橫車對直車       ✓ 已完成 2 遍     │
│ 和棋 · 42步 🌿 💬                       │
├──────────────────────────────────────────┤
│ 列手炮直橫車對橫車      ● 学习中          │
│ 红胜 · 38步                              │
├──────────────────────────────────────────┤
│ 顺手炮横车弃马          (无标记，灰色)     │
│ 红胜 · 36步 🌿                           │
└──────────────────────────────────────────┘
```

状态标记规则：
| 条件 | 显示 | 颜色 |
|------|------|------|
| completionCount > 0 | "已完成 N 遍" | 绿色 |
| status = IN_PROGRESS | "学习中" | 橙色 |
| viewCount > 0 且未完成 | "看过" | 灰色 |
| 无记录 | 不显示标记 | - |

### 6.2 棋谱页信息栏

在棋盘上方、标题下方显示：
```
第 3 次学习 · 已完成 2 遍
```

## 7. 版本与迁移

- Room DB version: 2 → 3
- 迁移策略: `fallbackToDestructiveMigration()`（现阶段用户数据可重置）
- 棋谱数据从 assets 重新导入，用户进度清零（可接受）
