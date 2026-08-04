"""
象棋走法合法性验证（全量版）

验证所有古谱 JSON 文件：
1. 初始棋子位置是否合法（帅/将在九宫、仕/士在宫格对角点、相/象在己方半场固定点等）
2. 每步走法是否符合中国象棋规则

坐标系：列 col 0-8（红方视角从左到右），行 row 0-9（0=黑方底线，9=红方底线）
binit 格式：64字符，32个(col,row)对，'99'=不在棋盘。槽位顺序见 SLOT_NAMES。
moves_raw 格式：每 4 位 = src_col, src_row, dst_col, dst_row（各一位数字）。
"""

import json
import sys
from pathlib import Path
from copy import deepcopy
from typing import Dict, List, Tuple, Optional

DATA_DIR = Path(__file__).parent.parent / 'data'

# ─────────────── 棋子槽位定义 ───────────────

SLOT_NAMES = [
    'R_Rook1', 'R_Knight1', 'R_Bishop1', 'R_Advisor1', 'R_King',
    'R_Advisor2', 'R_Bishop2', 'R_Knight2', 'R_Rook2',
    'R_Cannon1', 'R_Cannon2',
    'R_Pawn1', 'R_Pawn2', 'R_Pawn3', 'R_Pawn4', 'R_Pawn5',
    'B_Rook1', 'B_Knight1', 'B_Bishop1', 'B_Advisor1', 'B_King',
    'B_Advisor2', 'B_Bishop2', 'B_Knight2', 'B_Rook2',
    'B_Cannon1', 'B_Cannon2',
    'B_Pawn1', 'B_Pawn2', 'B_Pawn3', 'B_Pawn4', 'B_Pawn5',
]

PIECE_TYPE = {
    0: 'R', 1: 'N', 2: 'B', 3: 'A', 4: 'K',
    5: 'A', 6: 'B', 7: 'N', 8: 'R',
    9: 'C', 10: 'C',
    11: 'P', 12: 'P', 13: 'P', 14: 'P', 15: 'P',
    16: 'R', 17: 'N', 18: 'B', 19: 'A', 20: 'K',
    21: 'A', 22: 'B', 23: 'N', 24: 'R',
    25: 'C', 26: 'C',
    27: 'P', 28: 'P', 29: 'P', 30: 'P', 31: 'P',
}

def slot_side(slot: int) -> str:
    return 'r' if slot < 16 else 'b'

def slot_piece_type(slot: int) -> str:
    return PIECE_TYPE[slot]

# ─────────────── 标准开局 binit ───────────────

STANDARD_BINIT = (
    '09192939495969798917770626466686'
    '00102030405060708012720323436383'
)

# ─────────────── 初始位置合法性检查 ───────────────

# 仕/士合法位置
RED_ADVISOR_POS = {(3,9), (5,9), (4,8), (3,7), (5,7)}
BLACK_ADVISOR_POS = {(3,0), (5,0), (4,1), (3,2), (5,2)}

# 相/象合法位置
RED_BISHOP_POS = {(2,9), (6,9), (0,7), (4,7), (8,7), (2,5), (6,5)}
BLACK_BISHOP_POS = {(2,0), (6,0), (0,2), (4,2), (8,2), (2,4), (6,4)}


def validate_initial_position(board: Dict[Tuple[int,int], Tuple[str,str]]) -> List[str]:
    """检查初始布局中各棋子是否在合法位置。返回问题列表。"""
    issues = []

    red_king_count = 0
    black_king_count = 0

    for (col, row), (side, ptype) in board.items():
        if not (0 <= col <= 8 and 0 <= row <= 9):
            issues.append(f"({col},{row}) out of board")
            continue

        if ptype == 'K':
            if side == 'r':
                red_king_count += 1
                if not (3 <= col <= 5 and 7 <= row <= 9):
                    issues.append(f"Red King at ({col},{row}) not in palace")
            else:
                black_king_count += 1
                if not (3 <= col <= 5 and 0 <= row <= 2):
                    issues.append(f"Black King at ({col},{row}) not in palace")

        elif ptype == 'A':
            if side == 'r':
                if (col, row) not in RED_ADVISOR_POS:
                    issues.append(f"Red Advisor at ({col},{row}) not on valid point")
            else:
                if (col, row) not in BLACK_ADVISOR_POS:
                    issues.append(f"Black Advisor at ({col},{row}) not on valid point")

        elif ptype == 'B':
            if side == 'r':
                if (col, row) not in RED_BISHOP_POS:
                    issues.append(f"Red Bishop at ({col},{row}) not on valid point")
            else:
                if (col, row) not in BLACK_BISHOP_POS:
                    issues.append(f"Black Bishop at ({col},{row}) not on valid point")

    if red_king_count != 1:
        issues.append(f"Red has {red_king_count} King(s)")
    if black_king_count != 1:
        issues.append(f"Black has {black_king_count} King(s)")

    # 检查是否有两子占同一位置（不应该）
    positions = list(board.keys())
    if len(positions) != len(set(positions)):
        issues.append("Duplicate positions detected")

    return issues


