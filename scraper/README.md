# 象棋古谱下载工具

从东萍象棋网 (www.dpxq.com) 批量下载古谱数据，结构化为 JSON 格式。

## 目录结构

```
xiangqi/
├── scraper/
│   ├── scraper.py      # 主入口
│   ├── discover.py     # 目录发现模块（找到所有棋谱 ID）
│   ├── downloader.py   # HTTP 下载（限速+缓存+重试）
│   ├── parser.py       # DhtmlXQ 格式解析器
│   ├── validate.py     # 数据验证工具
│   └── requirements.txt
└── data/
    ├── 橘中秘.json
    ├── 自出洞来无敌手.json
    ├── 梅花谱.json
    ├── 反梅花谱.json
    ├── 适情雅趣.json
    └── raw/            # 原始 HTML 缓存（断点续传）
```

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

```bash
# 下载所有古谱（4 本重点 + 其他）
python scraper.py

# 只下载指定古谱
python scraper.py --manual 橘中秘

# 使用 ID 顺序扫描策略（目录页面无法访问时）
python scraper.py --manual 橘中秘 --scan

# 指定扫描范围（已知 ID 区间）
python scraper.py --manual 橘中秘 --scan --scan-start 43000 --scan-end 44500

# 查看支持的古谱列表
python scraper.py --list

# 验证已下载数据
python validate.py
python validate.py --manual 橘中秘
```

## 数据格式

### JSON 结构

```json
{
  "manual_name": "橘中秘",
  "manual_type": "古谱残局",
  "source": "http://www.dpxq.com",
  "total_discovered": 120,
  "total_parsed": 118,
  "downloaded_at": "2026-07-16",
  "games": [
    {
      "id": 43635,
      "url": "http://www.dpxq.com/hldcg/search/view_u_43635.html",
      "title": "第001局 乌龙摆尾",
      "event": "橘中秘",
      "class": "象棋谱大全-古谱残局",
      "round": "卷一",
      "date": "0000-00-00",
      "result": "红胜",
      "red_player": "",
      "black_player": "",
      "board_init_raw": "...",  // 原始 binit 编码（64字符）
      "board_init": [...],      // 解码后的棋子列表
      "moves_raw": "...",       // 原始着法编码
      "moves": [...],           // 解码后的着法列表
      "comments": {
        "0": "红先胜，着法精妙"
      },
      "hits": 50000,
      "add_date": "2007-07-26 01:49:01"
    }
  ]
}
```

### board_init 棋子格式

```json
{
  "piece": "红帅",
  "col": 4,       // 列 0-8（左到右：九路）
  "row": 0,       // 行 0-9（黑方底线=0，红方底线=9）
  "slot": 4,      // 内部槽序号
  "raw": "40"     // 原始编码对
}
```

### moves 着法格式

```json
{
  "step": 1,
  "from_col": 3,
  "from_row": 7,
  "to_col": 3,
  "to_row": 6,
  "raw": "6360"   // 原始编码（4字符）
}
```

## 网站结构说明

东萍象棋网数据组织：

```
www.dpxq.com
├── /hldcg/share/              # 棋谱分类目录
│   └── chess_象棋谱大全/
│       ├── 古谱残局/
│       │   ├── 橘中秘/        # 列表页（含分页）
│       │   ├── 适情雅趣/
│       │   └── ...
│       └── 古谱全局/
│           ├── 自出洞来无敌手/
│           └── ...
└── /hldcg/search/
    └── view_u_XXXXX.html      # 单局棋谱（DhtmlXQ 格式）
```

## 爬虫策略

程序按优先级依次尝试：

1. **目录页解析** - 从 `/hldcg/share/chess_象棋谱大全/古谱残局/橘中秘/` 获取 ID 列表
2. **搜索 API** - 通过 `/hldcg/search/?s=list.asp?owner=u&event=橘中秘` 分页获取
3. **ID 顺序扫描** - 在已知 ID 范围内逐个检验（最慢但最可靠）

## 注意事项

- 请求间隔 1~2.5 秒，避免对服务器造成压力
- 已下载页面缓存在 `data/raw/`，断线后可继续
- 部分注解需要 VIP 权限，免费用户可能看到截断内容
- 梅花谱、反梅花谱如找不到，可能以其他名称存在（如"梅花变"）
