"""
将原始棋谱 JSON（DhtmlXQ 编码）解码为 App 可直接导入的干净 JSON。

输出格式：中文纵线着法 + 坐标 + 变着分支，详见 plan 文档。
"""
import copy
import json
import os
import re
import logging
from pathlib import Path
from typing import List, Dict, Optional, Tuple

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s',
                    datefmt='%H:%M:%S')
logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).parent.parent / 'data'
APP_DIR = DATA_DIR / 'app'
APP_DIR.mkdir(parents=True, exist_ok=True)

# ─────────────────── 棋子定义 ───────────────────

# DhtmlXQ 的 32 个槽位顺序（棋盘从左到右排列）
SLOT_NAMES = [
    '车', '马', '相', '仕', '帅', '仕', '相', '马', '车',
    '炮', '炮',
    '兵', '兵', '兵', '兵', '兵',
    '車', '馬', '象', '士', '将', '士', '象', '馬', '車',
    '砲', '砲',
    '卒', '卒', '卒', '卒', '卒',
]

# 红方槽位 0-15，黑方槽位 16-31
RED_SLOTS = set(range(16))

# 标准开局 binit（全局谱 binit 为空时使用）
# 每 2 字符为十进制数值 = col + row * 9，32 个槽位共 64 字符
# 红方(row 9,7,6)：车(81,89), 马(82,88), 相(83,87), 仕(84,86), 帅(85), 炮(64,70), 兵(54,56,58,60,62)
# 黑方(row 0,2,3)：車(0,8), 馬(1,7), 象(2,6), 士(3,5), 将(4), 砲(19,25), 卒(27,29,31,33,35)
STANDARD_BINIT = (
    '091929394959697989'
    '1777'
    '0626466686'
    '001020304050607080'
    '1272'
    '0323436383'
)

# ─────────────────── 中文记谱常量 ───────────────────

RED_COL_NAMES = '九八七六五四三二一'    # col 0→8
BLACK_COL_NAMES = '１２３４５６７８９'  # col 0→8
RED_NUM = '零一二三四五六七八九'
BLACK_NUM = '０１２３４５６７８９'

# 多子消歧（同列 2~5 个同类棋子时的位序词）
MULTI_POS_WORDS_2 = ['前', '后']
MULTI_POS_WORDS_3 = ['前', '中', '后']
MULTI_POS_WORDS_4 = ['前', '中前', '中后', '后']
MULTI_POS_WORDS_5 = ['前', '二', '三', '四', '后']

# 直线行走的棋子（步数 = 行差）
STRAIGHT_PIECES = {'车', '車', '炮', '砲', '兵', '卒', '帅', '将'}
# 斜线行走的棋子（目标列号）
DIAGONAL_PIECES = {'马', '馬', '相', '象', '仕', '士'}


# ─────────────────── 棋盘状态类 ───────────────────

class Board:
    """维护 9 列 x 10 行棋盘状态，支持走子和中文记谱生成。"""

    def __init__(self):
        # grid[row][col] = (slot_index, piece_name) or None
        self.grid = [[None] * 9 for _ in range(10)]
        self.pieces: Dict[int, Tuple[int, int]] = {}  # slot -> (col, row)

    def clone(self) -> 'Board':
        b = Board()
        b.grid = [row[:] for row in self.grid]
        b.pieces = dict(self.pieces)
        return b

    def place(self, slot: int, col: int, row: int):
        self.grid[row][col] = (slot, SLOT_NAMES[slot])
        self.pieces[slot] = (col, row)

    def remove(self, col: int, row: int):
        cell = self.grid[row][col]
        if cell:
            slot = cell[0]
            self.pieces.pop(slot, None)
        self.grid[row][col] = None

    def move(self, fc: int, fr: int, tc: int, tr: int):
        """执行走子，返回 (slot, piece_name) 或 None（如果起点为空）。"""
        cell = self.grid[fr][fc]
        if not cell:
            return None
        slot, name = cell
        self.grid[fr][fc] = None
        # 吃子
        target = self.grid[tr][tc]
        if target:
            self.pieces.pop(target[0], None)
        self.grid[tr][tc] = (slot, name)
        self.pieces[slot] = (tc, tr)
        return cell

    def is_red(self, slot: int) -> bool:
        return slot in RED_SLOTS

    def find_same_col_pieces(self, name: str, col: int, is_red: bool) -> List[Tuple[int, int]]:
        """找同列同名棋子，返回 [(col, row), ...] 按行排序（从上到下，行号小→大）。"""
        results = []
        for r in range(10):
            cell = self.grid[r][col]
            if cell and cell[1] == name and self.is_red(cell[0]) == is_red:
                results.append((col, r))
        return results  # 已经按行号升序