# ─────────────── binit 解码 ───────────────

def decode_binit_to_board(binit: str) -> Dict[Tuple[int,int], Tuple[str,str]]:
    """
    解码 binit 为棋盘字典。
    返回 {(col, row): (side, piece_type)}
    """
    if not binit:
        binit = STANDARD_BINIT
    binit = binit.ljust(64, '9')

    board = {}
    for slot in range(32):
        c = int(binit[slot * 2])
        r = int(binit[slot * 2 + 1])
        if c == 9 and r == 9:
            continue
        side = slot_side(slot)
        ptype = slot_piece_type(slot)
        pos = (c, r)
        if pos in board:
            pass  # 重复位置，validate_initial_position 会捕获
        board[pos] = (side, ptype)
    return board


# ─────────────── 走法验证逻辑 ───────────────

def in_board(col, row):
    return 0 <= col <= 8 and 0 <= row <= 9


def in_palace(col, row, side):
    if not (3 <= col <= 5):
        return False
    return (7 <= row <= 9) if side == 'r' else (0 <= row <= 2)


def in_own_half(row, side):
    return (5 <= row <= 9) if side == 'r' else (0 <= row <= 4)


def count_between(board, c1, r1, c2, r2):
    """计算两点间棋子数（不含端点，必须同线）"""
    count = 0
    if c1 == c2:
        for r in range(min(r1, r2) + 1, max(r1, r2)):
            if (c1, r) in board:
                count += 1
    elif r1 == r2:
        for c in range(min(c1, c2) + 1, max(c1, c2)):
            if (c, r1) in board:
                count += 1
    return count




# ─────────────── 走法解析与整局验证 ───────────────

def parse_moves(moves_raw: str) -> List[Tuple[int,int,int,int]]:
    moves = []
    for i in range(0, len(moves_raw) - 3, 4):
        sc, sr, dc, dr = int(moves_raw[i]), int(moves_raw[i+1]), int(moves_raw[i+2]), int(moves_raw[i+3])
        moves.append((sc, sr, dc, dr))
    return moves


def detect_first_mover(board, moves) -> str:
    """根据第一步棋的起点棋子颜色判断先手方。"""
    if not moves:
        return 'r'
    sc, sr, _, _ = moves[0]
    piece = board.get((sc, sr))
    if piece:
        return piece[0]  # 'r' or 'b'
    return 'r'


def replay_moves(board, moves, start_move_num=1, first_mover='r'):
    """
    在棋盘上重放走法，返回错误列表。同时修改 board。
    first_mover: 'r' 红先, 'b' 黑先
    """
    errors = []
    for i, (sc, sr, dc, dr) in enumerate(moves):
        move_num = start_move_num + i
        # 根据 first_mover 调整轮次判断
        if first_mover == 'r':
            expected_side = 'r' if move_num % 2 == 1 else 'b'
        else:
            expected_side = 'b' if move_num % 2 == 1 else 'r'

        valid, reason = is_valid_move_ex(board, sc, sr, dc, dr, expected_side)
        if not valid:
            side_str = "red" if expected_side == 'r' else "black"
            errors.append(f"move {move_num}({side_str}) ({sc},{sr})->({dc},{dr}): {reason}")
        if (sc, sr) in board:
            piece = board.pop((sc, sr))
            board[(dc, dr)] = piece
    return errors


