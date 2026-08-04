"""
数据验证工具

验证已下载的古谱 JSON 文件：
1. 检查各古谱局数完整性
2. 验证 binit 棋盘编码格式
3. 检查必填字段完整性
4. 输出统计报告
"""

import json
import logging
import sys
from pathlib import Path
from typing import Dict, List, Any, Tuple
from collections import Counter

DATA_DIR = Path(__file__).parent.parent / 'data'

# 各古谱预期局数（历史记录，供完整性参考）
EXPECTED_COUNTS = {
    '橘中秘':          120,   # 残局部分约 100+，全局部分另有
    '自出洞来无敌手':   32,    # 全局约 30+
    '梅花谱':           48,    # 全局约 40+
    '反梅花谱':          40,   # 全局约 30+
    '适情雅趣':         204,   # 残局 200+ 局
    '渊深海阔':         480,   # 残局 400+ 局
    '心武残编':          72,   # 残局约 70 局
    '梦入神机':         120,   # 残局约 100+ 局
    '竹香斋':           120,   # 残局约 100+ 局
    '韬略元机':          60,   # 全局约 50+ 局
}

# 必填字段
REQUIRED_FIELDS = ['id', 'title', 'board_init_raw', 'result']
OPTIONAL_FIELDS = ['round', 'moves_raw', 'comments', 'hits']


def validate_binit(binit: str) -> Tuple[bool, str]:
    """
    验证 binit 格式正确性。

    规则：
    - 长度应为 64 字符（32 × 2 位编码）
    - 仅含数字字符（0-9）
    - 非 '99' 的值应在合理范围内（棋盘 0-89 格内）
    """
    if not binit:
        return False, "空字符串"

    if not binit.isdigit():
        return False, f"含非数字字符: {binit[:20]}"

    if len(binit) != 64:
        return False, f"长度 {len(binit)} ≠ 64"

    # 检查位置值是否在 0-89 范围内
    invalid_pos = []
    for i in range(0, 64, 2):
        pair = binit[i:i+2]
        val = int(pair)
        if val != 99 and val > 89:
            invalid_pos.append(f"槽{i//2}={pair}")

    if invalid_pos:
        return False, f"位置超范围: {invalid_pos[:5]}"

    return True, "OK"


def validate_movelist(movelist: str) -> Tuple[bool, str]:
    """验证 movelist 格式。"""
    if not movelist:
        return True, "空（残局正常）"

    if not movelist.isdigit():
        return False, f"含非数字字符"

    if len(movelist) % 4 != 0:
        return False, f"长度 {len(movelist)} 不是4的倍数"

    moves = len(movelist) // 4
    return True, f"{moves} 步"


def check_game(game: Dict, idx: int) -> List[str]:
    """检查单局棋谱，返回问题列表。"""
    issues = []

    # 检查必填字段
    for field in REQUIRED_FIELDS:
        if not game.get(field):
            issues.append(f"  缺少字段: {field}")

    # 验证 binit
    binit = game.get('board_init_raw', '')
    ok, msg = validate_binit(binit)
    if not ok:
        issues.append(f"  binit 格式错误: {msg}")

    # 验证 movelist
    moves_raw = game.get('moves_raw', '')
    ok, msg = validate_movelist(moves_raw)
    if not ok:
        issues.append(f"  movelist 格式错误: {msg}")

    return issues