# ─────────────────── binit 解码 ───────────────────

def decode_binit(binit: str) -> List[Dict]:
    """解码 binit 为棋子列表，返回 [{'piece': '帅', 'col': 4, 'row': 9}, ...]。"""
    if not binit:
        binit = STANDARD_BINIT
    # 补齐到 64 字符
    binit = binit.ljust(64, '9')

    pieces = []
    for slot in range(32):
        pair = binit[slot * 2:slot * 2 + 2]
        if pair == '99':
            continue
        try:
            c = int(pair[0])
            r = int(pair[1])
            pieces.append({
                'piece': SLOT_NAMES[slot],
                'col': c,
                'row': r,
            })
        except ValueError:
            continue
    return pieces


def init_board(binit: str) -> Board:
    """从 binit 创建初始棋盘。"""
    if not binit:
        binit = STANDARD_BINIT
    binit = binit.ljust(64, '9')

    board = Board()
    for slot in range(32):
        pair = binit[slot * 2:slot * 2 + 2]
        if pair == '99':
            continue
        try:
            c = int(pair[0])
            r = int(pair[1])
            board.place(slot, c, r)
        except ValueError:
            continue
    return board


# ─────────────────── 中文纵线记谱 ───────────────────

def _col_name(col: int, is_red: bool) -> str:
    return RED_COL_NAMES[col] if is_red else BLACK_COL_NAMES[col]


def _num(n: int, is_red: bool) -> str:
    return RED_NUM[n] if is_red else BLACK_NUM[n]


def generate_notation(board: Board, fc: int, fr: int, tc: int, tr: int) -> str:
    """根据当前棋盘状态和走子坐标，生成中文纵线着法字符串。"""
    cell = board.grid[fr][fc]
    if not cell:
        return f"{fc}{fr}{tc}{tr}"

    slot, name = cell
    is_red = board.is_red(slot)

    # 红方「进」= 行号减小（向上），黑方「进」= 行号增大（向下）
    row_diff = tr - fr
    forward = (row_diff < 0) if is_red else (row_diff > 0)

    # ── 确定棋子名称部分（含前/后消歧） ──
    same_col = board.find_same_col_pieces(name, fc, is_red)

    if len(same_col) == 1:
        piece_part = name + _col_name(fc, is_red)
    else:
        # 按「前→后」排列：红方 row 小=前，黑方 row 大=前
        if is_red:
            ordered = sorted(same_col, key=lambda p: p[1])
        else:
            ordered = sorted(same_col, key=lambda p: -p[1])

        idx = next(i for i, p in enumerate(ordered) if p == (fc, fr))

        if len(ordered) == 2:
            words = MULTI_POS_WORDS_2
        elif len(ordered) == 3:
            words = MULTI_POS_WORDS_3
        elif len(ordered) == 4:
            words = MULTI_POS_WORDS_4
        else:
            words = MULTI_POS_WORDS_5

        piece_part = words[idx] + name

    # ── 确定动作和目标 ──
    if fc == tc:
        # 平移（行不变不可能，这里是纵向直走）
        action = '进' if forward else '退'
        target = _num(abs(row_diff), is_red)
    elif fr == tr:
        action = '平'
        target = _col_name(tc, is_red)
    else:
        # 斜线走（马/相/仕）或炮/车斜向（不存在，但防御性处理）
        if name in DIAGONAL_PIECES:
            action = '进' if forward else '退'
            target = _col_name(tc, is_red)
        else:
            action = '进' if forward else '退'
            target = _num(abs(row_diff), is_red)

    return piece_part + action + target


# ─────────────────── 着法序列解码 ───────────────────