def is_valid_move_ex(board, sc, sr, dc, dr, expected_side):
    """验证一步棋是否合法（直接指定应走方）。"""
    if not in_board(sc, sr) or not in_board(dc, dr):
        return False, "coord out of board"
    if (sc, sr) == (dc, dr):
        return False, "src == dst"

    piece = board.get((sc, sr))
    if piece is None:
        return False, f"no piece at ({sc},{sr})"

    side, ptype = piece
    if side != expected_side:
        return False, f"wrong side (expect {'red' if expected_side=='r' else 'black'})"

    target = board.get((dc, dr))
    if target and target[0] == side:
        return False, "capture own piece"

    dx = dc - sc
    dy = dr - sr

    if ptype == 'K':
        if not in_palace(dc, dr, side):
            return False, "King leaves palace"
        if abs(dx) + abs(dy) != 1:
            return False, "King moves more than 1 step"

    elif ptype == 'A':
        if not in_palace(dc, dr, side):
            return False, "Advisor leaves palace"
        if abs(dx) != 1 or abs(dy) != 1:
            return False, "Advisor not diagonal-1"

    elif ptype == 'B':
        if abs(dx) != 2 or abs(dy) != 2:
            return False, "Bishop not tian-move"
        if not in_own_half(dr, side):
            return False, "Bishop crosses river"
        eye_c, eye_r = sc + dx // 2, sr + dy // 2
        if (eye_c, eye_r) in board:
            return False, "Bishop eye blocked"

    elif ptype == 'N':
        valid_jumps = [(1,2),(1,-2),(-1,2),(-1,-2),(2,1),(2,-1),(-2,1),(-2,-1)]
        if (dx, dy) not in valid_jumps:
            return False, "Knight not ri-move"
        if abs(dx) == 2:
            block = (sc + dx // 2, sr)
        else:
            block = (sc, sr + dy // 2)
        if block in board:
            return False, "Knight leg blocked"

    elif ptype == 'R':
        if dx != 0 and dy != 0:
            return False, "Rook not straight"
        if count_between(board, sc, sr, dc, dr) > 0:
            return False, "Rook path blocked"

    elif ptype == 'C':
        if dx != 0 and dy != 0:
            return False, "Cannon not straight"
        between = count_between(board, sc, sr, dc, dr)
        if target:
            if between != 1:
                return False, f"Cannon capture needs 1 screen (got {between})"
        else:
            if between != 0:
                return False, "Cannon move path blocked"

    elif ptype == 'P':
        if abs(dx) + abs(dy) != 1:
            return False, "Pawn moves more than 1"
        if side == 'r':
            if in_own_half(sr, 'r'):
                if dx != 0 or dy != -1:
                    return False, "Red Pawn before river: forward only"
            else:
                if dy > 0:
                    return False, "Pawn cannot retreat"
        else:
            if in_own_half(sr, 'b'):
                if dx != 0 or dy != 1:
                    return False, "Black Pawn before river: forward only"
            else:
                if dy < 0:
                    return False, "Pawn cannot retreat"

    return True, "OK"


def validate_game(game) -> Tuple[List[str], List[str]]:
    """验证一局棋谱。返回 (position_issues, move_errors)。"""
    pos_issues = []
    move_errors = []

    binit = game.get('board_init_raw', '')
    board = decode_binit_to_board(binit)
    pos_issues = validate_initial_position(board)

    moves_raw = game.get('moves_raw', '')
    if not moves_raw:
        return pos_issues, move_errors

    main_moves = parse_moves(moves_raw)

    # 自动检测先手方（残局谱可能黑先）
    first_mover = detect_first_mover(board, main_moves)

    move_errors = replay_moves(board, main_moves, start_move_num=1, first_mover=first_mover)

    # 验证变着
    all_variations = game.get('variations', [])
    for var in all_variations:
        branch = var.get('branch', '')
        var_moves_raw = var.get('moves_raw', '')
        if not var_moves_raw:
            continue

        parts = branch.split('_')
        if len(parts) < 2:
            continue
        parent_line = int(parts[0])
        branch_point = int(parts[1])

        # 重建到分支点的棋盘状态
        var_board = decode_binit_to_board(binit)

        if parent_line == 0:
            parent_moves = main_moves
            replay_count = branch_point - 1
        else:
            parent_var_idx = parent_line - 1
            if parent_var_idx >= len(all_variations):
                continue
            parent_var = all_variations[parent_var_idx]
            parent_branch = parent_var.get('branch', '0_0_0')
            pp = parent_branch.split('_')
            pp_parent = int(pp[0])
            pp_branch_point = int(pp[1])

            if pp_parent != 0:
                continue  # 跳过超过两层嵌套

            for j in range(min(pp_branch_point - 1, len(main_moves))):
                sc, sr, dc, dr = main_moves[j]
                if (sc, sr) in var_board:
                    var_board[(dc, dr)] = var_board.pop((sc, sr))

            p_moves = parse_moves(parent_var.get('moves_raw', ''))
            extra = branch_point - pp_branch_point
            for j in range(min(extra, len(p_moves))):
                sc, sr, dc, dr = p_moves[j]
                if (sc, sr) in var_board:
                    var_board[(dc, dr)] = var_board.pop((sc, sr))

            var_moves = parse_moves(var_moves_raw)
            errs = replay_moves(var_board, var_moves, start_move_num=branch_point,
                                first_mover=first_mover)
            for e in errs:
                move_errors.append(f"var[{branch}] {e}")
            continue

        # 重演到分支点
        for j in range(min(replay_count, len(parent_moves))):
            sc, sr, dc, dr = parent_moves[j]
            if (sc, sr) in var_board:
                var_board[(dc, dr)] = var_board.pop((sc, sr))

        var_moves = parse_moves(var_moves_raw)
        errs = replay_moves(var_board, var_moves, start_move_num=branch_point,
                            first_mover=first_mover)
        for e in errs:
            move_errors.append(f"var[{branch}] {e}")

    return pos_issues, move_errors


# ─────────────── 主程序 ───────────────

def main():
    data_dir = DATA_DIR
    if len(sys.argv) > 1:
        files = [Path(sys.argv[1])]
    else:
        files = sorted(data_dir.glob('*.json'))

    if not files:
        print(f"No JSON files in {data_dir}")
        sys.exit(1)

    grand_total_games = 0
    grand_pos_issues = 0
    grand_move_errors = 0
    grand_files_ok = 0

    for filepath in files:
        with open(filepath, encoding='utf-8') as f:
            data = json.load(f)

        manual = data.get('manual_name', filepath.stem)
        games = data.get('games', [])

        file_pos_issues = 0
        file_move_errors = 0
        file_games_with_problems = 0
        problem_details = []

        for idx, game in enumerate(games):
            pos_issues, move_errors = validate_game(game)
            if pos_issues or move_errors:
                file_games_with_problems += 1
                file_pos_issues += len(pos_issues)
                file_move_errors += len(move_errors)
                if len(problem_details) < 5:
                    title = game.get('title', f'game {idx+1}')
                    detail = f"  #{idx+1} {title}"
                    if pos_issues:
                        detail += f" | pos:{pos_issues[:2]}"
                    if move_errors:
                        detail += f" | moves:{move_errors[:2]}"
                    problem_details.append(detail)

        grand_total_games += len(games)

        if file_games_with_problems == 0:
            status = "[OK]"
            grand_files_ok += 1
        else:
            status = "[!!]"
            grand_pos_issues += file_pos_issues
            grand_move_errors += file_move_errors

        moves_total = sum(len(g.get('moves_raw', '')) // 4 for g in games)
        vars_total = sum(len(g.get('variations', [])) for g in games)
        print(f"{status} {manual} | {len(games)} games, {moves_total} moves, {vars_total} vars"
              f" | pos_err:{file_pos_issues} move_err:{file_move_errors}")

        for d in problem_details:
            print(d)

    print(f"\n{'='*70}")
    print(f"Summary: {len(files)} files, {grand_total_games} games")
    print(f"  Files OK: {grand_files_ok}/{len(files)}")
    print(f"  Position issues: {grand_pos_issues}")
    print(f"  Move errors: {grand_move_errors}")

    if grand_pos_issues == 0 and grand_move_errors == 0:
        print("\n  ALL POSITIONS AND MOVES ARE VALID.")
    sys.exit(0 if (grand_pos_issues == 0 and grand_move_errors == 0) else 1)


if __name__ == '__main__':
    main()
