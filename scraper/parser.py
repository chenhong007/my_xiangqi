"""
DhtmlXQ 格式解析器

东萍象棋网使用自定义的 DhtmlXQ 格式存储棋谱数据。
格式示例：
    [DhtmlXQ]
    [DhtmlXQ_title]第001局 气吞关右[/DhtmlXQ_title]
    [DhtmlXQ_binit]4771874849...[/DhtmlXQ_binit]
    ...
    [/DhtmlXQ]
"""

import re
from typing import Dict, List, Optional, Any


# DhtmlXQ 棋子编码映射
# binit 字段：60位十进制字符串，每位代表一个棋子槽
# 棋子代码（红方用小写，黑方用大写）
PIECE_CODES = {
    '0': None,        # 空位
    '1': 'R车',       # 红车
    '2': 'R马',       # 红马
    '3': 'R相',       # 红相
    '4': 'R仕',       # 红仕
    '5': 'R帅',       # 红帅
    '6': 'R炮',       # 红炮
    '7': 'R兵',       # 红兵
    '8': 'B车',       # 黑車
    '9': 'B马',       # 黑馬（注：需根据实际格式调整）
}

# 棋盘坐标：9列×10行，从黑方底线（行0）到红方底线（行9）
# DhtmlXQ 坐标系：col(0-8) × row(0-9)
BOARD_COLS = 9
BOARD_ROWS = 10


def parse_dhtmlxq_block(html: str) -> Optional[str]:
    """从 HTML 中提取 DhtmlXQ 数据块。"""
    # 匹配 [DhtmlXQ]...[/DhtmlXQ] 块
    pattern = r'\[DhtmlXQ\](.*?)\[/DhtmlXQ\]'
    match = re.search(pattern, html, re.DOTALL)
    if match:
        return match.group(1)
    return None


def parse_fields(block: str) -> Dict[str, Any]:
    """解析 DhtmlXQ 数据块中的所有字段。"""
    fields: Dict[str, Any] = {}
    comments: Dict[str, str] = {}

    # 匹配所有字段：[DhtmlXQ_FIELDNAME]value[/DhtmlXQ_FIELDNAME]
    pattern = r'\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]'
    for m in re.finditer(pattern, block, re.DOTALL):
        name = m.group(1)
        value = m.group(2).strip()

        if name.startswith('comment'):
            # 注解字段：comment0, comment1, ... -> 统一存入 comments 字典
            num = name[len('comment'):]
            if value:
                comments[num] = value
        else:
            fields[name] = value

    if comments:
        fields['comments'] = comments

    return fields


def decode_binit(binit: str) -> List[Dict]:
    """
    解码棋盘初始局面（binit 字段）。

    DhtmlXQ binit 格式（64字符，32个2位编码，每个代表一个棋子槽）：
    - 每对字符 XY 中：X 为棋子类型码，Y 为位置码
    - 99 表示该槽为空（棋子不在棋盘上/已被吃）
    
    注意：由于 DhtmlXQ 格式文档未完全公开，此处为近似解码。
    建议以原始 binit 字符串为权威数据，在 App 端实现完整解码。
    
    返回：棋子列表，每项包含 type、col、row 信息
    """
    if not binit or len(binit) < 4:
        return []

    pieces = []
    # 每2字符为一组，共32组（16红+16黑）
    for i in range(0, min(len(binit), 64), 2):
        pair = binit[i:i+2]
        if pair == '99':
            continue  # 空槽（棋子已不在棋盘）

        try:
            val = int(pair)
        except ValueError:
            continue

        # DhtmlXQ 位置编码：位置 = col + row*9（从黑方底线算起）
        # 棋子类型由槽序号决定（前16为红方，后16为黑方）
        slot_idx = i // 2
        is_red = slot_idx < 16

        # 棋子类型映射（基于槽序号顺序）
        red_types = ['车', '马', '相', '仕', '帅', '仕', '相', '马', '车',
                     '炮', '炮', '兵', '兵', '兵', '兵', '兵']
        black_types = ['車', '馬', '象', '士', '将', '士', '象', '馬', '車',
                       '砲', '砲', '卒', '卒', '卒', '卒', '卒']

        if is_red and slot_idx < len(red_types):
            piece_name = '红' + red_types[slot_idx]
        elif not is_red and (slot_idx - 16) < len(black_types):
            piece_name = '黑' + black_types[slot_idx - 16]
        else:
            piece_name = f'未知{slot_idx}'

        col = val % 9
        row = val // 9

        pieces.append({
            'piece': piece_name,
            'col': col,
            'row': row,
            'slot': slot_idx,
            'raw': pair,
        })

    return pieces