def decode_moves(board: Board, moves_raw: str) -> List[Dict]:
    """解码着法编码为着法列表，同时更新棋盘状态。"""
    def get_move_coordinates(moves_raw_str: str) -> List[Tuple[int, int, int, int]]:
        """尝试两种坐标系（Top-Left 和 Bottom-Left），返回正确的坐标列表。"""
        # 尝试 Top-Left
        tl_board = board.clone()
        tl_valid = True
        tl_moves = []
        for i in range(0, len(moves_raw_str) - 3, 4):
            try:
                fc = int(moves_raw_str[i])
                fr = int(moves_raw_str[i + 1])
                tc = int(moves_raw_str[i + 2])
                tr = int(moves_raw_str[i + 3])
            except ValueError:
                tl_valid = False
                break
            if tl_board.grid[fr][fc] is None:
                tl_valid = False
                break
            tl_moves.append((fc, fr, tc, tr))
            tl_board.move(fc, fr, tc, tr)

        # 尝试 Bottom-Left (y = 9 - y)
        bl_board = board.clone()
        bl_valid = True
        bl_moves = []
        for i in range(0, len(moves_raw_str) - 3, 4):
            try:
                fc = int(moves_raw_str[i])
                fr = 9 - int(moves_raw_str[i + 1])
                tc = int(moves_raw_str[i + 2])
                tr = 9 - int(moves_raw_str[i + 3])
            except ValueError:
                bl_valid = False
                break
            if bl_board.grid[fr][fc] is None:
                bl_valid = False
                break
            bl_moves.append((fc, fr, tc, tr))
            bl_board.move(fc, fr, tc, tr)

        if tl_valid and bl_valid:
            # 如果两种解析都合法，检查第一步是否为红方走子
            # 象棋一般都是红方先走，尤其残局
            if tl_moves:
                fc, fr, _, _ = tl_moves[0]
                tl_is_red = board.is_red(board.grid[fr][fc][0]) if board.grid[fr][fc] else False
                
                fc_b, fr_b, _, _ = bl_moves[0]
                bl_is_red = board.is_red(board.grid[fr_b][fc_b][0]) if board.grid[fr_b][fc_b] else False
                
                if tl_is_red and not bl_is_red:
                    return tl_moves
                elif bl_is_red and not tl_is_red:
                    return bl_moves
            return tl_moves
        elif tl_valid:
            return tl_moves
        elif bl_valid:
            return bl_moves
        
        # 如果都不完全合法，回退到按 Top-Left 逐步尝试
        fallback_moves = []
        b = board.clone()
        for i in range(0, len(moves_raw_str) - 3, 4):
            try:
                fc = int(moves_raw_str[i])
                fr = int(moves_raw_str[i + 1])
                tc = int(moves_raw_str[i + 2])
                tr = int(moves_raw_str[i + 3])
            except ValueError:
                break
            if b.grid[fr][fc] is None and b.grid[9 - fr][fc] is not None:
                fr = 9 - fr
                tr = 9 - tr
            fallback_moves.append((fc, fr, tc, tr))
            b.move(fc, fr, tc, tr)
        return fallback_moves

    move_coords = get_move_coordinates(moves_raw)

    result = []
    for fc, fr, tc, tr in move_coords:
        notation = generate_notation(board, fc, fr, tc, tr)
        board.move(fc, fr, tc, tr)
        result.append({
            'step': len(result) + 1,
            'notation': notation,
            'from': [fc, fr],
            'to': [tc, tr],
        })

    return result


# ─────────────────── 变着分支解码 ───────────────────