def validate_json_file(path: Path) -> Dict[str, Any]:
    """验证单个 JSON 文件，返回验证报告。"""
    report = {
        'file': path.name,
        'valid': True,
        'total_games': 0,
        'games_with_issues': 0,
        'games_with_moves': 0,
        'games_with_comments': 0,
        'volume_distribution': {},
        'result_distribution': {},
        'issues': [],
        'warnings': [],
    }

    if not path.exists():
        report['valid'] = False
        report['issues'].append(f"文件不存在: {path}")
        return report

    try:
        with open(path, encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        report['valid'] = False
        report['issues'].append(f"JSON 解析失败: {e}")
        return report

    manual_name = data.get('manual_name', path.stem)
    games = data.get('games', [])
    report['total_games'] = len(games)

    # 期望局数检查
    expected = EXPECTED_COUNTS.get(manual_name, 0)
    if expected and len(games) < expected * 0.5:
        report['warnings'].append(
            f"局数偏少: {len(games)} 局，预期 ≥ {expected//2} 局"
        )
    elif expected and len(games) >= expected:
        report['warnings'].append(
            f"局数充足: {len(games)} 局（预期 ~{expected}）"
        )

    # 统计各字段分布
    volumes = Counter()
    results = Counter()
    game_issues_count = 0

    for i, game in enumerate(games):
        issues = check_game(game, i)
        if issues:
            game_issues_count += 1
            if game_issues_count <= 5:  # 只记录前5个有问题的
                report['issues'].extend(
                    [f"局{i+1}({game.get('title','?')}): {iss}" for iss in issues]
                )

        if game.get('moves_raw'):
            report['games_with_moves'] += 1

        if game.get('comments'):
            report['games_with_comments'] += 1

        volumes[game.get('round', '未知')] += 1
        results[game.get('result', '未知')] += 1

    report['games_with_issues'] = game_issues_count
    report['volume_distribution'] = dict(volumes.most_common(10))
    report['result_distribution'] = dict(results.most_common())

    if game_issues_count > len(games) * 0.1:
        report['valid'] = False

    return report


def print_report(report: Dict, verbose: bool = False) -> None:
    """打印验证报告。"""
    status = "✓" if report['valid'] else "✗"
    print(f"\n{status} {report['file']}")
    print(f"  总局数: {report['total_games']}")
    print(f"  含着法: {report['games_with_moves']}")
    print(f"  含注解: {report['games_with_comments']}")
    print(f"  有问题: {report['games_with_issues']}")

    if report['volume_distribution']:
        vols = list(report['volume_distribution'].items())[:5]
        print(f"  卷/章节: {vols}")

    if report['result_distribution']:
        print(f"  结果分布: {dict(report['result_distribution'])}")

    if report['warnings']:
        for w in report['warnings']:
            print(f"  ⚠ {w}")

    if report['issues']:
        print(f"  问题 ({len(report['issues'])} 条):")
        for iss in report['issues'][:10]:
            print(f"    {iss}")
        if len(report['issues']) > 10:
            print(f"    ...共 {len(report['issues'])} 条")


def validate_board_decoding(game: Dict) -> None:
    """验证棋盘解码结果合理性。"""
    pieces = game.get('board_init', [])
    if not pieces:
        return

    red_count = sum(1 for p in pieces if p['piece'].startswith('红'))
    black_count = sum(1 for p in pieces if p['piece'].startswith('黑'))

    # 合理性：红黑各不超过16子
    assert red_count <= 16, f"红方棋子数异常: {red_count}"
    assert black_count <= 16, f"黑方棋子数异常: {black_count}"

    # 检查坐标范围
    for p in pieces:
        assert 0 <= p['col'] <= 8, f"col={p['col']} 超范围"
        assert 0 <= p['row'] <= 9, f"row={p['row']} 超范围"


def main():
    import argparse

    parser = argparse.ArgumentParser(description='验证古谱 JSON 数据')
    parser.add_argument('--manual', '-m', help='指定古谱名称')
    parser.add_argument('--verbose', '-v', action='store_true', help='详细输出')
    parser.add_argument('--check-decode', '-d', action='store_true',
                        help='验证棋盘解码结果')
    args = parser.parse_args()

    json_files = list(DATA_DIR.glob('*.json'))
    if not json_files:
        print(f"数据目录 {DATA_DIR} 中无 JSON 文件")
        sys.exit(1)

    if args.manual:
        json_files = [f for f in json_files if args.manual in f.stem]

    all_valid = True
    total_games = 0

    print(f"\n{'='*60}")
    print(f"验证目录: {DATA_DIR}")
    print(f"文件数量: {len(json_files)}")

    for path in sorted(json_files):
        report = validate_json_file(path)
        print_report(report, args.verbose)

        if not report['valid']:
            all_valid = False
        total_games += report['total_games']

        # 可选：验证解码结果
        if args.check_decode and path.exists():
            with open(path, encoding='utf-8') as f:
                data = json.load(f)
            decode_errors = 0
            for game in data.get('games', [])[:20]:  # 只检查前20局
                try:
                    validate_board_decoding(game)
                except AssertionError as e:
                    decode_errors += 1
                    if args.verbose:
                        print(f"    解码验证失败: {game.get('title')} - {e}")
            if decode_errors:
                print(f"    棋盘解码问题: {decode_errors}/20 局")

    print(f"\n{'='*60}")
    print(f"汇总: {len(json_files)} 个文件，共 {total_games} 局")
    print(f"验证结果: {'全部通过 ✓' if all_valid else '存在问题 ✗'}")

    sys.exit(0 if all_valid else 1)


if __name__ == '__main__':
    main()