def decode_movelist(movelist: str) -> List[Dict]:
    """
    解码着法列表（movelist 字段）。

    DhtmlXQ movelist 格式：每4字符为一步棋，前2位为起点，后2位为终点。
    坐标编码：col + row*9（同 binit 位置编码）。
    
    返回：着法列表，每项包含 from_col/row、to_col/row
    """
    if not movelist:
        return []

    moves = []
    # 每4个字符为一步棋
    for i in range(0, len(movelist) - 3, 4):
        chunk = movelist[i:i+4]
        if len(chunk) < 4:
            break
        try:
            from_pos = int(chunk[:2])
            to_pos = int(chunk[2:4])
        except ValueError:
            continue

        from_col = from_pos % 9
        from_row = from_pos // 9
        to_col = to_pos % 9
        to_row = to_pos // 9

        moves.append({
            'step': i // 4 + 1,
            'from_col': from_col,
            'from_row': from_row,
            'to_col': to_col,
            'to_row': to_row,
            'raw': chunk,
        })

    return moves


def parse_game(html: str, game_id: int, source_url: str) -> Optional[Dict]:
    """
    从棋谱页面 HTML 解析完整游戏数据。

    返回结构化游戏字典，包含原始数据和解码结果。
    """
    block = parse_dhtmlxq_block(html)
    if block is None:
        return None

    raw = parse_fields(block)
    if not raw:
        return None

    # 解码棋盘位置和着法
    binit = raw.get('binit', '')
    movelist = raw.get('movelist', '')

    game = {
        'id': game_id,
        'url': source_url,
        'title': raw.get('title', ''),
        'event': raw.get('event', ''),
        'class': raw.get('class', ''),
        'round': raw.get('round', ''),
        'date': raw.get('date', ''),
        'result': raw.get('result', ''),

        # 棋手信息（古谱残局通常为空）
        'red_player': raw.get('red', '') or raw.get('redname', ''),
        'black_player': raw.get('black', '') or raw.get('blackname', ''),

        # 棋盘数据（原始 + 解码）
        'board_init_raw': binit,
        'board_init': decode_binit(binit) if binit else [],

        # 着法（原始 + 解码）
        'moves_raw': movelist,
        'moves': decode_movelist(movelist) if movelist else [],

        # 注解
        'comments': raw.get('comments', {}),

        # 元信息
        'hits': _safe_int(raw.get('hits', '0')),
        'price': _safe_int(raw.get('price', '0')),
        'sort_id': raw.get('sortid', ''),
        'owner': raw.get('owner', ''),
        'add_date': raw.get('adddate', ''),
        'edit_date': raw.get('editdate', ''),
    }

    return game


def _safe_int(val: str) -> int:
    """安全转换字符串为整数。"""
    try:
        return int(val)
    except (ValueError, TypeError):
        return 0


def extract_game_ids_from_html(html: str) -> List[int]:
    """
    从列表页 HTML 中提取所有棋谱 ID（view_u_XXXXX 链接）。
    """
    pattern = r'view_u_(\d+)\.html'
    ids = [int(m.group(1)) for m in re.finditer(pattern, html)]
    return list(dict.fromkeys(ids))  # 去重保序


if __name__ == '__main__':
    # 测试解析（使用已知的示例数据）
    sample_block = '''[DhtmlXQ_ver]www_dpxq_com[/DhtmlXQ_ver]
[DhtmlXQ_title]第001局 气吞关右[/DhtmlXQ_title]
[DhtmlXQ_binit]4771874849390712299945998665310668660230405020739983895899999999[/DhtmlXQ_binit]
[DhtmlXQ_movelist][/DhtmlXQ_movelist]
[DhtmlXQ_class]象棋谱大全-古谱残局[/DhtmlXQ_class]
[DhtmlXQ_event]适情雅趣[/DhtmlXQ_event]
[DhtmlXQ_round]卷一[/DhtmlXQ_round]
[DhtmlXQ_result]红胜[/DhtmlXQ_result]
[DhtmlXQ_hits]190957[/DhtmlXQ_hits]
[DhtmlXQ_comment0]红弃双车、兵，马炮胜[/DhtmlXQ_comment0]'''

    sample_html = f'[DhtmlXQ]{sample_block}[/DhtmlXQ]'
    result = parse_game(sample_html, 42809, 'http://www.dpxq.com/hldcg/search/view_u_42809.html')
    if result:
        print(f"解析成功: {result['title']} ({result['event']})")
        print(f"结果: {result['result']}, 注解: {result['comments']}")
        print(f"棋子数: {len(result['board_init'])}")
    else:
        print("解析失败")
