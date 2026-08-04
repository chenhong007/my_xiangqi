# My Xiangqi (中国象棋)

这是一个中国象棋相关的项目，包含象棋棋谱数据、爬虫工具以及 Android 应用程序。

## 项目结构

- `android/`: 象棋 Android 应用程序的源代码。
- `data/`: 包含大量象棋棋谱数据的 JSON 文件（共计 2800+ 个文件）。
- `docs/`: 项目相关文档，如产品需求文档 (PRD) 等。
- `scraper/`: 用于抓取和处理象棋棋谱数据的 Python 爬虫脚本和工具。
- `logs/`: 爬虫及其他脚本的运行日志。
- `*.py`: 根目录下的各类 Python 辅助脚本，用于数据校验、格式检查等（如 `check_completeness.py`, `brace_check.py` 等）。
- `gupu.md`: 棋谱相关的说明文档。

## 数据说明

`data` 目录下包含了丰富的象棋古谱和现代棋谱数据，格式为 JSON，可用于象棋软件的导入、分析或机器学习训练。

## 爬虫工具

`scraper` 目录下包含了一系列 Python 脚本，用于从网络上抓取棋谱数据、验证数据完整性以及格式化输出。使用前请参考该目录下的 `README.md` 和 `requirements.txt` 安装依赖。

## Android 应用

`android` 目录下是一个完整的 Android 象棋应用工程，支持棋谱的展示、打谱等功能。详细说明请参考 `android/README.md`。

## 许可证

[请在此处添加您的许可证信息]
