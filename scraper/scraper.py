"""
象棋古谱下载主程序

功能：
1. 发现各古谱的所有棋谱 ID
2. 批量下载各局棋谱页面
3. 解析 DhtmlXQ 格式
4. 输出结构化 JSON 文件

用法：
    python scraper.py                          # 下载所有目标古谱
    python scraper.py --manual 橘中秘          # 只下载指定古谱
    python scraper.py --manual 橘中秘 --scan   # 强制使用 ID 扫描策略
    python scraper.py --list                   # 列出所有支持的古谱
"""

import json
import logging
import argparse
import sys
from datetime import date
from pathlib import Path
from typing import List, Dict, Optional

from discover import MANUALS, KNOWN_ID_RANGES, discover_manual
from downloader import fetch_game_page
from parser import parse_game

logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).parent.parent / 'data'
BASE_URL = 'http://www.dpxq.com'


def download_and_parse_games(
    game_ids: List[int],
    manual_name: str,
    manual_type: str,
) -> List[Dict]:
    """
    批量下载并解析棋谱列表。

    返回解析成功的游戏列表。
    """
    games = []
    total = len(game_ids)

    for idx, gid in enumerate(game_ids, 1):
        url = f"{BASE_URL}/hldcg/search/view_u_{gid}.html"
        logger.info(f"[{idx}/{total}] 下载 ID={gid}")

        html = fetch_game_page(gid)
        if not html:
            logger.warning(f"  下载失败，跳过 ID={gid}")
            continue

        game = parse_game(html, gid, url)
        if not game:
            logger.warning(f"  解析失败，跳过 ID={gid}")
            continue

        # 验证 event 是否匹配（ID 扫描可能带来误差）
        game_event = game.get('event', '')
        game_class = game.get('class', '')
        if game_event and manual_name not in game_event:
            # 检查别名
            manual_conf = next(
                (m for m in MANUALS if m['name'] == manual_name), None
            )
            alt_names = manual_conf.get('alt_names', []) if manual_conf else []
            if not any(alt in game_event for alt in alt_names):
                logger.debug(
                    f"  ID={gid} event='{game_event}' 不匹配 '{manual_name}'，跳过"
                )
                continue

        games.append(game)
        logger.info(
            f"  ✓ {game.get('title', '?')} | {game.get('result', '?')} "
            f"| 注解{len(game.get('comments', {}))}条"
        )

    return games


def save_manual_json(
    manual_name: str,
    manual_type: str,
    games: List[Dict],
    game_ids: List[int],
) -> Path:
    """将古谱数据保存为 JSON 文件。"""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    output_path = DATA_DIR / f"{manual_name}.json"

    output = {
        'manual_name': manual_name,
        'manual_type': manual_type,
        'source': BASE_URL,
        'total_discovered': len(game_ids),
        'total_parsed': len(games),
        'downloaded_at': str(date.today()),
        'games': games,
    }

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    logger.info(f"已保存: {output_path} ({len(games)} 局)")
    return output_path


def process_manual(
    manual: Dict,
    force_scan: bool = False,
    scan_range: Optional[tuple] = None,
) -> Optional[Path]:
    """
    处理单个古谱：发现 → 下载 → 解析 → 保存。
    """
    name = manual['name']
    mtype = manual.get('type', '')

    logger.info(f"\n{'='*60}")
    logger.info(f"处理古谱: 《{name}》 [{mtype}]")

    # 如果强制扫描，直接使用 ID 扫描策略
    if force_scan:
        from discover import discover_via_id_scan
        r = scan_range or KNOWN_ID_RANGES.get(name)
        if not r:
            logger.error(f"《{name}》无已知 ID 范围，无法扫描")
            return None
        game_ids = discover_via_id_scan(manual, r[0], r[1])
    else:
        r = scan_range or KNOWN_ID_RANGES.get(name)
        game_ids = discover_manual(manual, scan_range=r)

    if not game_ids:
        logger.warning(f"《{name}》未发现任何棋谱 ID")
        return None

    logger.info(f"发现 {len(game_ids)} 个棋谱 ID")

    # 下载并解析
    games = download_and_parse_games(game_ids, name, mtype)

    if not games:
        logger.warning(f"《{name}》解析结果为空")
        return None

    # 按局号排序
    games.sort(key=lambda g: (g.get('round', ''), g.get('title', '')))

    # 保存 JSON
    return save_manual_json(name, mtype, games, game_ids)


def print_summary(results: Dict[str, Optional[Path]]) -> None:
    """打印下载汇总报告。"""
    print(f"\n{'='*60}")
    print("下载汇总报告")
    print('='*60)
    for name, path in results.items():
        if path and path.exists():
            # 读取并显示统计
            with open(path, encoding='utf-8') as f:
                data = json.load(f)
            total = data.get('total_parsed', 0)
            print(f"  ✓ 《{name}》: {total} 局 → {path.name}")
        else:
            print(f"  ✗ 《{name}》: 失败")
    print('='*60)


def main():
    parser = argparse.ArgumentParser(
        description='东萍象棋网古谱下载工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        '--manual', '-m',
        help='指定古谱名称（如: 橘中秘）',
    )
    parser.add_argument(
        '--scan', '-s',
        action='store_true',
        help='强制使用 ID 顺序扫描策略',
    )
    parser.add_argument(
        '--list', '-l',
        action='store_true',
        help='列出所有支持的古谱',
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='显示详细日志',
    )
    parser.add_argument(
        '--scan-start',
        type=int,
        help='ID 扫描起始值（配合 --scan 使用）',
    )
    parser.add_argument(
        '--scan-end',
        type=int,
        help='ID 扫描结束值（配合 --scan 使用）',
    )

    args = parser.parse_args()

    # 配置日志
    log_level = logging.DEBUG if args.verbose else logging.INFO
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s %(levelname)s %(message)s',
        datefmt='%H:%M:%S',
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(
                DATA_DIR.parent / 'scraper' / 'scraper.log',
                encoding='utf-8',
            ),
        ],
    )

    if args.list:
        print("支持的古谱列表：")
        for m in MANUALS:
            r = KNOWN_ID_RANGES.get(m['name'], ('?', '?'))
            print(f"  {m['name']:<15} [{m['type']}]  ID范围: {r[0]}-{r[1]}")
        return

    # 确定要处理的古谱
    if args.manual:
        targets = [m for m in MANUALS if m['name'] == args.manual]
        if not targets:
            print(f"错误: 未找到古谱 '{args.manual}'")
            print("可用古谱:", [m['name'] for m in MANUALS])
            sys.exit(1)
    else:
        # 默认处理用户指定的 4 本 + 其他主要古谱
        priority = ['橘中秘', '自出洞来无敌手', '梅花谱', '反梅花谱',
                    '适情雅趣', '渊深海阔', '心武残编', '梦入神机']
        targets = [m for m in MANUALS if m['name'] in priority]

    # 处理扫描范围
    scan_range = None
    if args.scan_start and args.scan_end:
        scan_range = (args.scan_start, args.scan_end)

    # 执行下载
    results = {}
    for manual in targets:
        try:
            path = process_manual(
                manual,
                force_scan=args.scan,
                scan_range=scan_range,
            )
            results[manual['name']] = path
        except KeyboardInterrupt:
            logger.info("用户中断")
            break
        except Exception as e:
            logger.error(f"处理《{manual['name']}》时出错: {e}", exc_info=True)
            results[manual['name']] = None

    print_summary(results)


if __name__ == '__main__':
    main()