def decode_variations(board_init: Board, main_moves_raw: str,
                      variations_raw: List[Dict]) -> List[Dict]:
    """
    解码变着分支。

    branch 格式: "parent_step_branch"
    - parent: 父分支编号（0=主线）
    - step: 从父分支第 step 步之后分叉
    - branch: 本分支编号
    """
    if not variations_raw:
        return []

    # 拓扑排序：确保父分支一定在子分支之前处理
    var_by_id = {}
    for v in variations_raw:
        parts = v['branch'].split('_')
        var_by_id[int(parts[2])] = v

    sorted_vars = []
    visited = set()

    def _topo(bid):
        if bid in visited or bid not in var_by_id:
            return
        parent_id = int(var_by_id[bid]['branch'].split('_')[0])
        if parent_id != 0 and parent_id in var_by_id:
            _topo(parent_id)
        visited.add(bid)
        sorted_vars.append(var_by_id[bid])

    for bid in sorted(var_by_id.keys()):
        _topo(bid)

    branch_moves = {0: main_moves_raw}
    branch_boards = {0: board_init}     # branch_id -> Board 在该分支起始时的状态
    branch_start_step = {0: 1}          # branch_id -> 该分支的全局起始步号

    def replay_board(board_start: Board, moves_raw: str, steps: int) -> Board:
        b = board_start.clone()
        
        # 探测分支的坐标系
        is_bl = False
        if len(moves_raw) >= 4:
            try:
                fc = int(moves_raw[0])
                fr = int(moves_raw[1])
                if b.grid[fr][fc] is None and b.grid[9 - fr][fc] is not None:
                    is_bl = True
            except ValueError:
                pass

        for i in range(min(steps, len(moves_raw) // 4)):
            try:
                fc = int(moves_raw[i * 4])
                fr = int(moves_raw[i * 4 + 1])
                tc = int(moves_raw[i * 4 + 2])
                tr = int(moves_raw[i * 4 + 3])
                if is_bl:
                    fr = 9 - fr
                    tr = 9 - tr
            except ValueError:
                continue
            b.move(fc, fr, tc, tr)
        return b

    result = []
    for var in sorted_vars:
        parts = var['branch'].split('_')
        parent_id = int(parts[0])
        abs_step = int(parts[1])      # 全局绝对步号
        branch_id = int(parts[2])

        branch_moves[branch_id] = var['moves_raw']
        branch_start_step[branch_id] = abs_step

        parent_moves = branch_moves.get(parent_id, main_moves_raw)
        parent_board = branch_boards.get(parent_id, board_init)
        parent_start = branch_start_step.get(parent_id, 1)

        # 在父分支中回放的步数 = 全局步号 - 父分支全局起始步号
        replay_count = abs_step - parent_start

        fork_board = replay_board(parent_board, parent_moves, replay_count)
        branch_boards[branch_id] = fork_board.clone()

        moves = decode_moves(fork_board, var['moves_raw'])

        result.append({
            'branch_after': abs_step,
            'parent_branch': parent_id,
            'moves': moves,
        })

    return result


# ─────────────────── 单局解码 ───────────────────

def decode_game(game: Dict) -> Optional[Dict]:
    """将一局原始数据解码为 App 格式。"""
    binit = game.get('board_init_raw', '')
    moves_raw = game.get('moves_raw', '')

    if not moves_raw:
        return None

    init_pieces = decode_binit(binit)
    board = init_board(binit)
    board_for_vars = board.clone()

    moves = decode_moves(board, moves_raw)
    variations = decode_variations(
        board_for_vars, moves_raw,
        game.get('variations', [])
    )

    result = {
        'id': game['id'],
        'title': game.get('title', ''),
        'round': game.get('round', ''),
        'result': game.get('result', ''),
        'red_player': game.get('red_player', ''),
        'black_player': game.get('black_player', ''),
        'init_board': init_pieces,
        'moves': moves,
    }

    if variations:
        result['variations'] = variations

    comments = game.get('comments', {})
    if comments:
        result['comments'] = comments

    return result


# ─────────────────── 批量导出 ───────────────────

def export_all():
    files = sorted(DATA_DIR.glob('*.json'))
    summary = {}

    for fpath in files:
        with open(fpath, encoding='utf-8') as f:
            data = json.load(f)

        name = data.get('manual_name', fpath.stem)
        mtype = data.get('manual_type', '')
        games = data.get('games', [])

        decoded_games = []
        errors = 0
        for g in games:
            try:
                result = decode_game(g)
                if result:
                    decoded_games.append(result)
            except Exception as e:
                errors += 1
                if errors <= 3:
                    logger.warning(f"  解码失败 ID={g.get('id','?')}: {e}")

        out = {
            'manual': name,
            'type': mtype,
            'total': len(decoded_games),
            'games': decoded_games,
        }
        out_path = APP_DIR / f"{name}.json"
        with open(out_path, 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)

        summary[name] = (len(decoded_games), len(games), errors)
        logger.info(f"  {name}: {len(decoded_games)}/{len(games)} 局"
                     + (f" ({errors} 错误)" if errors else ""))

    print(f"\n{'='*50}")
    print("导出汇总:")
    total_ok = 0
    total_all = 0
    for name, (ok, total, errs) in summary.items():
        total_ok += ok
        total_all += total
        status = '完整' if ok == total else f'缺 {total - ok}'
        print(f"  {name}: {ok}/{total} — {status}")
    print(f"\n总计: {total_ok}/{total_all} 局已导出到 {APP_DIR}/")


if __name__ == '__main__':
    export_all()
